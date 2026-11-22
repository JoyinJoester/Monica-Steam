package takagi.ru.monica.steam.friends.voice.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountSourceRepository
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.friends.voice.background.SteamVoiceCallService
import takagi.ru.monica.steam.friends.voice.background.SteamVoiceNotificationPublisher
import takagi.ru.monica.steam.friends.voice.data.SteamVoiceRealtimeService
import takagi.ru.monica.steam.friends.voice.data.SteamVoiceService
import takagi.ru.monica.steam.friends.voice.data.SteamVoiceSignalingRetrier
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceCallState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceAudioRoute
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceConnectionState
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceIncomingRequest
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceParticipant
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceRealtimeEvent
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTarget
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceTargetType
import takagi.ru.monica.steam.friends.voice.domain.SteamVoiceWebRtcSession
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceAudioLevels
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceAudioSample
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceWebViewCallbacks
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceWebViewEngine
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceMediaHealth
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceMediaHealthMonitor
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceMediaSample
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceRecoveryBudget
import takagi.ru.monica.steam.friends.voice.media.isSteamVoiceMediaConnected
import takagi.ru.monica.steam.network.cm.SteamCmClient
import takagi.ru.monica.steam.network.cm.steamCmAccountKey
import takagi.ru.monica.steam.session.domain.SteamAccountSessionResolver
import takagi.ru.monica.steam.session.domain.resolveOrKeep

