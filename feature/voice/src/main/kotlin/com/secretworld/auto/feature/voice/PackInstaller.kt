package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Descarga, verifica e instala un paquete zip en un directorio, de forma
 * atomica. Es la mecanica comun a las voces y a los modelos de escucha: lo que
 * cambia entre ellos es el catalogo y el destino, no como se bajan.
 *
 * Las reglas son las de SPEC RF-1.8: progreso real, `sha256` antes de instalar
 * y nunca un directorio a medias.
 */
internal class PackInstaller(private val context: Context) {

    /**
     * @param bundledAsset nombre del asset con el paquete dentro del APK, si lo
     *   hay. Se prefiere a la red: ni espera ni cobertura. Lo usa el banco de
     *   pruebas y es tambien una opcion de producto para preinstalar un idioma.
     */
    fun install(
        url: String,
        bundledAsset: String?,
        expectedBytes: Long,
        sha256: String,
        target: File,
        workName: String,
        onProgress: (VoiceInstaller.Progress) -> Unit,
        /** Lo hecho de este paquete, de 0 a 1, en las tres fases: copiar, comprobar y descomprimir. */
        onFraction: (Float) -> Unit = {},
    ): Result<File> {
        val work = File(context.cacheDir, workName)
        work.deleteRecursively()
        work.mkdirs()
        val archive = File(work, "pack.zip")

        return runCatching {
            if (bundledAsset != null) {
                Log.i(TAG, "instalando $workName desde el propio APK")
                copyAsset(bundledAsset, archive, expectedBytes, onProgress) { onFraction(COPY_SHARE * it) }
            } else {
                download(url, archive, expectedBytes, onProgress)
            }

            onProgress(VoiceInstaller.Progress.Verifying)
            val actual = sha256Of(archive) { onFraction(COPY_SHARE + VERIFY_SHARE * it) }
            require(actual.equals(sha256, ignoreCase = true)) {
                "checksum no coincide: esperado ${sha256.take(16)}, obtenido ${actual.take(16)}"
            }

            onProgress(VoiceInstaller.Progress.Extracting)
            val staging = File(work, "out")
            unzip(archive, staging) { onFraction(COPY_SHARE + VERIFY_SHARE + (1 - COPY_SHARE - VERIFY_SHARE) * it) }

            // Instalacion atomica: se monta aparte y se mueve al final, para que
            // nunca exista un directorio a medias.
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            require(staging.renameTo(target)) { "no se pudo mover a ${target.absolutePath}" }

            work.deleteRecursively()
            Log.i(TAG, "$workName instalado en $target")
            onProgress(VoiceInstaller.Progress.Done(target))
            target
        }.onFailure {
            work.deleteRecursively()
            onProgress(VoiceInstaller.Progress.Failed(it.message ?: it.toString()))
            Log.e(TAG, "fallo instalando $workName", it)
        }
    }

    /** Si existe un asset con ese nombre dentro del APK. */
    fun hasAsset(name: String): Boolean = runCatching {
        context.assets.open(name).close()
        true
    }.getOrDefault(false)

    /**
     * El tamaño se toma del catalogo, no de `openFd`: un asset comprimido dentro
     * del APK no tiene descriptor de fichero y `openFd` lanza.
     */
    private fun copyAsset(
        assetName: String,
        into: File,
        expectedBytes: Long,
        onProgress: (VoiceInstaller.Progress) -> Unit,
        onFraction: (Float) -> Unit = {},
    ) {
        var read = 0L
        context.assets.open(assetName).use { input ->
            into.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    read += n
                    onFraction((read.toFloat() / expectedBytes.coerceAtLeast(1)).coerceAtMost(1f))
                }
            }
        }
        onProgress(VoiceInstaller.Progress.Downloading(read, expectedBytes))
    }

    private fun download(url: String, into: File, expectedBytes: Long, onProgress: (VoiceInstaller.Progress) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode} al pedir $url"
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedBytes
            var read = 0L
            var lastReported = 0L
            connection.inputStream.use { input ->
                into.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        // No inundar la UI: se informa cada 5 %.
                        if (read - lastReported > total / 20) {
                            lastReported = read
                            onProgress(VoiceInstaller.Progress.Downloading(read, total))
                        }
                    }
                }
            }
            onProgress(VoiceInstaller.Progress.Downloading(read, total))
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Of(file: File, onFraction: (Float) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val total = file.length().coerceAtLeast(1)
        var read = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
                read += n
                onFraction((read.toFloat() / total).coerceAtMost(1f))
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzip(archive: File, into: File, onFraction: (Float) -> Unit = {}) {
        into.mkdirs()
        val root = into.canonicalPath
        val total = archive.length().coerceAtLeast(1)
        var read = 0L
        // Cuenta lo leído del zip, no lo escrito: el total que se conoce es el del archivo.
        val counting = object : java.io.FilterInputStream(archive.inputStream()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                if (n > 0) {
                    read += n
                    onFraction((read.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        ZipInputStream(counting.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val out = File(into, entry.name)
                // Zip slip: una entrada con ../ escribiria fuera del directorio.
                require(out.canonicalPath.startsWith(root)) { "entrada fuera de destino: ${entry.name}" }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().buffered().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    private companion object {
        const val TAG = "SWVoice"
        /** Lo que pesa cada fase en la barra: copiar del APK, comprobar y descomprimir. */
        const val COPY_SHARE = 0.35f
        const val VERIFY_SHARE = 0.15f
    }
}
