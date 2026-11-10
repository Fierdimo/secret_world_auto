package com.secretworld.auto.domain

/** Una narración ya sintetizada y guardada. */
data class NarrationEntry(
    val placeId: String,
    val language: VoiceLanguage,
    val path: String,
    val bytes: Long,
    val lastUsedEpochMs: Long,
)

/**
 * Donde viven las narraciones ya sintetizadas. Puerto: el dominio decide **qué**
 * guardar y **qué** tirar, no cómo se escribe en disco.
 */
interface NarrationStore {

    fun has(placeId: String, language: VoiceLanguage): Boolean

    /** Ruta donde iría —o donde está— la narración. No implica que exista. */
    fun pathFor(placeId: String, language: VoiceLanguage): String

    fun entries(): List<NarrationEntry>

    fun totalBytes(): Long = entries().sumOf { it.bytes }

    fun remove(placeId: String, language: VoiceLanguage)

    /** Marca como usada recientemente, para que la política LRU sea justa. */
    fun touch(placeId: String, language: VoiceLanguage)
}

/** De dónde sale el texto que se va a narrar. */
interface NarrationTextSource {
    fun textFor(place: Place, language: VoiceLanguage): String?

    /**
     * Si contar [place] en [language] exige traducirlo antes, lo que en el
     * dispositivo tarda. Se avisa y se pregunta en vez de dejar al conductor
     * esperando sin saber por qué.
     */
    fun needsTranslation(place: Place, language: VoiceLanguage): Boolean = false
}
