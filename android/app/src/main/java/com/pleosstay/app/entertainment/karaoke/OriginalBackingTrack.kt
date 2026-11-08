package com.pleosstay.app.entertainment.karaoke

import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/** Creates an actual original PCM16 WAV in Entertainment-owned private storage, never synthetic microphone input. */
object OriginalBackingTrack {
    const val SAMPLE_RATE=16_000
    const val DURATION_MILLIS=8_000L
    fun create(directory:File):File {
        check(directory.isDirectory || directory.mkdirs())
        val target=File(directory,"stay-exercise-v1.wav")
        val bytes=wavBytes()
        val temp=File.createTempFile("backing-",".wav",directory)
        try {
            FileOutputStream(temp).use{it.write(bytes);it.fd.sync()}
            java.nio.file.Files.move(temp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }finally{temp.delete()}
        return target
    }
    fun wavBytes():ByteArray {
        val count=(SAMPLE_RATE*DURATION_MILLIS/1000).toInt()
        val output=java.nio.ByteBuffer.allocate(44+count*2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray(Charsets.US_ASCII));output.putInt(36+count*2);output.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
        output.putInt(16);output.putShort(1);output.putShort(1);output.putInt(SAMPLE_RATE);output.putInt(SAMPLE_RATE*2);output.putShort(2);output.putShort(16)
        output.put("data".toByteArray(Charsets.US_ASCII));output.putInt(count*2)
        repeat(count){i->
            val time=i.toDouble()/SAMPLE_RATE
            val chord=if(time<4)listOf(48,52,55) else listOf(43,47,50)
            val envelope=minOf(1.0,time/0.03,(8-time)/0.08).coerceAtLeast(0.0)
            val value=chord.sumOf{midi->sin(2*PI*440*2.0.pow((midi-69)/12.0)*time)}*0.07*envelope
            output.putShort((value*Short.MAX_VALUE).toInt().toShort())
        };return output.array()
    }
}
