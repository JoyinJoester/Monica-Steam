package takagi.ru.monica.steam.friends.voice.media

/** Shared by the hidden WebRTC engine and the on-device synthetic loopback test.
 * Reads scalar RTC stats only; no extra capture, decoded samples or recording. */
internal object SteamVoiceAudioMeterScript {
    val source = """
        function createMonicaVoiceAudioMeter(getPeer, getTrack, getGates, emit) {
          let timer = null, epoch = 0, revision = 0, enabled = false, busy = false;
          let previous = new Map();
          const level = (report, next) => {
            const old = previous.get(report.id);
            next.set(report.id, report);
            if (old && report.timestamp <= old.timestamp) return 0;
            if (Number.isFinite(report.audioLevel)) return Math.max(0, Math.min(1, report.audioLevel));
            if (!old) return 0;
            const energy = report.totalAudioEnergy - old.totalAudioEnergy;
            const duration = report.totalSamplesDuration - old.totalSamplesDuration;
            return Number.isFinite(energy) && Number.isFinite(duration) && energy >= 0 && duration > 0
              ? Math.min(1, Math.sqrt(energy / duration)) : 0;
          };
          const sample = async () => {
            const peer = getPeer(), token = epoch, version = revision;
            if (!enabled || busy || !peer) return;
            busy = true;
            try {
              const stats = await peer.getStats();
              if (!enabled || token !== epoch || peer !== getPeer()) return;
              const gates = getGates(), track = getTrack(), next = new Map();
              let local = 0;
              const remote = [];
              stats.forEach(report => {
                if ((report.kind || report.mediaType) !== 'audio') return;
                if (report.type === 'media-source') {
                  const value = level(report, next);
                  if (!gates.microphoneMuted && track && track.enabled && !track.muted && track.readyState === 'live') {
                    local = Math.max(local, value);
                  }
                } else if (report.type === 'inbound-rtp' && !report.isRemote && remote.length < 64) {
                  const value = level(report, next);
                  if (!gates.outputMuted && Number.isInteger(report.ssrc) && report.ssrc >= 0) {
                    remote.push({ssrc: report.ssrc, level: value});
                  }
                }
              });
              previous = next;
              emit(JSON.stringify({revision: version, local, remote}));
            } catch (e) { /* A missing meter must never interrupt the call. */ }
            finally { busy = false; }
          };
          return {
            setEnabled(value) {
              enabled = !!value; epoch++; previous.clear();
              if (timer !== null) clearInterval(timer);
              timer = enabled ? setInterval(sample, 120) : null;
            },
            setRevision(value) { revision = value; epoch++; previous.clear(); },
            stop() { this.setEnabled(false); },
            sample
          };
        }
    """.trimIndent()
}
