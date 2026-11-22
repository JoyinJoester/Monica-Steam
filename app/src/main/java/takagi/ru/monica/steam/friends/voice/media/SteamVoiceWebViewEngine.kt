package takagi.ru.monica.steam.friends.voice.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import org.json.JSONObject
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger

internal interface SteamVoiceWebViewCallbacks {
    fun onLocalMediaReady()
    fun onLocalOffer(descriptionJson: String)
    fun onLocalAnswer(descriptionJson: String)
    fun onIceStateChanged(state: String)
    fun onAudioLevels(stats: String)
    fun onMediaStats(stats: String)
    fun onDiagnostic(message: String)
    fun onEngineTerminated(message: String)
    fun onFailure(message: String)
}

/**
 * Uses Android WebView's platform WebRTC implementation as a small, hidden
 * media surface. This keeps the APK free of a 40+ MB native libwebrtc AAR
 * while retaining the same WebRTC signaling Steam's official client uses.
 */
internal class SteamVoiceWebViewEngine(
    context: Context,
    private val callbacks: SteamVoiceWebViewCallbacks
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var started = false
    private var stopping = false
    private var microphoneMuted = false
    private var outputMuted = false
    @Volatile private var audioMeterEnabled = false
    @Volatile private var audioMeterRevision = 0L

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (started) return
        started = true
        stopping = false
        mainHandler.post {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                started = false
                callbacks.onFailure("Microphone permission is required for Steam voice chat")
                return@post
            }
            val view = runCatching { WebView(appContext).apply {
                setBackgroundColor(Color.TRANSPARENT)
                alpha = 0f
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                }
                addJavascriptInterface(Bridge(), BRIDGE_NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = true

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        val notifyRecovery = started && !stopping
                        webView = null
                        started = false
                        runCatching { view?.destroy() }
                        if (notifyRecovery) {
                            callbacks.onEngineTerminated(if (detail?.didCrash() == true) {
                                "Android WebView voice renderer crashed"
                            } else {
                                "Android stopped the WebView voice renderer"
                            })
                        }
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest) {
                        mainHandler.post {
                            val origin = request.origin?.host.orEmpty()
                            if (origin == "steamcommunity.com" &&
                                ContextCompat.checkSelfPermission(
                                    appContext,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                            } else {
                                request.deny()
                            }
                        }
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                            SteamDiagLogger.append(
                                "voice_webview_console ${consoleMessage.message().take(180)}"
                            )
                        }
                        return true
                    }
                }
                WebView.setWebContentsDebuggingEnabled(false)
                loadDataWithBaseURL(
                    BASE_URL,
                    HTML,
                    "text/html",
                    "UTF-8",
                    null
                )
            } }.getOrElse { error ->
                started = false
                callbacks.onFailure(
                    error.message?.takeIf(String::isNotBlank)
                        ?: "Android WebView could not start Steam voice chat"
                )
                return@post
            }
            webView = view
        }
    }

    fun setRemoteDescription(descriptionJson: String, meterEnabled: Boolean = audioMeterEnabled,
        meterRevision: Long = audioMeterRevision) {
        audioMeterEnabled = meterEnabled
        audioMeterRevision = meterRevision
        evaluate("window.monicaVoice && window.monicaVoice.setRemoteDescription(" +
            "${JSONObject.quote(descriptionJson)}, $meterEnabled, $meterRevision)")
    }

    fun setMicrophoneMuted(muted: Boolean) {
        microphoneMuted = muted
        evaluate("window.monicaVoice && window.monicaVoice.setMicrophoneMuted(${muted})")
    }

    fun setOutputMuted(muted: Boolean) {
        outputMuted = muted
        evaluate("window.monicaVoice && window.monicaVoice.setOutputMuted(${muted})")
    }

    fun configureAudioMeter(enabled: Boolean, revision: Long) {
        audioMeterEnabled = enabled
        audioMeterRevision = revision
        evaluate("window.monicaVoice && window.monicaVoice.configureAudioMeter($enabled, $revision)")
    }

    fun stop() {
        started = false
        stopping = true
        mainHandler.post {
            webView?.let { view ->
                runCatching {
                    view.evaluateJavascript("window.monicaVoice && window.monicaVoice.stop()", null)
                    view.removeJavascriptInterface(BRIDGE_NAME)
                    view.stopLoading()
                    view.destroy()
                }
            }
            webView = null
        }
    }

    private fun evaluate(script: String) {
        mainHandler.post { webView?.evaluateJavascript(script, null) }
    }

    private fun dispatch(action: () -> Unit) {
        mainHandler.post { if (started && !stopping) action() }
    }

    private inner class Bridge {
        @android.webkit.JavascriptInterface
        fun onLocalMediaReady() = dispatch { callbacks.onLocalMediaReady() }

        @android.webkit.JavascriptInterface
        fun onLocalOffer(descriptionJson: String) = dispatch { callbacks.onLocalOffer(descriptionJson) }

        @android.webkit.JavascriptInterface
        fun onLocalAnswer(descriptionJson: String) = dispatch { callbacks.onLocalAnswer(descriptionJson) }

        @android.webkit.JavascriptInterface
        fun onIceStateChanged(state: String) = dispatch { callbacks.onIceStateChanged(state) }

        @android.webkit.JavascriptInterface
        fun onMediaStats(stats: String) = dispatch { callbacks.onMediaStats(stats) }

        @android.webkit.JavascriptInterface
        fun onDiagnostic(message: String) = dispatch { callbacks.onDiagnostic(message) }

        @android.webkit.JavascriptInterface
        fun onFailure(message: String) = dispatch { callbacks.onFailure(message) }

        @android.webkit.JavascriptInterface
        fun onAudioLevels(stats: String) {
            val receivedAt = android.os.SystemClock.elapsedRealtime()
            dispatch {
                if (android.os.SystemClock.elapsedRealtime() - receivedAt < 600) callbacks.onAudioLevels(stats)
            }
        }

        @android.webkit.JavascriptInterface
        fun isAudioMeterEnabled(): Boolean = audioMeterEnabled

        @android.webkit.JavascriptInterface
        fun audioMeterRevision(): Long = audioMeterRevision

        @android.webkit.JavascriptInterface
        fun isMicrophoneMuted(): Boolean = microphoneMuted

        @android.webkit.JavascriptInterface
        fun isOutputMuted(): Boolean = outputMuted
    }

    private companion object {
        const val BRIDGE_NAME = "MonicaVoiceBridge"
        const val BASE_URL = "https://steamcommunity.com/chat/voice/"
        val HTML = """
            <!doctype html><meta name="viewport" content="width=device-width">
            <script>
            ${SteamVoiceAudioMeterScript.source}
            (() => {
              let pc = null;
              let stream = null;
              let statsTimer = null;
              let descriptionChain = Promise.resolve();
              let descriptionsPending = 0, meterWanted = false, meterRevision = 0;
              let microphoneMuted = false;
              let outputMuted = false;
              let microphonePermission = "unknown";
              const bridge = () => window.MonicaVoiceBridge;
              const audioMeter = createMonicaVoiceAudioMeter(
                () => pc, () => stream && stream.getAudioTracks()[0],
                () => ({microphoneMuted, outputMuted}), raw => bridge().onAudioLevels(raw));
              const fail = (e) => bridge().onFailure(String(e && e.message || e));
              const diagnostic = (message) => bridge().onDiagnostic(String(message));
              const reportIce = () => {
                if (pc) bridge().onIceStateChanged(pc.iceConnectionState || "new");
              };
              // Steam's official client normalises Opus before handing an SDP
              // to WebRTCClient. Keep the Android WebView offer byte-for-byte
              // compatible with that policy so the relay enables our sender.
              const normalizeOpus = (description) => {
                if (!description || !description.sdp) return description;
                const lines = description.sdp.split("\r\n");
                const opus = lines.map(line => line.match(/^a=rtpmap:(\d+)\s+opus(?:\/|\s)/i))
                  .find(Boolean);
                if (!opus) return description;
                const payload = opus[1];
                const fmtp = `a=fmtp:${'$'}{payload} minptime=10;useinbandfec=1;usedtx=1`;
                const index = lines.findIndex(line => line.startsWith(`a=fmtp:${'$'}{payload} `));
                if (index >= 0) lines[index] = fmtp;
                else {
                  const rtpIndex = lines.findIndex(line => line.startsWith(`a=rtpmap:${'$'}{payload} `));
                  lines.splice(rtpIndex + 1, 0, fmtp);
                }
                description.sdp = lines.join("\r\n");
                return description;
              };
              const reportMedia = async () => {
                if (!pc || !stream) return;
                try {
                  const track = stream.getAudioTracks()[0];
                  let bytes = 0, packets = 0, audioLevel = -1;
                  (await pc.getStats()).forEach(report => {
                    if (report.type === "outbound-rtp" && report.kind === "audio" && !report.isRemote) {
                      bytes = Number(report.bytesSent || 0);
                      packets = Number(report.packetsSent || 0);
                    }
                    if (report.type === "media-source" && report.kind === "audio") {
                      audioLevel = Number(report.audioLevel ?? -1);
                    }
                  });
                  bridge().onMediaStats(JSON.stringify({
                    bytes, packets, audioLevel,
                    enabled: !!track && track.enabled,
                    muted: !!track && track.muted,
                    readyState: track ? track.readyState : "missing",
                    permission: microphonePermission
                  }));
                } catch (e) { /* stats are diagnostic and must never end a call */ }
              };
              window.monicaVoice = {
                async start() {
                  try {
                    microphoneMuted = !!bridge().isMicrophoneMuted();
                    outputMuted = !!bridge().isOutputMuted();
                    try {
                      const permission = await navigator.permissions.query({ name: "microphone" });
                      microphonePermission = permission.state || "unknown";
                      permission.onchange = () => {
                        microphonePermission = permission.state || "unknown";
                      };
                    } catch (e) { microphonePermission = "unknown"; }
                    stream = await navigator.mediaDevices.getUserMedia({
                      audio: {
                        echoCancellation: true,
                        noiseSuppression: true,
                        autoGainControl: true,
                        channelCount: { ideal: 1 },
                        sampleRate: { ideal: 48000 }
                      },
                      video: false
                    });
                    try {
                      pc = new RTCPeerConnection({ sdpSemantics: "plan-b" });
                    } catch (planBError) {
                      diagnostic("plan-b unavailable; using unified-plan");
                      pc = new RTCPeerConnection();
                    }
                    stream.getTracks().forEach(t => {
                      if (t.kind === "audio" && "contentHint" in t) t.contentHint = "speech";
                      t.enabled = !microphoneMuted;
                      pc.addTrack(t, stream);
                    });
                    const localAudioTrack = stream.getAudioTracks()[0];
                    if (!localAudioTrack || localAudioTrack.readyState !== "live") {
                      throw new Error("Microphone audio track is not live");
                    }
                    bridge().onLocalMediaReady();
                    pc.oniceconnectionstatechange = reportIce;
                    pc.onconnectionstatechange = reportIce;
                    pc.ontrack = (event) => {
                      try {
                        let remote = Array.from(document.querySelectorAll("audio[data-monica-remote='1']"))
                          .find(a => a.dataset.monicaTrack === event.track.id);
                        if (!remote) {
                          remote = document.createElement("audio");
                          remote.autoplay = true;
                          remote.controls = false;
                          remote.dataset.monicaRemote = "1";
                          remote.dataset.monicaTrack = event.track.id;
                          document.body.appendChild(remote);
                        }
                        remote.srcObject = event.streams && event.streams[0]
                          ? event.streams[0] : new MediaStream([event.track]);
                        remote.muted = outputMuted;
                        const playRemote = () => remote.play().catch(error => {
                          diagnostic(`remote audio playback failed: ${'$'}{error && error.name || error}`);
                        });
                        remote.onloadedmetadata = playRemote;
                        playRemote();
                      } catch (e) { fail(e); }
                    };
                    const offer = normalizeOpus(await pc.createOffer({
                      offerToReceiveAudio: true,
                      voiceActivityDetection: true
                    }));
                    await pc.setLocalDescription(offer);
                    bridge().onLocalOffer(JSON.stringify(pc.localDescription));
                    statsTimer = window.setInterval(reportMedia, 5000);
                    audioMeter.setRevision(Number(bridge().audioMeterRevision()));
                    audioMeter.setEnabled(bridge().isAudioMeterEnabled());
                  } catch (e) { fail(e); }
                },
                setRemoteDescription(raw, wanted, revision) {
                  descriptionsPending++;
                  meterWanted = wanted; meterRevision = revision;
                  audioMeter.setEnabled(false);
                  descriptionChain = descriptionChain.then(async () => {
                    if (!pc) return;
                    const description = typeof raw === "string" ? JSON.parse(raw) : raw;
                    await pc.setRemoteDescription(description);
                    if (description.type === "offer") {
                      const answer = normalizeOpus(await pc.createAnswer());
                      await pc.setLocalDescription(answer);
                      bridge().onLocalAnswer(JSON.stringify(pc.localDescription));
                    }
                  }).catch(e => fail(e)).finally(() => {
                    descriptionsPending--;
                    if (pc && descriptionsPending === 0) {
                      audioMeter.setRevision(meterRevision);
                      audioMeter.setEnabled(meterWanted);
                    }
                  });
                  return descriptionChain;
                },
                setMicrophoneMuted(muted) {
                  microphoneMuted = !!muted;
                  if (stream) stream.getAudioTracks().forEach(t => t.enabled = !microphoneMuted);
                },
                setOutputMuted(muted) {
                  outputMuted = !!muted;
                  document.querySelectorAll("audio[data-monica-remote='1']").forEach(a => a.muted = outputMuted);
                },
                configureAudioMeter(enabled, revision) {
                  meterWanted = enabled; meterRevision = revision;
                  audioMeter.setRevision(revision);
                  audioMeter.setEnabled(enabled && descriptionsPending === 0);
                },
                stop() {
                  audioMeter.stop();
                  if (statsTimer) window.clearInterval(statsTimer);
                  if (pc) { try { pc.close(); } catch (e) {} }
                  if (stream) stream.getTracks().forEach(t => t.stop());
                  document.querySelectorAll("audio[data-monica-remote='1']").forEach(a => {
                    try { a.pause(); a.srcObject = null; } catch (e) {}
                    a.remove();
                  });
                  pc = null; stream = null; statsTimer = null;
                  descriptionChain = Promise.resolve();
                }
              };
              window.addEventListener("load", () => window.monicaVoice.start());
            })();
            </script>
        """.trimIndent()
    }
}
