package com.secretworld.auto.feature.voice

import com.secretworld.auto.domain.NarrationEntry
import com.secretworld.auto.domain.NarrationStore
import com.secretworld.auto.domain.VoiceLanguage
import java.io.File

/**
 * Narraciones sintetizadas, en disco.
 *
 * El "ultimo uso" es el `lastModified` del fichero. Podria llevarse un indice
 * aparte, pero seria un segundo estado que mantener sincronizado con el disco
 * para no ganar nada: el sistema de ficheros ya lleva esa cuenta.
 */
class DiskNarrationStore(root: File) : NarrationStore {

    // Con número: al cambiar cómo se sintetiza, lo guardado con la forma anterior
    // —frases ininteligibles en italiano— no se vuelve a reproducir.
    private val root = File(root, "narration-2").apply { mkdirs() }.also { File(root, "narration").deleteRecursively() }

    override fun has(placeId: String, language: VoiceLanguage): Boolean =
        file(placeId, language).let { it.isFile && it.length() > 0 }

    override fun pathFor(placeId: String, language: VoiceLanguage): String =
        file(placeId, language).also { it.parentFile?.mkdirs() }.absolutePath

    override fun entries(): List<NarrationEntry> =
        VoiceLanguage.entries.flatMap { language ->
            (dirFor(language).listFiles()?.asList() ?: emptyList())
                .filter { it.isFile && it.name.endsWith(SUFFIX) }
                .map {
                    NarrationEntry(
                        placeId = it.name.removeSuffix(SUFFIX),
                        language = language,
                        path = it.absolutePath,
                        bytes = it.length(),
                        lastUsedEpochMs = it.lastModified(),
                    )
                }
        }

    override fun remove(placeId: String, language: VoiceLanguage) {
        file(placeId, language).delete()
    }

    override fun touch(placeId: String, language: VoiceLanguage) {
        file(placeId, language).setLastModified(System.currentTimeMillis())
    }

    /** Vacia la cache entera. Para "olvidar lo escuchado" y para pruebas. */
    fun clear(): Boolean = root.deleteRecursively().also { root.mkdirs() }

    private fun dirFor(language: VoiceLanguage) = File(root, language.code)

    private fun file(placeId: String, language: VoiceLanguage) =
        // El id puede llevar dos puntos ("wiki:es:1488483") y MediaPlayer
        // tomaría el nombre por una URL con esquema. Solo letras, dígitos y guiones.
        File(dirFor(language), placeId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("") + SUFFIX)

    private companion object {
        const val SUFFIX = ".wav"
    }
}
