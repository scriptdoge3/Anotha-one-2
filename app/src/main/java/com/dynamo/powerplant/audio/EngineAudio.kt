package com.dynamo.powerplant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.dynamo.powerplant.sim.Plant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything you hear is synthesised: there are no sample files. The exhaust
 * beat follows the firing events out of the engine model, the hum follows the
 * alternator's actual frequency, and the knock appears when it is detonating.
 */
class EngineAudio {

    companion object {
        const val RATE = 22050
        const val CHUNK = 512
    }

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val rnd = Random(7)

    // --- values handed over from the game thread ---
    @Volatile private var exhaustQueue = 0
    @Volatile private var backfireQueue = 0
    @Volatile private var clunkQueue = 0
    @Volatile private var tickQueue = 0
    @Volatile private var humHz = 0f
    @Volatile private var humLevel = 0f
    @Volatile private var rumbleLevel = 0f
    @Volatile private var knockLevel = 0f
    @Volatile private var starterLevel = 0f
    @Volatile private var rpmNorm = 0f

    // --- voice state, audio thread only ---
    private var humPhase = 0.0
    private var starterPhase = 0.0
    private var exhaustEnv = 0f
    private var exhaustPhase = 0.0
    private var exhaustPitch = 62.0
    private var backfireEnv = 0f
    private var clunkEnv = 0f
    private var tickEnv = 0f
    private var knockPhase = 0.0
    private var lowpass = 0f

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK * 8)
        track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuf, AudioTrack.MODE_STREAM
            )
        }
        val t = track ?: return
        if (t.state != AudioTrack.STATE_INITIALIZED) { track = null; return }
        running = true
        t.play()
        thread = Thread({ pump(t) }, "dynamo-audio").also { it.priority = Thread.MAX_PRIORITY; it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        try { track?.pause(); track?.flush(); track?.release() } catch (_: Throwable) {}
        track = null
    }

    /** Called once per frame from the game loop. */
    fun update(p: Plant, dt: Double) {
        if (p.events.fired) exhaustQueue++
        if (p.events.backfired) backfireQueue++
        if (p.events.breakerClosed || p.events.breakerOpened || p.events.knifeSwitch) clunkQueue++

        val hz = p.hz.toFloat()
        humHz = if (hz > 5f) hz * 2f else 0f            // the 120 cycle growl off the iron
        val volts = (p.genVolts / 2300.0).toFloat().coerceIn(0f, 1.3f)
        val load = (abs(p.outputKw) / 120.0).toFloat().coerceIn(0f, 1.4f)
        humLevel = (volts * 0.055f + load * 0.10f).coerceIn(0f, 0.20f)
        rpmNorm = (p.rpm / 600.0).toFloat().coerceIn(0f, 1.8f)
        rumbleLevel = if (p.rpm > 20) (0.035f + rpmNorm * 0.045f) else 0f
        knockLevel = p.engine.knockIndex.toFloat().coerceIn(0f, 1f)
        starterLevel = if (p.engine.starterCranking) 0.16f else 0f
    }

    fun clunk() { clunkQueue++ }
    fun tick() { tickQueue++ }

    private fun pump(t: AudioTrack) {
        val buf = ShortArray(CHUNK)
        while (running) {
            fill(buf)
            try { t.write(buf, 0, buf.size) } catch (_: Throwable) { return }
        }
    }

    private fun fill(out: ShortArray) {
        // consume any events that arrived since the last chunk
        while (exhaustQueue > 0) {
            exhaustQueue--
            exhaustEnv = 1f
            exhaustPhase = 0.0
            // A faster engine gives a sharper, higher crack from the stack.
            exhaustPitch = 48.0 + rpmNorm * 34.0
        }
        while (backfireQueue > 0) { backfireQueue--; backfireEnv = 1f }
        while (clunkQueue > 0) { clunkQueue--; clunkEnv = 1f }
        while (tickQueue > 0) { tickQueue--; tickEnv = 1f }

        val hz = humHz
        val hl = humLevel
        val rl = rumbleLevel
        val kl = knockLevel
        val sl = starterLevel

        for (i in out.indices) {
            var s = 0f

            // --- exhaust beat: a soft thump with a noisy edge ---
            if (exhaustEnv > 0.0005f) {
                exhaustPhase += exhaustPitch * 2.0 * PI / RATE
                val body = sin(exhaustPhase).toFloat() * 0.75f + sin(exhaustPhase * 2.02).toFloat() * 0.25f
                val edge = (rnd.nextFloat() * 2f - 1f) * exhaustEnv * exhaustEnv * 0.55f
                s += (body * exhaustEnv + edge) * 0.42f
                exhaustEnv *= 0.99955f - 0.00035f * rpmNorm
                if (exhaustEnv < 0.0005f) exhaustEnv = 0f
            }

            // --- unburnt charge going off in the pipe ---
            if (backfireEnv > 0.0005f) {
                s += (rnd.nextFloat() * 2f - 1f) * backfireEnv * 0.55f
                backfireEnv *= 0.9990f
            }

            // --- the alternator's hum, which is the sound of being in step ---
            if (hz > 1f && hl > 0.0005f) {
                humPhase += hz * 2.0 * PI / RATE
                if (humPhase > 2 * PI) humPhase -= 2 * PI
                s += (sin(humPhase).toFloat() * 0.7f + sin(humPhase * 2.0).toFloat() * 0.3f) * hl
            }

            // --- flywheel and bearing rumble ---
            if (rl > 0.0005f) {
                lowpass += ((rnd.nextFloat() * 2f - 1f) - lowpass) * 0.035f
                s += lowpass * rl
            }

            // --- detonation: a hard ping riding on the beat ---
            if (kl > 0.05f && exhaustEnv > 0.25f) {
                knockPhase += 2600.0 * 2.0 * PI / RATE
                s += sin(knockPhase).toFloat() * kl * exhaustEnv * 0.22f
            }

            // --- the starting motor whine ---
            if (sl > 0.0005f) {
                starterPhase += (240.0 + rpmNorm * 500.0) * 2.0 * PI / RATE
                s += (sin(starterPhase).toFloat() * 0.5f + sin(starterPhase * 1.5).toFloat() * 0.5f) * sl
            }

            // --- switchgear ---
            if (clunkEnv > 0.0005f) {
                s += ((rnd.nextFloat() * 2f - 1f) * 0.6f + sin(clunkEnv * 40.0).toFloat() * 0.4f) * clunkEnv * 0.45f
                clunkEnv *= 0.9982f
            }
            if (tickEnv > 0.0005f) {
                s += (rnd.nextFloat() * 2f - 1f) * tickEnv * 0.18f
                tickEnv *= 0.9955f
            }

            out[i] = (min(1f, kotlin.math.max(-1f, s)) * 20000f).toInt().toShort()
        }
    }
}
