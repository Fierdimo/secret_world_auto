package com.secretworld.auto.feature.voice

import com.secretworld.auto.domain.VoiceLanguage

/**
 * Que voces existen y de donde se bajan.
 *
 * Las voces **no van dentro del APK**: con las dos empaquetadas pesaba 169 MB, y
 * sin ellas menos de 4 (ver spikes/voice/RESULTS.md). Se instalan bajo demanda.
 *
 * `sha256` no es decoracion: se descarga por una conexion movil de vehiculo, que
 * se corta, y un modelo a medias que se cargue igual es un fallo dificil de
 * diagnosticar. Se verifica antes de instalar.
 */
data class VoicePack(
    val language: VoiceLanguage,
    val url: String,
    val bytes: Long,
    val sha256: String,
    /** Licencia del dataset de origen. Ver spikes/voice/RESULTS.md. */
    val licence: String,
    /**
     * El modelo que tiene que haber instalado. Sin él, una voz distinta del
     * mismo idioma —otra que se instaló antes— contaría como esta, y cambiar
     * de voz no tendría efecto.
     */
    val model: String? = null,
)

object VoiceCatalog {

    /**
     * Base del CDN. En la prueba apunta al host de desarrollo: 10.0.2.2 es la
     * maquina anfitriona vista desde el emulador de Android.
     */
    var baseUrl: String = "http://10.0.2.2:8765"

    private val packs = listOf(
        VoicePack(
            language = VoiceLanguage.ENGLISH,
            url = "en.zip",
            bytes = 22546958L,
            sha256 = "f1208531b2092e078d29a0d1293a0e2943ff8ba3df5514f7252b0331df81129a",
            licence = "LJ Speech, dominio publico",
        ),
        VoicePack(
            language = VoiceLanguage.SPANISH,
            url = "es.zip",
            bytes = 14931149L,
            sha256 = "33284b4066a4b84cfda45bbbfd7b79f720f867f6528ef5859a2abe649604199f",
            // x_low a propósito: es la más rápida de sintetizar. Se probó ald
            // (es_MX, medium) y sonaba con una prosodia rara; aquí manda la
            // velocidad de respuesta sobre la calidad de la voz.
            licence = "carlfm (es_ES, x_low), dominio público",
        ),
        VoicePack(
            language = VoiceLanguage.ITALIAN,
            url = "it.zip",
            // riccardo (x_low) y no paola (medium): en un móvil lento paola
            // tardaba en sintetizar más de lo que dura la frase. Además paola
            // hereda el problema de licencia de lessac; riccardo no.
            bytes = 14929848L,
            sha256 = "0b915ff6d056e4cf497837e8185dc60f9dc645c6b213e6efc7ec5f6c8e8370c4",
            licence = "M-AILABS (riccardo, x_low), por confirmar",
            model = "it_IT-riccardo-x_low.onnx",
        ),
    )

    fun packFor(language: VoiceLanguage): VoicePack? = packs.firstOrNull { it.language == language }

    fun urlFor(pack: VoicePack): String = "$baseUrl/${pack.url}"

    fun all(): List<VoicePack> = packs
}
