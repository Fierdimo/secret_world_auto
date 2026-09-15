package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Un modelo de escucha o de lenguaje, y de donde se baja.
 *
 * Igual que las voces (SPEC RF-1.8.7): **nada de esto va en el APK de tienda**.
 * Se instala en la primera ejecucion, con `sha256` verificado antes de tocar
 * disco.
 *
 * @param sha256 `null` significa que el paquete **todavia no esta publicado** en
 *   el CDN y por tanto no se puede descargar: solo se admite cargado a mano en
 *   un dispositivo de pruebas ([ModelStore.sideloadDir]).
 */
data class ModelPack(
    val id: String,
    val url: String,
    val bytes: Long,
    val sha256: String?,
    /** Fichero que tiene que existir para dar el modelo por instalado. */
    val marker: String,
    val licence: String,
)

object ModelCatalog {

    /** Base del CDN. En desarrollo, la maquina anfitriona vista desde el emulador. */
    var baseUrl: String = "http://10.0.2.2:8765/models"

    /** Palabra de activacion: zipformer de gigaspeech, 3,3 M parametros. */
    val KWS_EN = ModelPack(
        id = "kws-en",
        url = "kws-en.zip",
        bytes = 4786047L,
        sha256 = "c1414e17d794902ca334d488097481c21af9e1879c962f0ce529b6efdab1f3d5",
        marker = "keywords.txt",
        licence = "Apache 2.0 (k2-fsa)",
    )

    /** Detector de voz: decide cuando el conductor ha terminado de hablar. */
    val VAD = ModelPack(
        id = "vad",
        url = "vad.zip",
        bytes = 508315L,
        sha256 = "a4cd5c3a5031177ff0beed25f3ef4b5a7970ddc182d104dd32b8a1b370e6efbb",
        marker = "silero_vad.onnx",
        licence = "MIT (Silero)",
    )

    /**
     * Reconocedor de ordenes: Whisper base multilingue, int8. Ingles e italiano.
     * Se probo tiny (60 MB) y entendia "watch the nerve" por "what's nearby".
     */
    val ASR = ModelPack(
        id = "asr-whisper-base",
        url = "asr-whisper-base.zip",
        bytes = 94365397L,
        sha256 = "4edc5a47cd506aca24e33e37c9bb1ec311a09a3dcce9c6383c4a38eec9f6746f",
        marker = "tokens.txt",
        licence = "MIT (OpenAI Whisper)",
    )

    /**
     * LLM de intenciones: Gemma 3 1B instruct, int4, en formato `.task` de
     * MediaPipe. El `.task` de dentro es byte a byte el oficial de
     * `litert-community` (sha256 e3d981c0…); ver tools/fetch-voice-models.sh.
     */
    val LLM = ModelPack(
        id = "llm-gemma3-1b",
        url = "llm-gemma3-1b.zip",
        bytes = 391640710L,
        sha256 = "e0ccd7250f6e8396d78ae1014a8c14c61f264c7698d509f49f0aaf6a0bf2e244",
        marker = "model.task",
        licence = "Gemma Terms of Use",
    )

    /** Lo que hace falta para escuchar sin tocar nada. El LLM es aparte. */
    val listening: List<ModelPack> = listOf(KWS_EN, VAD, ASR)

    fun urlFor(pack: ModelPack): String = "$baseUrl/${pack.url}"
}

/**
 * Donde viven los modelos en el dispositivo y como se instalan.
 *
 * Dos sitios, con prioridad:
 *
 * 1. `filesDir/models/<id>`: lo que instalo la app, verificado. **Es lo unico
 *    que existe en la build de tienda.**
 * 2. `externalFilesDir/models/<id>`: cargado a mano con `adb push` en un
 *    dispositivo de pruebas (tools/sideload-models.sh). Sin verificar, porque lo
 *    puso quien esta probando. SPEC RF-1.8.9: es comodidad de desarrollo, no
 *    comportamiento de producto.
 */
class ModelStore(private val context: Context) {

    private val packs = PackInstaller(context)

    fun installDir(pack: ModelPack): File = File(context.filesDir, "models/${pack.id}")

    fun sideloadDir(pack: ModelPack): File? =
        context.getExternalFilesDir("models")?.let { File(it, pack.id) }

    /** El directorio con el modelo listo para cargar, o `null` si no esta. */
    fun dirFor(pack: ModelPack): File? =
        listOfNotNull(installDir(pack), sideloadDir(pack)).firstOrNull { File(it, pack.marker).isFile }

    fun isAvailable(pack: ModelPack): Boolean = dirFor(pack) != null

    fun missing(packs: List<ModelPack>): List<ModelPack> = packs.filterNot(::isAvailable)

    /**
     * Descarga, verifica e instala. Bloquea: llamar desde un hilo de fondo.
     * Un paquete sin `sha256` no se descarga, se rechaza: no se instala nada
     * que no se pueda verificar.
     */
    fun install(pack: ModelPack, onProgress: (VoiceInstaller.Progress) -> Unit = {}): Result<File> {
        dirFor(pack)?.let {
            onProgress(VoiceInstaller.Progress.Done(it))
            return Result.success(it)
        }
        val sha = pack.sha256 ?: run {
            val reason = "el modelo ${pack.id} no esta publicado: cargar a mano en un dispositivo de pruebas"
            Log.w(TAG, reason)
            onProgress(VoiceInstaller.Progress.Failed(reason))
            return Result.failure(IllegalStateException(reason))
        }
        val bundled = "models/${pack.id}.zip".takeIf { packs.hasAsset(it) }
        return packs.install(
            url = ModelCatalog.urlFor(pack),
            bundledAsset = bundled,
            expectedBytes = pack.bytes,
            sha256 = sha,
            target = installDir(pack),
            workName = "model-${pack.id}",
            onProgress = onProgress,
        )
    }

    private companion object {
        const val TAG = "SWVoice"
    }
}
