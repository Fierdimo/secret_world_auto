package com.secretworld.auto.domain

/**
 * Sintetiza **por delante** de la narración (SPEC RF-1.2).
 *
 * El motor embebido sintetiza a un factor de tiempo real de ~0,22: un minuto de
 * audio cuesta unos trece segundos de CPU. Eso es rápido, pero no instantáneo, y
 * RNF-1 pide audio en menos de tres segundos desde frío. La salida es no esperar:
 * como [TourQueue] ya sabe qué lugar viene después, se sintetiza con antelación a
 * disco y en el momento de hablar solo hay que abrir un fichero.
 *
 * La otra mitad del trabajo es no llenar el disco del vehículo: se respeta un
 * presupuesto y se tira lo más viejo, **nunca lo que está a punto de sonar**.
 */
class NarrationPrefetcher(
    private val store: NarrationStore,
    private val synthesizer: SpeechSynthesizer,
    private val textSource: NarrationTextSource,
    /** Presupuesto de disco para narraciones cacheadas. */
    private val budgetBytes: Long,
    /** Cuántos lugares por delante se preparan. */
    private val lookahead: Int = 3,
) {

    data class Report(
        val synthesized: List<String> = emptyList(),
        val alreadyCached: List<String> = emptyList(),
        val skippedNoText: List<String> = emptyList(),
        val evicted: List<String> = emptyList(),
        val failed: Map<String, String> = emptyMap(),
    )

    /**
     * Prepara los primeros [lookahead] lugares de [queue]. Bloquea mientras
     * sintetiza: llamar desde un hilo de fondo.
     */
    fun prefetch(queue: List<Place>, language: VoiceLanguage): Report {
        val targets = queue.take(lookahead)
        if (targets.isEmpty()) return Report()

        val synthesized = mutableListOf<String>()
        val cached = mutableListOf<String>()
        val noText = mutableListOf<String>()
        val evicted = mutableListOf<String>()
        val failed = mutableMapOf<String, String>()

        // Lo que está en cola es intocable: tirarlo sería tirar justo lo que hace
        // falta dentro de un minuto.
        val protectedIds = targets.map { it.id }.toSet()

        for (place in targets) {
            if (store.has(place.id, language)) {
                store.touch(place.id, language)
                cached += place.id
                continue
            }
            if (!place.hasNarrationIn(language)) {
                noText += place.id
                continue
            }
            val text = textSource.textFor(place, language)
            if (text.isNullOrBlank()) {
                noText += place.id
                continue
            }

            evicted += makeRoom(protectedIds)

            val path = store.pathFor(place.id, language)
            runCatching { synthesizer.synthesizeToFile(text, language, path) }
                .onSuccess { synthesized += place.id }
                .onFailure { failed[place.id] = it.message ?: it.toString() }
        }

        return Report(synthesized, cached, noText, evicted, failed)
    }

    /**
     * Hace sitio antes de escribir, tirando lo menos usado. No toca nada que esté
     * en la cola actual, aunque eso signifique quedarse por encima del
     * presupuesto: es preferible pasarse de disco que cortar la narración.
     */
    private fun makeRoom(protectedIds: Set<String>): List<String> {
        val evicted = mutableListOf<String>()
        var total = store.totalBytes()
        if (total <= budgetBytes) return evicted

        val candidates = store.entries()
            .filterNot { it.placeId in protectedIds }
            .sortedBy { it.lastUsedEpochMs }

        for (entry in candidates) {
            if (total <= budgetBytes) break
            store.remove(entry.placeId, entry.language)
            total -= entry.bytes
            evicted += entry.placeId
        }
        return evicted
    }
}
