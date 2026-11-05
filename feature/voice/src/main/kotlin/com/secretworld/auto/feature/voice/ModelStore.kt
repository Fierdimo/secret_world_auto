package com.secretworld.auto.feature.voice

import android.content.Context
import android.util.Log
import com.secretworld.auto.domain.VoiceLanguage
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
     * Reconocedor de ordenes: FastConformer de NVIDIA, en/es/fr/de, int8. Se
     * probaron Whisper tiny y base: tiny oia "watch the nerve" por "what's
     * nearby", base destrozaba el espanol. Este es 6x mas rapido y entiende los dos.
     */
    val ASR = ModelPack(
        id = "asr-nemo-multi",
        url = "asr-nemo-multi.zip",
        bytes = 103517461L,
        sha256 = "2afeacfd2c147a019586cd46b7e09c54cc2a6a73499a4987792215de30d898b1",
        marker = "tokens.txt",
        licence = "CC-BY-4.0 (NVIDIA NeMo)",
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

    /**
     * El mismo FastConformer en diez idiomas (be, de, en, es, fr, hr, it, pl,
     * ru, uk). Es el unico de la familia que entiende italiano: misma
     * arquitectura y configuracion que [ASR]. Lo empaqueta la demo
     * (tools/fetch-demo-assets.sh); todavia no esta en el CDN.
     */
    val ASR_EU = ModelPack(
        id = "asr-nemo-eu",
        url = "asr-nemo-eu.zip",
        bytes = 102883173L,
        sha256 = "d2d1cd3889f666ff535b27b45f6f30dabb2070ffcb638aa36cf06c5db486c9e3",
        marker = "tokens.txt",
        licence = "CC-BY-4.0 (NVIDIA NeMo)",
    )

    /**
     * Reductor de ruido GTCRN: limpia cada frase antes del reconocedor. Es
     * **opcional**: si está se usa, si no todo sigue igual. Lo empaqueta la demo.
     */
    val DENOISER = ModelPack(
        id = "denoiser-gtcrn",
        url = "denoiser-gtcrn.zip",
        bytes = 127723L,
        sha256 = "689c2c1424a0026d4dd1551c8b87a66a1cce1969812195fd3e16da255d974dcd",
        marker = "gtcrn_simple.onnx",
        licence = "MIT (GTCRN)",
    )

    /**
     * El reconocedor que conviene para el idioma, sin mirar qué hay instalado:
     * el de diez idiomas solo para italiano; para lo demás el de cuatro, que no
     * escribe en cirílico.
     */
    fun asrFor(language: VoiceLanguage): ModelPack =
        if (language == VoiceLanguage.ITALIAN) ASR_EU else ASR

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
    fun install(pack: ModelPack, onProgress: (VoiceInstaller.Progress) -> Unit = {}, onFraction: (Float) -> Unit = {}): Result<File> {
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
        val bundled = bundledName(pack).takeIf { packs.hasAsset(it) }
        return packs.install(
            url = ModelCatalog.urlFor(pack),
            bundledAsset = bundled,
            expectedBytes = pack.bytes,
            sha256 = sha,
            target = installDir(pack),
            workName = "model-${pack.id}",
            onProgress = onProgress,
            onFraction = onFraction,
        )
    }

    /**
     * El reconocedor para el idioma, entre los que hay —instalados o dentro del
     * APK—. El preferido es el de [ModelCatalog.asrFor]; si no está, el otro,
     * que también entiende inglés y español: mejor oír con el segundo que no oír.
     *
     * Por qué dos: el de diez idiomas es el único con italiano, pero también
     * sabe ruso, ucraniano y bielorruso, y con ruido escribe a veces en
     * cirílico («Секрет», «Игры в каркадины»). El de cuatro no tiene cirílico.
     */
    fun asrFor(language: VoiceLanguage): ModelPack {
        val preferred = ModelCatalog.asrFor(language)
        val other = if (preferred == ModelCatalog.ASR) ModelCatalog.ASR_EU else ModelCatalog.ASR
        fun present(pack: ModelPack) = isAvailable(pack) || isBundled(pack)
        return when {
            present(preferred) -> preferred
            // El de cuatro no sabe italiano: para italiano no sirve de reserva.
            language != VoiceLanguage.ITALIAN && present(other) -> other
            else -> preferred
        }
    }

    /** Lo que hace falta para escuchar sin tocar nada. El LLM es aparte. */
    fun listening(language: VoiceLanguage): List<ModelPack> = listOf(ModelCatalog.KWS_EN, ModelCatalog.VAD, asrFor(language))

    /** Si el APK trae este paquete dentro. */
    fun isBundled(pack: ModelPack): Boolean = packs.hasAsset(bundledName(pack))

    /**
     * Instala desde el APK lo que venga empaquetado y falte. Es lo que hace que
     * la demo funcione sola, sin adb ni CDN. Bloquea: llamar desde un hilo de
     * fondo. Informa del avance total, por bytes, de 0 a 100.
     */
    fun installBundled(packs: List<ModelPack>, onPack: (ModelPack) -> Unit = {}, onPercent: (Int) -> Unit = {}): List<ModelPack> {
        val pending = packs.filter { !isAvailable(it) && isBundled(it) }
        val total = pending.sumOf { it.bytes }.coerceAtLeast(1)
        var done = 0L
        val failed = pending.filter { pack ->
            onPack(pack)
            onPercent((done * 100 / total).toInt())
            // Dentro de cada paquete también avanza: uno de cien megas dejaba la
            // barra quieta medio minuto.
            var last = -1
            val ok = install(pack, onFraction = { f ->
                val now = ((done + (pack.bytes * f).toLong()) * 100 / total).toInt()
                if (now != last) {
                    last = now
                    onPercent(now)
                }
            }).isSuccess
            done += pack.bytes
            !ok
        }
        onPercent(100)
        return failed
    }

    private fun bundledName(pack: ModelPack) = "models/${pack.id}.zip"

    private companion object {
        const val TAG = "SWVoice"
    }
}
