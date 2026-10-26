package com.bnx.clipme

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.OutputStream

/**
 * MP3 encoder (LAME, via the pure-Java jump3r port; LGPL 2.1+) at the highest quality: CBR 320 kbps, quality 2.
 * Feed 16-bit interleaved PCM to [write], then call [finish]. The module wiring mirrors jump3r's own `LameEncoder`,
 * which cannot be used on Android because it depends on javax.sound.
 */
class Mp3Encoder(private val out: OutputStream, sampleRate: Int, private val channels: Int) {
    private val lame = Lame()
    private val gfp: LameGlobalFlags

    init {
        require(channels in 1..2) { "only mono and stereo are supported" }
        val ga = GainAnalysis()
        val bs = BitStream()
        val p = Presets()
        val qupvt = QuantizePVT()
        val qu = Quantize()
        val vbr = VBRTag()
        val ver = Version()
        val id3 = ID3Tag()
        val rv = Reservoir()
        val tak = Takehiro()
        val mpg = MPGLib()
        val intf = Interface()
        val common = Common()
        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg)
        bs.setModules(ga, mpg, ver, vbr)
        id3.setModules(bs, ver)
        p.setModules(lame)
        qu.setModules(bs, rv, qupvt, tak)
        qupvt.setModules(tak, rv, lame.enc.psy)
        rv.setModules(bs)
        tak.setModules(qupvt)
        vbr.setModules(lame, bs, ver)
        mpg.setModules(intf, common)
        intf.setModules(vbr, common)

        gfp = lame.lame_init()
        gfp.num_channels = channels
        gfp.in_samplerate = sampleRate
        gfp.mode = if (channels == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        gfp.VBR = VbrMode.vbr_off
        gfp.brate = if (sampleRate < 32000) 160 else 320 // MPEG-2 caps at 160 kbps
        gfp.quality = 2
        id3.id3tag_init(gfp)
        gfp.write_id3tag_automatic = false
        gfp.findReplayGain = false
        check(lame.lame_init_params(gfp) >= 0) { "LAME rejected the parameters" }
    }

    /** [frames] sample frames of interleaved 16-bit PCM in [pcm]. */
    fun write(pcm: ShortArray, frames: Int) {
        if (frames <= 0) return
        val left = IntArray(frames)
        val right = IntArray(frames)
        for (i in 0 until frames) {
            left[i] = pcm[i * channels].toInt() shl 16
            right[i] = if (channels == 2) pcm[i * channels + 1].toInt() shl 16 else left[i]
        }
        val buf = ByteArray(frames * 5 / 4 + 7200)
        val n = lame.lame_encode_buffer_int(gfp, left, right, frames, buf, 0, buf.size)
        check(n >= 0) { "LAME encode error $n" }
        out.write(buf, 0, n)
    }

    fun finish() {
        val buf = ByteArray(7200)
        val n = lame.lame_encode_flush(gfp, buf, 0, buf.size)
        if (n > 0) out.write(buf, 0, n)
        lame.lame_close(gfp)
    }
}