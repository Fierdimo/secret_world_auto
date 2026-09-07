package com.secretworld.auto.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NarrationPrefetcherTest {

    // ---------------------------------------------------------------- dobles

    private class FakeStore(private val bytesPerEntry: Long = 3_000_000L) : NarrationStore {
        val data = linkedMapOf<String, NarrationEntry>()
        var clock = 0L

        private fun key(id: String, lang: VoiceLanguage) = "$id|${lang.code}"

        override fun has(placeId: String, language: VoiceLanguage) =
            key(placeId, language) in data

        override fun pathFor(placeId: String, language: VoiceLanguage) =
            "/cache/${language.code}/$placeId.wav"

        override fun entries() = data.values.toList()

        override fun remove(placeId: String, language: VoiceLanguage) {
            data.remove(key(placeId, language))
        }

        override fun touch(placeId: String, language: VoiceLanguage) {
            val k = key(placeId, language)
            data[k]?.let { data[k] = it.copy(lastUsedEpochMs = ++clock) }
        }

        /** Simula lo que hace el sintetizador al escribir el fichero. */
        fun record(placeId: String, language: VoiceLanguage) {
            data[key(placeId, language)] = NarrationEntry(
                placeId, language, pathFor(placeId, language), bytesPerEntry, ++clock,
            )
        }
    }

    private class FakeSynthesizer(
        private val store: FakeStore,
        private val failOn: Set<String> = emptySet(),
    ) : SpeechSynthesizer {
        val calls = mutableListOf<String>()
        override val availableLanguages = VoiceLanguage.entries.toSet()

        override fun synthesizeToFile(text: String, language: VoiceLanguage, outputPath: String): Synthesis {
            val placeId = outputPath.substringAfterLast('/').removeSuffix(".wav")
            calls += placeId
            if (placeId in failOn) error("motor roto para $placeId")
            store.record(placeId, language)
            return Synthesis(outputPath, audioSeconds = 70.0, elapsedSeconds = 15.0, sampleRate = 22050)
        }
    }

    private class FakeText(private val missing: Set<String> = emptySet()) : NarrationTextSource {
        override fun textFor(place: Place, language: VoiceLanguage): String? =
            if (place.id in missing) null else "texto de ${place.id} en ${language.code}"
    }

    private fun place(id: String, languages: Set<VoiceLanguage> = VoiceLanguage.entries.toSet()) =
        Place(id, id, Coordinates(41.9, 12.5), 75, languages)

    // ----------------------------------------------------------------- tests

    @Test
    fun `solo prepara los siguientes lugares, no toda la cola`() {
        val store = FakeStore()
        val synth = FakeSynthesizer(store)
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), budgetBytes = Long.MAX_VALUE, lookahead = 2)

        val report = prefetcher.prefetch(
            listOf(place("a"), place("b"), place("c"), place("d")), VoiceLanguage.ENGLISH,
        )

        assertEquals(listOf("a", "b"), synth.calls)
        assertEquals(listOf("a", "b"), report.synthesized)
    }

    @Test
    fun `no vuelve a sintetizar lo que ya esta en cache`() {
        val store = FakeStore()
        store.record("a", VoiceLanguage.ENGLISH)
        val synth = FakeSynthesizer(store)
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), Long.MAX_VALUE, lookahead = 2)

        val report = prefetcher.prefetch(listOf(place("a"), place("b")), VoiceLanguage.ENGLISH)

        assertEquals(listOf("b"), synth.calls, "solo lo que falta")
        assertEquals(listOf("a"), report.alreadyCached)
    }

    @Test
    fun `salta lugares sin texto en el idioma`() {
        val store = FakeStore()
        val synth = FakeSynthesizer(store)
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(missing = setOf("b")), Long.MAX_VALUE)

        val report = prefetcher.prefetch(listOf(place("a"), place("b"), place("c")), VoiceLanguage.ITALIAN)

        assertEquals(listOf("a", "c"), synth.calls)
        assertEquals(listOf("b"), report.skippedNoText)
    }

    @Test
    fun `salta lugares que no declaran ese idioma`() {
        val store = FakeStore()
        val synth = FakeSynthesizer(store)
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), Long.MAX_VALUE)

        val soloIngles = place("b", languages = setOf(VoiceLanguage.ENGLISH))
        val report = prefetcher.prefetch(listOf(place("a"), soloIngles), VoiceLanguage.ITALIAN)

        assertEquals(listOf("a"), synth.calls)
        assertEquals(listOf("b"), report.skippedNoText)
    }

    @Test
    fun `respeta el presupuesto tirando lo menos usado`() {
        val store = FakeStore(bytesPerEntry = 1_000_000L)
        // Tres viejas en cache, presupuesto para dos y media.
        store.record("vieja1", VoiceLanguage.ENGLISH)
        store.record("vieja2", VoiceLanguage.ENGLISH)
        store.record("vieja3", VoiceLanguage.ENGLISH)
        val synth = FakeSynthesizer(store)
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), budgetBytes = 2_500_000L, lookahead = 1)

        val report = prefetcher.prefetch(listOf(place("nueva")), VoiceLanguage.ENGLISH)

        assertEquals(listOf("vieja1"), report.evicted, "se tira la mas antigua")
        assertTrue(store.has("nueva", VoiceLanguage.ENGLISH))
        assertTrue(store.has("vieja3", VoiceLanguage.ENGLISH), "la mas reciente sobrevive")
    }

    @Test
    fun `nunca tira lo que esta en la cola actual`() {
        val store = FakeStore(bytesPerEntry = 1_000_000L)
        store.record("a", VoiceLanguage.ENGLISH)
        store.record("b", VoiceLanguage.ENGLISH)
        val synth = FakeSynthesizer(store)
        // Presupuesto ridiculo: la unica forma de cumplirlo seria tirar a y b.
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), budgetBytes = 1L, lookahead = 3)

        val report = prefetcher.prefetch(
            listOf(place("a"), place("b"), place("c")), VoiceLanguage.ENGLISH,
        )

        assertTrue(report.evicted.isEmpty(), "prefiere pasarse de disco a cortar la narracion")
        assertTrue(store.has("a", VoiceLanguage.ENGLISH))
        assertTrue(store.has("b", VoiceLanguage.ENGLISH))
    }

    @Test
    fun `un fallo del motor no arrastra al resto de la cola`() {
        val store = FakeStore()
        val synth = FakeSynthesizer(store, failOn = setOf("b"))
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), Long.MAX_VALUE, lookahead = 3)

        val report = prefetcher.prefetch(
            listOf(place("a"), place("b"), place("c")), VoiceLanguage.ENGLISH,
        )

        assertEquals(listOf("a", "c"), report.synthesized)
        assertTrue("b" in report.failed)
    }

    @Test
    fun `con la cola vacia no hace nada`() {
        val store = FakeStore()
        val synth = FakeSynthesizer(store)
        val prefetcher = NarrationPrefetcher(store, synth, FakeText(), Long.MAX_VALUE)

        val report = prefetcher.prefetch(emptyList(), VoiceLanguage.ENGLISH)

        assertTrue(synth.calls.isEmpty())
        assertEquals(NarrationPrefetcher.Report(), report)
    }
}
