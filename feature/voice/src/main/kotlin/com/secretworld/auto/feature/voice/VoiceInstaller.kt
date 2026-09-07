package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import com.secretworld.auto.domain.VoiceLanguage
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Instala voces en el dispositivo.
 *
 * El flujo de producto es: al arrancar por primera vez se instala el idioma que
 * el usuario elige; los demas se instalan cuando los pide. Hasta que la voz esta
 * instalada la app **no puede narrar, y lo dice** — degradar en silencio seria
 * peor que no funcionar (misma regla que RF-2.6).
 */
class VoiceInstaller(private val context: Context) {

    sealed interface Progress {
        data class Downloading(val bytesRead: Long, val bytesTotal: Long) : Progress {
            val fraction: Float get() = if (bytesTotal > 0) bytesRead.toFloat() / bytesTotal else 0f
        }
        data object Verifying : Progress
        data object Extracting : Progress
        data class Done(val installDir: File) : Progress
        data class Failed(val reason: String) : Progress
    }

    fun isInstalled(language: VoiceLanguage): Boolean {
        val dir = installDirFor(language)
        return dir.isDirectory && dir.listFiles()?.any { it.name.endsWith(".onnx") } == true
    }

    fun installedLanguages(): Set<VoiceLanguage> =
        VoiceLanguage.entries.filter { isInstalled(it) }.toSet()

    fun installDirFor(language: VoiceLanguage): File =
        File(context.filesDir, "voices/${language.code}")

    /** Borra una voz instalada. El disco del vehiculo no es infinito. */
    fun uninstall(language: VoiceLanguage): Boolean = installDirFor(language).deleteRecursively()

    /**
     * Descarga, verifica e instala. Bloquea: llamar desde un hilo de fondo.
     * Informa por [onProgress] para que la UI pueda enseñar algo honesto.
     */
    fun install(language: VoiceLanguage, onProgress: (Progress) -> Unit = {}): Result<File> {
        val pack = VoiceCatalog.packFor(language)
            ?: return Result.failure(IllegalArgumentException("no hay voz para ${language.code}"))

        val target = installDirFor(language)
        if (isInstalled(language)) {
            onProgress(Progress.Done(target))
            return Result.success(target)
        }

        val work = File(context.cacheDir, "voice-${language.code}")
        work.deleteRecursively()
        work.mkdirs()
        val archive = File(work, "pack.zip")

        return runCatching {
            // Si la voz viene empaquetada en la app, se instala desde ahi: ni red
            // ni espera. Lo usa el sabor de banco de pruebas, y es tambien una
            // opcion de producto si algun dia interesa preinstalar un idioma.
            val bundled = bundledAsset(language)
            if (bundled != null) {
                Log.i(TAG, "instalando voz ${language.code} desde el propio APK")
                copyAsset(bundled, archive, pack.bytes, onProgress)
            } else {
                download(VoiceCatalog.urlFor(pack), archive, pack.bytes, onProgress)
            }

            onProgress(Progress.Verifying)
            val actual = sha256Of(archive)
            require(actual.equals(pack.sha256, ignoreCase = true)) {
                "checksum no coincide: esperado ${pack.sha256.take(16)}, obtenido ${actual.take(16)}"
            }

            onProgress(Progress.Extracting)
            val staging = File(work, "out")
            unzip(archive, staging)

            // Instalacion atomica: se monta aparte y se mueve al final, para que
            // nunca exista un directorio de voz a medias.
            target.deleteRecursively()
            target.parentFile?.mkdirs()
            require(staging.renameTo(target)) { "no se pudo mover la voz a ${target.absolutePath}" }

            work.deleteRecursively()
            Log.i(TAG, "voz ${language.code} instalada en $target")
            onProgress(Progress.Done(target))
            target
        }.onFailure {
            work.deleteRecursively()
            onProgress(Progress.Failed(it.message ?: it.toString()))
            Log.e(TAG, "fallo instalando ${language.code}", it)
        }
    }

    /** Nombre del asset con la voz empaquetada, si existe. */
    private fun bundledAsset(language: VoiceLanguage): String? {
        val name = "voices/${language.code}.zip"
        return runCatching {
            context.assets.open(name).close()
            name
        }.getOrNull()
    }

    /**
     * El tamaño se toma del catalogo, no de `openFd`: un asset comprimido dentro
     * del APK no tiene descriptor de fichero y `openFd` lanza.
     */
    private fun copyAsset(
        assetName: String,
        into: File,
        expectedBytes: Long,
        onProgress: (Progress) -> Unit,
    ) {
        val total = expectedBytes
        var read = 0L
        context.assets.open(assetName).use { input ->
            into.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    read += n
                }
            }
        }
        onProgress(Progress.Downloading(read, total))
    }

    private fun download(url: String, into: File, expectedBytes: Long, onProgress: (Progress) -> Unit) {
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
                            onProgress(Progress.Downloading(read, total))
                        }
                    }
                }
            }
            onProgress(Progress.Downloading(read, total))
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzip(archive: File, into: File) {
        into.mkdirs()
        val root = into.canonicalPath
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
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
    }
}
