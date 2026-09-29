package com.secretworld.auto.feature.voice

import android.content.Context
import com.secretworld.auto.domain.VoiceLanguage
import java.io.File

/**
 * Instala voces en el dispositivo.
 *
 * El flujo de producto es: al arrancar por primera vez se instala el idioma que
 * el usuario elige; los demas se instalan cuando los pide. Hasta que la voz esta
 * instalada la app **no puede narrar, y lo dice** — degradar en silencio seria
 * peor que no funcionar (misma regla que RF-2.6).
 *
 * La mecanica de descarga, verificacion e instalacion atomica vive en
 * [PackInstaller], compartida con los modelos de escucha ([ModelStore]).
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

    private val packs = PackInstaller(context)

    fun isInstalled(language: VoiceLanguage): Boolean {
        val dir = installDirFor(language)
        val model = VoiceCatalog.packFor(language)?.model
        if (model != null) return File(dir, model).isFile
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

        // Si la voz viene empaquetada en la app, se instala desde ahi: ni red
        // ni espera. Lo usa el sabor de banco de pruebas.
        val bundled = "voices/${language.code}.zip".takeIf { packs.hasAsset(it) }

        return packs.install(
            url = VoiceCatalog.urlFor(pack),
            bundledAsset = bundled,
            expectedBytes = pack.bytes,
            sha256 = pack.sha256,
            target = target,
            workName = "voice-${language.code}",
            onProgress = onProgress,
        )
    }
}
