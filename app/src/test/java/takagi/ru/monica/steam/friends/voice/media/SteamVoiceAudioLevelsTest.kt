package takagi.ru.monica.steam.friends.voice.media

import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.steam.friends.voice.domain.*

class SteamVoiceAudioLevelsTest {
    private val connected = SteamVoiceCallState(accountSteamId = "self",
        target = SteamVoiceTarget(SteamVoiceTargetType.DIRECT, "Peer", partnerSteamId = "peer"),
        state = SteamVoiceConnectionState.CONNECTED, participants = listOf(SteamVoiceParticipant("peer")))
    private fun sample(meter: SteamVoiceAudioLevels) = SteamVoiceAudioSample(meter.revision, .25f,
        mapOf(42L to .36f, 43L to .49f, 999L to 1f))

    @Test fun mapsOnlyKnownStreamsAndCombinesSameMemberByMaximum() {
        val meter = SteamVoiceAudioLevels(); meter.replaceMapping(mapOf(42L to "peer", 43L to "peer"))
        meter.accept(sample(meter), connected, 100)
        assertEquals(mapOf("self" to .5f, "peer" to .7f), meter.values)
    }
    @Test fun localMuteAndRemoteMuteAreIndependent() {
        val meter = SteamVoiceAudioLevels(); meter.replaceMapping(mapOf(42L to "peer"))
        meter.accept(sample(meter), connected.copy(microphoneMuted = true), 100)
        assertEquals(setOf("peer"), meter.values.keys)
        meter.accept(sample(meter), connected.copy(outputMuted = true), 200)
        assertEquals(setOf("self"), meter.values.keys)
    }
    @Test fun mutedAbsentAndLeftParticipantsCannotSpeak() {
        val meter = SteamVoiceAudioLevels(); meter.replaceMapping(mapOf(42L to "peer", 43L to "unknown"))
        for (p in listOf(SteamVoiceParticipant("peer", micMuted = true),
            SteamVoiceParticipant("peer", hasNoMic = true), SteamVoiceParticipant("peer", joined = false))) {
            meter.accept(sample(meter), connected.copy(participants = listOf(p)), 100)
            assertEquals(setOf("self"), meter.values.keys)
        }
        meter.removeParticipant("peer")
        meter.accept(sample(meter), connected, 200)
        assertEquals(setOf("self"), meter.values.keys)
    }
    @Test fun reconnectAndEndedCallsClearAllLevels() {
        val meter = SteamVoiceAudioLevels(); meter.replaceMapping(mapOf(42L to "peer"))
        for (state in SteamVoiceConnectionState.entries.filter { it != SteamVoiceConnectionState.CONNECTED }) {
            meter.accept(sample(meter), connected, 100)
            meter.accept(sample(meter), connected.copy(state = state), 200)
            assertTrue(meter.values.isEmpty())
        }
    }
    @Test fun mappingChangesRejectLateOldReportsEvenForReusedSsrc() {
        val meter = SteamVoiceAudioLevels(); meter.replaceMapping(mapOf(42L to "peer"))
        val stale = sample(meter)
        meter.replaceMapping(mapOf(42L to "next"))
        meter.accept(stale, connected, 100)
        assertTrue(meter.values.isEmpty())
        meter.invalidate()
        meter.accept(stale, connected, 200)
        assertTrue(meter.values.isEmpty())
    }
    @Test fun expiresStalledReportsAndSilenceImmediatelyReplacesSpeaking() {
        val meter = SteamVoiceAudioLevels()
        meter.accept(sample(meter), connected, 100)
        meter.expire(699); assertFalse(meter.values.isEmpty())
        meter.expire(700); assertTrue(meter.values.isEmpty())
        meter.accept(sample(meter), connected, 800)
        meter.accept(SteamVoiceAudioSample(meter.revision, .001f, emptyMap()), connected, 900)
        assertTrue(meter.values.isEmpty())
    }
    @Test fun rejectsInvalidLevelsAndBoundsLargeValues() {
        val meter = SteamVoiceAudioLevels()
        for (level in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -.5f)) {
            meter.accept(SteamVoiceAudioSample(meter.revision, level, emptyMap()), connected, 0)
            assertTrue(meter.values.isEmpty())
        }
        meter.accept(SteamVoiceAudioSample(meter.revision, 3f, emptyMap()), connected, 0)
        assertEquals(1f, meter.values["self"])
    }
    @Test fun parsesUnsignedSsrcAndRejectsMalformedOrOversizedPayloads() {
        val sample = SteamVoiceAudioSample.parse("""{"revision":7,"local":0.25,"remote":[{"ssrc":4294967295,"level":0.5}]}""")!!
        assertEquals(7L, sample.revision); assertEquals(.5f, sample.remote[4294967295])
        for (raw in listOf("{}", "[]", "x".repeat(16385),
            """{"revision":1,"local":0,"remote":[{"ssrc":-1,"level":1}]}""",
            """{"revision":1,"local":0,"remote":[${List(65) { "{\"ssrc\":1,\"level\":1}" }.joinToString()}]}""")) {
            assertNull(SteamVoiceAudioSample.parse(raw))
        }
    }
}