/** Process-owned voice session that survives navigation and screen-off via its foreground service. */
class SteamVoiceCallRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sourceRepository = SteamAccountSourceRepository.get(appContext)
    private val sessionResolver: SteamAccountSessionResolver = sourceRepository.sessionResolver()
    private val cm = SteamCmClient { account ->
        sourceRepository.sessionHandle(account)?.stableKey ?: steamCmAccountKey(account)
    }
    private val gateway = SteamVoiceService(cm)
    private val realtime = SteamVoiceRealtimeService(cm, sessionResolver)
    private val notificationPublisher = SteamVoiceNotificationPublisher(appContext)
    private val _state = MutableStateFlow(SteamVoiceCallState())
    val state: StateFlow<SteamVoiceCallState> = _state.asStateFlow()

    private val audioMeter = SteamVoiceAudioLevels()
    private val _audioLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val audioLevels: StateFlow<Map<String, Float>> = _audioLevels.asStateFlow()

    private var account: SteamAccount? = null
    private var accountKey: String = ""
    private var mediaEngine: SteamVoiceWebViewEngine? = null
    private var realtimeJob: Job? = null
    private var mediaRecoveryJob: Job? = null
    private var iceDisconnectJob: Job? = null
    private var webRtcSession: SteamVoiceWebRtcSession? = null
    private var remoteDescriptionVersion = "0"
    private var joiningVoice = false
    private var voiceWebRtcUpdated = false
    private var directAccepted = false
    private var localMediaReady = false
    private var mediaGeneration = 0L
    private var iceConnected = false
    private val mediaHealthMonitor = SteamVoiceMediaHealthMonitor()
    private val recoveryBudget = SteamVoiceRecoveryBudget()
    private val signalingRetrier = SteamVoiceSignalingRetrier()

    init {
        scope.launch {
            combine(state, _audioLevels.subscriptionCount) { call, count -> call to count }
                .collectLatest { (call, count) ->
                    val revision = audioMeter.invalidate()
                    _audioLevels.value = emptyMap()
                    val enabled = call.isConnected && count > 0
                    mediaEngine?.configureAudioMeter(enabled, revision)
                    if (enabled) while (true) {
                        delay(120)
                        audioMeter.expire(android.os.SystemClock.elapsedRealtime())
                        _audioLevels.value = audioMeter.values
                    }
                }
        }
    }

    fun startGroup(
        account: SteamAccount,
        groupId: String,
        chatId: String,
        title: String
    ) = start(
        account,
        SteamVoiceTarget(
            type = SteamVoiceTargetType.GROUP,
            title = title,
            groupId = groupId,
            chatId = chatId
        )
    )

    /** Keeps the CM voice notification stream alive for incoming call invites. */
    fun observeAccount(account: SteamAccount) {
        val nextAccountKey = resolveAccountKey(account)
        if (this.account?.id == account.id &&
            this.account?.steamId == account.steamId &&
            accountKey == nextAccountKey &&
            realtimeJob?.isActive == true
        ) {
            val credentialsChanged = this.account?.accessToken != account.accessToken ||
                this.account?.refreshToken != account.refreshToken ||
                this.account?.steamLoginSecure != account.steamLoginSecure
            this.account = account
            if (credentialsChanged) startRealtime(account)
            return
        }
        if (_state.value.isActive && (
                this.account?.id != account.id ||
                    this.account?.steamId != account.steamId ||
                    accountKey != nextAccountKey
                )
        ) return
        val previousAccount = this.account
        val previousRequest = _state.value.incomingRequest
        val accountChanged = previousAccount != null && (
            previousAccount.id != account.id ||
                previousAccount.steamId != account.steamId ||
                accountKey != nextAccountKey
            )
        if (accountChanged && previousRequest != null) {
            scope.launch {
                runNetwork(previousAccount, "reject_previous_account") { prepared ->
                    gateway.answerDirectVoice(
                        prepared,
                        previousRequest.partnerSteamId,
                        previousRequest.voiceChatId,
                        accepted = false
                    )
                }
            }
            notificationPublisher.cancel()
            _state.value = SteamVoiceCallState(accountSteamId = account.steamId)
        }
        this.account = account
        accountKey = nextAccountKey
        _state.value = _state.value.copy(accountSteamId = account.steamId)
        startRealtime(account)
    }

    fun startDirect(account: SteamAccount, partnerSteamId: String, title: String) = start(
        account,
        SteamVoiceTarget(
            type = SteamVoiceTargetType.DIRECT,
            title = title,
            partnerSteamId = partnerSteamId
        )
    )

    fun acceptIncoming(account: SteamAccount, title: String) {
        val request = _state.value.incomingRequest ?: return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                failure = "Microphone permission is required for Steam voice chat"
            )
            return
        }
        this.account = account
        accountKey = resolveAccountKey(account)
        scope.launch {
            val result = runNetwork("accept_direct") { prepared ->
                gateway.answerDirectVoice(
                    prepared,
                    request.partnerSteamId,
                    request.voiceChatId,
                    accepted = true
                )
            }
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    failure = result.exceptionOrNull().voiceMessage("Unable to accept Steam voice call")
                )
                return@launch
            }
            start(
                account = account,
                target = SteamVoiceTarget(
                    type = SteamVoiceTargetType.DIRECT,
                    title = title,
                    partnerSteamId = request.partnerSteamId
                ),
                initialVoiceChatId = request.voiceChatId
            )
        }
    }

    internal fun acceptIncomingFromNotification() {
        val currentAccount = account ?: return
        val request = _state.value.incomingRequest ?: return
        acceptIncoming(currentAccount, request.partnerSteamId)
    }

    internal fun rejectIncomingFromNotification() = rejectIncoming()

    fun rejectIncoming() {
        val currentAccount = account ?: return
        val request = _state.value.incomingRequest ?: return
        scope.launch {
            runNetwork("reject_direct") { prepared ->
                gateway.answerDirectVoice(
                    prepared,
                    request.partnerSteamId,
                    request.voiceChatId,
                    accepted = false
                )
            }
            _state.value = _state.value.copy(incomingRequest = null)
            notificationPublisher.cancel()
        }
    }

    fun toggleMicrophone() {
        val muted = !_state.value.microphoneMuted
        _state.value = _state.value.copy(microphoneMuted = muted)
        mediaEngine?.setMicrophoneMuted(muted)
        publishVoiceStatus()
    }

    fun toggleOutput() {
        val muted = !_state.value.outputMuted
        _state.value = _state.value.copy(outputMuted = muted)
        mediaEngine?.setOutputMuted(muted)
        publishVoiceStatus()
    }

    fun selectAudioRoute(route: SteamVoiceAudioRoute) {
        if (route !in _state.value.availableAudioRoutes) return
        _state.value = _state.value.copy(requestedAudioRoute = route)
    }

    internal fun updateAudioRoutes(
        availableRoutes: List<SteamVoiceAudioRoute>,
        activeRoute: SteamVoiceAudioRoute,
        requestedRoute: SteamVoiceAudioRoute
    ) {
        val normalized = (listOf(SteamVoiceAudioRoute.AUTO) + availableRoutes)
            .distinct()
        val requested = requestedRoute.takeIf { it in normalized }
            ?: SteamVoiceAudioRoute.AUTO
        _state.value = _state.value.copy(
            availableAudioRoutes = normalized,
            audioRoute = activeRoute.takeIf { it in normalized }
                ?: SteamVoiceAudioRoute.AUTO,
            requestedAudioRoute = requested
        )
    }

    fun stop() = stopInternal(notifySteam = true)

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    private fun start(
        account: SteamAccount,
        target: SteamVoiceTarget,
        initialVoiceChatId: String = ""
    ) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(
                failure = "Microphone permission is required for Steam voice chat"
            )
            return
        }
        if (_state.value.isActive) stopInternal(notifySteam = true)
        this.account = account
        accountKey = resolveAccountKey(account)
        webRtcSession = null
        remoteDescriptionVersion = "0"
        joiningVoice = false
        voiceWebRtcUpdated = false
        directAccepted = target.type == SteamVoiceTargetType.DIRECT && initialVoiceChatId.isNotBlank()
        localMediaReady = false
        iceConnected = false
        mediaHealthMonitor.reset()
        recoveryBudget.reset()
        val previousVoiceState = _state.value
        _state.value = SteamVoiceCallState(
            accountSteamId = account.steamId,
            target = target,
            voiceChatId = initialVoiceChatId,
            state = SteamVoiceConnectionState.REQUESTING_MICROPHONE,
            audioRoute = previousVoiceState.audioRoute,
            requestedAudioRoute = previousVoiceState.requestedAudioRoute,
            availableAudioRoutes = previousVoiceState.availableAudioRoutes,
            incomingRequest = null
        )
        startRealtime(account)
        runCatching { SteamVoiceCallService.start(appContext) }
            .onFailure { error ->
                SteamDiagLogger.append(
                    "voice_foreground_start failed type=${error::class.java.simpleName}"
                )
            }
        startMediaEngine()
    }

    private fun startMediaEngine() {
        if (!_state.value.isActive) return
        val generation = ++mediaGeneration
        val engine = SteamVoiceWebViewEngine(
            appContext,
            object : SteamVoiceWebViewCallbacks {
                override fun onLocalMediaReady() {
                    if (!isCurrentMedia(generation)) return
                    localMediaReady = true
                    updateVoiceConnectionState()
                }

                override fun onLocalOffer(descriptionJson: String) {
                    if (isCurrentMedia(generation)) {
                        scope.launch { initiateWebRtc(descriptionJson, generation) }
                    }
                }

                override fun onLocalAnswer(descriptionJson: String) {
                    if (isCurrentMedia(generation)) {
                        scope.launch { acknowledgeRemoteDescription(descriptionJson, generation) }
                    }
                }

                override fun onIceStateChanged(state: String) {
                    if (isCurrentMedia(generation)) {
                        handleIceState(state, generation)
                    }
                }

                override fun onAudioLevels(stats: String) {
                    if (!isCurrentMedia(generation) || _audioLevels.subscriptionCount.value == 0) return
                    val sample = SteamVoiceAudioSample.parse(stats) ?: return
                    audioMeter.accept(sample, _state.value, android.os.SystemClock.elapsedRealtime())
                    _audioLevels.value = audioMeter.values
                }

                override fun onMediaStats(stats: String) {
                    if (isCurrentMedia(generation)) {
                        evaluateMediaStats(stats, generation)
                    }
                }

                override fun onDiagnostic(message: String) {
                    if (isCurrentMedia(generation)) {
                        SteamDiagLogger.append(
                            "voice_media_diagnostic message=${message.voiceDiagnostic()}"
                        )
                    }
                }

                override fun onEngineTerminated(message: String) {
                    if (isCurrentMedia(generation)) {
                        requestMediaRecovery(message, generation)
                    }
                }

                override fun onFailure(message: String) {
                    if (!isCurrentMedia(generation)) return
                    SteamDiagLogger.append(
                        "voice_media_error message=${message.voiceDiagnostic()}"
                    )
                    if (message.isSteamVoicePermissionFailure()) {
                        fail("Microphone permission is required for Steam voice chat")
                    } else {
                        requestMediaRecovery("Steam voice media error", generation)
                    }
                }
            }
        )
        mediaEngine = engine
        engine.configureAudioMeter(false, audioMeter.replaceMapping(emptyMap()))
        _audioLevels.value = emptyMap()
        engine.setMicrophoneMuted(_state.value.microphoneMuted)
        engine.setOutputMuted(_state.value.outputMuted)
        engine.start()
    }

    private suspend fun initiateWebRtc(descriptionJson: String, generation: Long) {
        if (!isCurrentMedia(generation)) return
        _state.value = _state.value.copy(state = SteamVoiceConnectionState.CONNECTING_MEDIA)
        val remote = runRetryableNetwork("init_webrtc", generation) { prepared ->
            gateway.initiateWebRtc(
                prepared,
                descriptionJson,
                clientName = "Chrome",
                clientVersion = "126"
            )
        }.getOrElse {
            fail(it.voiceMessage("Steam WebRTC connection failed"))
            return
        }
        if (!isCurrentMedia(generation)) return
        _state.value = _state.value.copy(state = SteamVoiceConnectionState.CONNECTING_MEDIA)
        mediaEngine?.setRemoteDescription(remote)
    }

    private suspend fun onIceConnected(generation: Long) {
        if (!isCurrentMedia(generation)) return
        iceConnected = true
        iceDisconnectJob?.cancel()
        iceDisconnectJob = null
        if (voiceWebRtcUpdated && _state.value.voiceChatId.isNotBlank()) {
            updateVoiceConnectionState()
            return
        }
        if (joiningVoice) return
        val currentTarget = _state.value.target ?: return
        joiningVoice = true
        when (currentTarget.type) {
            SteamVoiceTargetType.GROUP -> {
                if (_state.value.voiceChatId.isNotBlank()) {
                    maybeUpdateVoiceWebRtcData(generation)
                    return
                }
                val voiceId = runNetwork("join_group") { prepared ->
                    gateway.joinGroupVoice(
                        prepared,
                        requireNotNull(currentTarget.groupId),
                        requireNotNull(currentTarget.chatId)
                    )
                }.getOrElse {
                    joiningVoice = false
                    fail(it.voiceMessage("Unable to join Steam voice channel"))
                    return
                }
                if (!isCurrentMedia(generation)) return
                _state.value = _state.value.copy(voiceChatId = voiceId)
                maybeUpdateVoiceWebRtcData(generation)
            }
            SteamVoiceTargetType.DIRECT -> {
                val partnerSteamId = requireNotNull(currentTarget.partnerSteamId)
                if (_state.value.voiceChatId.isNotBlank()) {
                    maybeUpdateVoiceWebRtcData(generation)
                } else {
                    val voiceId = runNetwork("request_direct") { prepared ->
                        gateway.requestDirectVoice(prepared, partnerSteamId)
                    }.getOrElse {
                        joiningVoice = false
                        fail(it.voiceMessage("Unable to start Steam voice call"))
                        return
                    }
                    if (!isCurrentMedia(generation)) return
                    _state.value = _state.value.copy(
                        voiceChatId = voiceId,
                        state = SteamVoiceConnectionState.WAITING_FOR_ACCEPT
                    )
                }
            }
        }
    }

    private suspend fun maybeUpdateVoiceWebRtcData(
        expectedGeneration: Long = mediaGeneration
    ) {
        if (!isCurrentMedia(expectedGeneration)) return
        val voiceChatId = _state.value.voiceChatId.takeIf(String::isNotBlank) ?: return
        val session = webRtcSession ?: return
        if (voiceWebRtcUpdated) return
        voiceWebRtcUpdated = true
        val result = runRetryableNetwork("update_voice_webrtc", expectedGeneration) { prepared ->
            gateway.updateVoiceWebRtcData(
                prepared,
                voiceChatId,
                session,
                userAgent = ANDROID_WEBVIEW_USER_AGENT
            )
        }
        if (result.isFailure) {
            voiceWebRtcUpdated = false
            joiningVoice = false
            requestMediaRecovery("Steam voice session setup failed", expectedGeneration)
            return
        }
        if (!isCurrentMedia(expectedGeneration)) return
        joiningVoice = false
        updateVoiceConnectionState()
        publishVoiceStatus()
    }

    private suspend fun acknowledgeRemoteDescription(
        localAnswerJson: String,
        generation: Long
    ) {
        if (!isCurrentMedia(generation)) return
        val session = webRtcSession ?: return
        val version = remoteDescriptionVersion
        val result = runRetryableNetwork("ack_remote_description", generation) { prepared ->
            gateway.acknowledgeRemoteDescription(prepared, session, version)
        }
        if (result.isFailure) {
            SteamDiagLogger.append(
                "voice_ack_remote failed type=${result.exceptionOrNull()?.javaClass?.simpleName}"
            )
        }
        if (!isCurrentMedia(generation)) return
        // Steam's server receives the local answer through the acknowledged
        // WebRTC session coordinates; keeping the argument makes the callback
        // explicit and prevents accidental acknowledgement before an answer.
        if (localAnswerJson.isBlank()) return
    }

    private fun handleIceState(rawState: String, generation: Long) {
        when (rawState.lowercase()) {
            "connected", "completed" -> scope.launch { onIceConnected(generation) }
            "disconnected" -> {
                iceConnected = false
                _state.value = _state.value.copy(
                    state = SteamVoiceConnectionState.RECONNECTING,
                    failure = null
                )
                iceDisconnectJob?.cancel()
                iceDisconnectJob = scope.launch {
                    delay(ICE_DISCONNECT_GRACE_MILLIS)
                    if (isCurrentMedia(generation) && !iceConnected) {
                        requestMediaRecovery("Steam voice media disconnected", generation)
                    }
                }
            }
            "failed" -> {
                iceConnected = false
                requestMediaRecovery("Steam voice media connection failed", generation)
            }
        }
    }

    private fun evaluateMediaStats(rawStats: String, generation: Long) {
        if (!isCurrentMedia(generation)) return
        if (!hasMicrophonePermission()) {
            fail("Microphone permission is required for Steam voice chat")
            return
        }
        val sample = SteamVoiceMediaSample.parse(rawStats) ?: return
        if (sample.readyState.equals("live", ignoreCase = true)) {
            localMediaReady = true
        }
        when (mediaHealthMonitor.observe(
            sample = sample,
            nowMillis = System.currentTimeMillis(),
            microphoneMuted = _state.value.microphoneMuted
        )) {
            SteamVoiceMediaHealth.HEALTHY -> Unit
            SteamVoiceMediaHealth.PERMISSION_REVOKED ->
                fail("Microphone permission is required for Steam voice chat")
            SteamVoiceMediaHealth.TRACK_MISSING,
            SteamVoiceMediaHealth.TRACK_ENDED -> {
                localMediaReady = false
                requestMediaRecovery("Steam voice microphone track ended", generation)
            }
            SteamVoiceMediaHealth.OUTBOUND_STALLED ->
                requestMediaRecovery("Steam voice outbound audio stalled", generation)
        }
    }

    private fun requestMediaRecovery(reason: String, generation: Long) {
        if (!isCurrentMedia(generation) || mediaRecoveryJob?.isActive == true) return
        if (!hasMicrophonePermission()) {
            fail("Microphone permission is required for Steam voice chat")
            return
        }
        val attempt = recoveryBudget.request(System.currentTimeMillis())
        if (attempt == null) {
            fail("Steam voice media repeatedly failed")
            return
        }
        _state.value = _state.value.copy(
            state = SteamVoiceConnectionState.RECONNECTING,
            failure = null
        )
        SteamDiagLogger.append(
            "voice_media_recovery attempt=${attempt.attempt} reason=${reason.voiceDiagnostic()}"
        )
        val job = scope.launch {
            delay(attempt.delayMillis)
            if (isCurrentMedia(generation)) restartMediaEngine(reason)
        }
        mediaRecoveryJob = job
        job.invokeOnCompletion {
            if (mediaRecoveryJob === job) mediaRecoveryJob = null
        }
    }

    private fun restartMediaEngine(reason: String) {
        if (!_state.value.isActive) return
        iceDisconnectJob?.cancel()
        iceDisconnectJob = null
        mediaGeneration++
        audioMeter.replaceMapping(emptyMap())
        _audioLevels.value = emptyMap()
        mediaEngine?.stop()
        mediaEngine = null
        webRtcSession = null
        remoteDescriptionVersion = "0"
        joiningVoice = false
        voiceWebRtcUpdated = false
        iceConnected = false
        localMediaReady = false
        mediaHealthMonitor.reset()
        SteamDiagLogger.append("voice_media_restarted reason=${reason.voiceDiagnostic()}")
        startMediaEngine()
    }

    private fun isCurrentMedia(generation: Long): Boolean =
        _state.value.isActive && mediaGeneration == generation

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startRealtime(account: SteamAccount) {
        realtimeJob?.cancel()
        realtimeJob = scope.launch {
            try {
                realtime.events(account).collect(::applyRealtimeEvent)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (_state.value.isActive) {
                    _state.value = _state.value.copy(
                        state = SteamVoiceConnectionState.RECONNECTING,
                        failure = error.voiceMessage("Steam voice signaling disconnected")
                    )
                }
            }
        }
    }

    private suspend fun applyRealtimeEvent(event: SteamVoiceRealtimeEvent) {
        when (event) {
            is SteamVoiceRealtimeEvent.ConnectionChanged -> {
                if (!event.connected && _state.value.isActive) {
                    _state.value = _state.value.copy(state = SteamVoiceConnectionState.RECONNECTING)
                } else if (event.connected && iceConnected && voiceWebRtcUpdated) {
                    updateVoiceConnectionState()
                }
            }
            is SteamVoiceRealtimeEvent.IncomingDirectRequest -> {
                if (_state.value.isActive) {
                    runNetwork("reject_busy_direct") { prepared ->
                        gateway.answerDirectVoice(
                            prepared,
                            event.partnerSteamId,
                            event.voiceChatId,
                            accepted = false
                        )
                    }
                } else if (_state.value.incomingRequest?.voiceChatId != event.voiceChatId) {
                    _state.value = _state.value.copy(
                        accountSteamId = account?.steamId.orEmpty(),
                        incomingRequest = SteamVoiceIncomingRequest(
                            event.partnerSteamId,
                            event.voiceChatId
                        )
                    )
                    notificationPublisher.post(_state.value)
                }
            }
            is SteamVoiceRealtimeEvent.DirectResponse -> {
                if (event.voiceChatId == _state.value.voiceChatId &&
                    event.partnerSteamId == _state.value.target?.partnerSteamId
                ) {
                    if (event.accepted) {
                        directAccepted = true
                        maybeUpdateVoiceWebRtcData(mediaGeneration)
                        updateVoiceConnectionState()
                    } else {
                        directAccepted = false
                        fail("Steam voice call was declined")
                    }
                }
            }
            is SteamVoiceRealtimeEvent.UserJoined -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    updatePresence(event.steamId, joined = true)
                }
            }
            is SteamVoiceRealtimeEvent.UserLeft -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    updatePresence(event.steamId, joined = false)
                }
            }
            is SteamVoiceRealtimeEvent.UserStatus -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    updateParticipant(event.participant)
                }
            }
            is SteamVoiceRealtimeEvent.AllUsersStatus -> {
                if (event.voiceChatId == _state.value.voiceChatId) {
                    _state.value = _state.value.copy(participants = event.participants)
                }
            }
            is SteamVoiceRealtimeEvent.VoiceEnded -> {
                if (event.voiceChatId == _state.value.voiceChatId) stopInternal(notifySteam = false)
            }
            is SteamVoiceRealtimeEvent.RejoinRequired -> {
                val target = _state.value.target
                if (target?.type == SteamVoiceTargetType.GROUP &&
                    target.groupId == event.groupId && target.chatId == event.chatId
                ) {
                    voiceWebRtcUpdated = false
                    joiningVoice = false
                    _state.value = _state.value.copy(
                        voiceChatId = "",
                        state = SteamVoiceConnectionState.RECONNECTING
                    )
                    onIceConnected(mediaGeneration)
                }
            }
            is SteamVoiceRealtimeEvent.WebRtcConnected -> {
                if (_state.value.isActive) {
                    webRtcSession = event.session
                    maybeUpdateVoiceWebRtcData(mediaGeneration)
                }
            }
            is SteamVoiceRealtimeEvent.RemoteDescriptionUpdated -> {
                if (_state.value.isActive) {
                    val revision = audioMeter.replaceMapping(event.description.ssrcToSteamIds)
                    _audioLevels.value = emptyMap()
                    remoteDescriptionVersion = event.description.version
                    mediaEngine?.setRemoteDescription(event.description.descriptionJson,
                        _state.value.isConnected && _audioLevels.subscriptionCount.value > 0, revision)
                }
            }
        }
    }

    private fun updatePresence(steamId: String, joined: Boolean) {
        val current = _state.value.participants.associateBy(SteamVoiceParticipant::steamId).toMutableMap()
        if (joined) {
            current[steamId] = current[steamId]?.copy(joined = true)
                ?: SteamVoiceParticipant(steamId)
        } else {
            audioMeter.removeParticipant(steamId)
            current.remove(steamId)
        }
        _state.value = _state.value.copy(participants = current.values.toList())
    }

    private fun updateParticipant(participant: SteamVoiceParticipant) {
        val current = _state.value.participants.associateBy(SteamVoiceParticipant::steamId).toMutableMap()
        current[participant.steamId] = participant
        _state.value = _state.value.copy(participants = current.values.toList())
    }

    private fun publishVoiceStatus() {
        val voiceId = _state.value.voiceChatId.takeIf(String::isNotBlank) ?: return
        scope.launch {
            runNetwork("voice_status") { prepared ->
                gateway.notifyVoiceStatus(
                    prepared,
                    voiceId,
                    _state.value.microphoneMuted,
                    _state.value.outputMuted,
                    hasNoMicrophone = false
                )
            }
        }
    }

    private fun stopInternal(notifySteam: Boolean, failure: String? = null) {
        val currentAccount = account
        val current = _state.value
        if (notifySteam && currentAccount != null && current.target != null) {
            scope.launch {
                runNetwork("leave_voice") { prepared ->
                    when (current.target.type) {
                        SteamVoiceTargetType.GROUP -> if (current.voiceChatId.isNotBlank()) {
                            gateway.leaveGroupVoice(
                                prepared,
                                requireNotNull(current.target.groupId),
                                requireNotNull(current.target.chatId)
                            )
                        }
                        SteamVoiceTargetType.DIRECT -> if (current.voiceChatId.isNotBlank()) {
                            gateway.leaveDirectVoice(
                                prepared,
                                requireNotNull(current.target.partnerSteamId),
                                current.voiceChatId
                            )
                        }
                    }
                }
            }
        }
        realtimeJob?.cancel()
        realtimeJob = null
        mediaRecoveryJob?.cancel()
        mediaRecoveryJob = null
        iceDisconnectJob?.cancel()
        iceDisconnectJob = null
        mediaGeneration++
        audioMeter.replaceMapping(emptyMap())
        _audioLevels.value = emptyMap()
        mediaEngine?.stop()
        mediaEngine = null
        webRtcSession = null
        remoteDescriptionVersion = "0"
        joiningVoice = false
        voiceWebRtcUpdated = false
        directAccepted = false
        iceConnected = false
        localMediaReady = false
        mediaHealthMonitor.reset()
        recoveryBudget.reset()
        account = currentAccount
        _state.value = SteamVoiceCallState(
            accountSteamId = currentAccount?.steamId.orEmpty(),
            state = if (failure == null) SteamVoiceConnectionState.IDLE
            else SteamVoiceConnectionState.FAILED,
            failure = failure
        )
        currentAccount?.let { startRealtime(it) }
        notificationPublisher.cancel()
        SteamVoiceCallService.stop(appContext)
    }

    private fun fail(message: String) {
        SteamDiagLogger.append("voice_call failed message=${message.take(180)}")
        stopInternal(notifySteam = true, failure = message)
    }

    private suspend fun <T> runNetwork(
        operation: String,
        block: (SteamAccount) -> T
    ): Result<T> {
        val current = account ?: return Result.failure(IllegalStateException("Steam voice account missing"))
        return runNetwork(current, operation, block)
    }

    private suspend fun <T> runRetryableNetwork(
        operation: String,
        generation: Long,
        block: (SteamAccount) -> T
    ): Result<T> {
        return try {
            Result.success(
                signalingRetrier.execute(
                    onRetry = { retry ->
                        if (!isCurrentMedia(generation)) {
                            throw CancellationException("Steam voice media generation changed")
                        }
                        _state.value = _state.value.copy(
                            state = SteamVoiceConnectionState.RECONNECTING,
                            failure = null
                        )
                        SteamDiagLogger.append(
                            "voice_signaling_retry operation=$operation attempt=${retry.attempt} " +
                                "delay=${retry.delayMillis} type=${retry.failure::class.java.simpleName}"
                        )
                    }
                ) {
                    if (!isCurrentMedia(generation)) {
                        throw CancellationException("Steam voice media generation changed")
                    }
                    runNetwork(operation, block).getOrThrow()
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun <T> runNetwork(
        sourceAccount: SteamAccount,
        operation: String,
        block: (SteamAccount) -> T
    ): Result<T> {
        return try {
            val prepared = sessionResolver.resolveOrKeep(sourceAccount)
            if (account?.id == sourceAccount.id && account?.steamId == sourceAccount.steamId) {
                account = prepared
            }
            Result.success(withContext(Dispatchers.IO) { block(prepared) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SteamDiagLogger.append(
                "voice_network operation=$operation type=${error::class.java.simpleName}"
            )
            Result.failure(error)
        }
    }

    private fun Throwable?.voiceMessage(fallback: String): String =
        this?.message?.takeIf(String::isNotBlank)?.take(180) ?: fallback

    private fun resolveAccountKey(current: SteamAccount): String =
        sourceRepository.sessionHandle(current)?.stableKey ?: steamCmAccountKey(current)

    private fun updateVoiceConnectionState() {
        val targetType = _state.value.target?.type ?: return
        val connected = isSteamVoiceMediaConnected(
            targetType = targetType,
            voiceChatId = _state.value.voiceChatId,
            iceConnected = iceConnected,
            webRtcUpdated = voiceWebRtcUpdated,
            directAccepted = directAccepted,
            localMediaReady = localMediaReady
        )
        _state.value = _state.value.copy(
            state = when {
                connected -> SteamVoiceConnectionState.CONNECTED
                targetType == SteamVoiceTargetType.DIRECT &&
                    _state.value.voiceChatId.isNotBlank() && !directAccepted ->
                    SteamVoiceConnectionState.WAITING_FOR_ACCEPT
                else -> SteamVoiceConnectionState.CONNECTING_MEDIA
            },
            failure = null
        )
    }

    companion object {
        const val MICROPHONE_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val ICE_DISCONNECT_GRACE_MILLIS = 4_000L
        private const val ANDROID_WEBVIEW_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36 Monica-Steam"
        private val INSTANCE = AtomicReference<SteamVoiceCallRuntime?>()

        fun get(context: Context): SteamVoiceCallRuntime = INSTANCE.get()
            ?: synchronized(this) {
                INSTANCE.get() ?: SteamVoiceCallRuntime(context).also(INSTANCE::set)
            }
    }
}

private fun String.isSteamVoicePermissionFailure(): Boolean {
    val normalized = lowercase()
    return normalized.contains("permission") ||
        normalized.contains("notallowederror") ||
        normalized.contains("permissiondenied")
}

private fun String.voiceDiagnostic(): String =
    replace(Regex("[\\r\\n\\t]+"), " ").take(120)
