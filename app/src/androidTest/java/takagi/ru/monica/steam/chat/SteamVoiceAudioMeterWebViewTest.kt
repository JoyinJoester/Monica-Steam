package takagi.ru.monica.steam.chat

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import takagi.ru.monica.steam.SteamComposeFixtureActivity
import takagi.ru.monica.steam.friends.voice.media.SteamVoiceAudioMeterScript

/** Real platform WebRTC loopback with a generated tone. No microphone, account or remote server. */
class SteamVoiceAudioMeterWebViewTest {
    @get:Rule val rule = createAndroidComposeRule<SteamComposeFixtureActivity>()
    class Probe {
        val complete = CountDownLatch(1)
        @Volatile var result = ""
        @JavascriptInterface fun done(value: String) { result = value; complete.countDown() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test fun productionSamplerReadsRealLocalAndRemoteAudioAndStopsCleanly() {
        val probe = Probe()
        lateinit var view: WebView
        rule.runOnUiThread {
            view = WebView(rule.activity).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                addJavascriptInterface(probe, "Probe")
            }
            rule.activity.setContentView(view)
            view.loadDataWithBaseURL("https://localhost/", """
                <!doctype html><script>
                ${SteamVoiceAudioMeterScript.source}
                (async () => {
                  const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
                  const check = (condition, message) => { if (!condition) throw new Error(message); };
                  let context, a, b, oscillator, sender, receiver;
                  try {
                    context = new AudioContext(); await context.resume();
                    oscillator = context.createOscillator(); oscillator.frequency.value = 440;
                    const gain = context.createGain(); gain.gain.value = .45;
                    const destination = context.createMediaStreamDestination();
                    oscillator.connect(gain); gain.connect(destination); oscillator.start();
                    const track = destination.stream.getAudioTracks()[0];
                    a = new RTCPeerConnection({iceServers: []}); b = new RTCPeerConnection({iceServers: []});
                    a.onicecandidate = e => { if(e.candidate) b.addIceCandidate(e.candidate).catch(() => {}); };
                    b.onicecandidate = e => { if(e.candidate) a.addIceCandidate(e.candidate).catch(() => {}); };
                    b.ontrack = e => {
                      const audio = document.createElement('audio'); audio.autoplay = true;
                      audio.volume = .1;
                      audio.srcObject = new MediaStream([e.track]); document.body.appendChild(audio);
                      audio.play().catch(error => Probe.done(JSON.stringify({ok:false,error:'Playback: '+error})));
                    };
                    a.addTrack(track, destination.stream);
                    await a.setLocalDescription(await a.createOffer()); await b.setRemoteDescription(a.localDescription);
                    await b.setLocalDescription(await b.createAnswer()); await a.setRemoteDescription(b.localDescription);
                    let gates = {microphoneMuted:false, outputMuted:false}, local = [], remote = [];
                    sender = createMonicaVoiceAudioMeter(() => a, () => track, () => gates, raw => local.push(JSON.parse(raw)));
                    receiver = createMonicaVoiceAudioMeter(() => b, () => null, () => gates, raw => remote.push(JSON.parse(raw)));
                    sender.setRevision(7); receiver.setRevision(7); sender.setEnabled(true); receiver.setEnabled(true);
                    for (let i=0; i<80; i++) {
                      if (local.some(v => v.local > .01) && remote.some(v => v.remote.some(r => r.level > .01))) break;
                      await wait(120);
                    }
                    const localPeak = Math.max(...local.map(v => v.local), 0);
                    const remotePeak = Math.max(...remote.flatMap(v => v.remote.map(r => r.level)), 0);
                    const ssrc = remote.flatMap(v => v.remote).find(r => r.level > .01)?.ssrc;
                    check(localPeak > .01, 'No local RTC audio level');
                    check(remotePeak > .01 && Number.isInteger(ssrc), 'No attributed inbound RTC level: ' + JSON.stringify({localPeak,remotePeak,iceA:a.iceConnectionState,iceB:b.iceConnectionState,stats:Array.from((await b.getStats()).values()).filter(r => r.type === 'inbound-rtp')}));
                    gates = {microphoneMuted:true, outputMuted:true};
                    sender.setRevision(8); receiver.setRevision(8); await wait(360);
                    check(local.filter(v => v.revision === 8).length > 0 && local.filter(v => v.revision === 8).every(v => v.local === 0), 'Local mute');
                    check(remote.filter(v => v.revision === 8).length > 0 && remote.filter(v => v.revision === 8).every(v => v.remote.length === 0), 'Output mute');
                    gates = {microphoneMuted:false, outputMuted:false}; gain.gain.value = 0;
                    await wait(900);
                    check(local.at(-1).local < .008 && remote.at(-1).remote.every(v => v.level < .008), 'Silence');
                    sender.stop(); receiver.stop(); const count = local.length + remote.length;
                    await wait(360); check(count === local.length + remote.length, 'Sampler continued after stop');

                    // Exercise the same production sampler for absent audioLevel, stale stats, and late promises.
                    let reports = [], samples = [], resolveStats;
                    const fake = {getStats: async () => reports};
                    const meter = createMonicaVoiceAudioMeter(() => fake, () => track, () => gates, raw => samples.push(JSON.parse(raw)));
                    meter.setRevision(9); meter.setEnabled(true);
                    reports = [{id:'energy',type:'inbound-rtp',kind:'audio',ssrc:4294967295,timestamp:100,totalAudioEnergy:1,totalSamplesDuration:1}];
                    await meter.sample();
                    reports = [{...reports[0],timestamp:200,totalAudioEnergy:1.25,totalSamplesDuration:2}];
                    await meter.sample(); check(samples.at(-1).remote[0].level === .5, 'Energy fallback');
                    await meter.sample(); check(samples.at(-1).remote[0].level === 0, 'Stale RTC timestamp');
                    fake.getStats = () => new Promise(resolve => resolveStats = resolve);
                    const pending = meter.sample(); const before = samples.length;
                    meter.setRevision(10); resolveStats(reports); await pending;
                    check(samples.length === before, 'Old revision promise emitted'); meter.stop();
                    Probe.done(JSON.stringify({ok:true,localPeak,remotePeak,ssrc,samples:count,
                      mute:true,silence:true,stopped:true,energyFallback:true,staleTimestamp:true,latePromiseRejected:true}));
                  } catch(e) { Probe.done(JSON.stringify({ok:false,error:String(e.stack || e)})); }
                  finally { sender?.stop(); receiver?.stop(); oscillator?.stop(); a?.close(); b?.close(); await context?.close(); }
                })();
                </script>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        try {
            assertTrue("WebRTC loopback timed out", probe.complete.await(25, TimeUnit.SECONDS))
            File(rule.activity.filesDir, "voice-motion").mkdirs()
            File(rule.activity.filesDir, "voice-motion/rtc-result.json").writeText(probe.result)
            assertTrue(probe.result, JSONObject(probe.result).optBoolean("ok"))
        } finally { rule.runOnUiThread { view.removeJavascriptInterface("Probe"); view.destroy() } }
    }
}
