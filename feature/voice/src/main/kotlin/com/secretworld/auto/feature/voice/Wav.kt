package com.secretworld.auto.feature.voice

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Une WAV del mismo formato —los de una misma voz— en uno solo. */
internal object Wav {

    fun join(parts: List<File>, out: File) {
        require(parts.isNotEmpty()) { "nada que unir" }
        val chunks = parts.map { read(it) }
        val (format, _) = chunks.first()
        val data = chunks.sumOf { it.second.size }
        // RIFF y WAVE, el bloque de formato con su cabecera y la de los datos.
        val header = 12 + 8 + format.size + 8
        val bytes = ByteBuffer.allocate(header + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(header - 8 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(format.size); put(format)
            put("data".toByteArray()); putInt(data)
            chunks.forEach { put(it.second) }
        }
        val tmp = File(out.path + ".tmp")
        tmp.writeBytes(bytes.array().copyOf(bytes.position()))
        tmp.renameTo(out)
    }

    /** El bloque "fmt " y las muestras de un WAV. */
    private fun read(file: File): Pair<ByteArray, ByteArray> {
        val bytes = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        bytes.position(12)
        var format: ByteArray? = null
        while (bytes.remaining() >= 8) {
            val id = ByteArray(4).also { bytes.get(it) }.decodeToString()
            val size = bytes.int
            val body = ByteArray(minOf(size, bytes.remaining())).also { bytes.get(it) }
            if (size % 2 == 1 && bytes.hasRemaining()) bytes.get()
            when (id) {
                "fmt " -> format = body
                "data" -> return requireNotNull(format) { "WAV sin formato: $file" } to body
            }
        }
        error("WAV sin datos: $file")
    }
}
