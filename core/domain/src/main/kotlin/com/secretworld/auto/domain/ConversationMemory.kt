package com.secretworld.auto.domain

/**
 * Lo que el asistente recuerda de la conversación.
 *
 * Sin esto cada frase empieza de cero y el asistente parece sordo a lo que
 * acaba de decir él mismo: enumera tres sitios y no sabe qué es "el segundo";
 * narra un lugar y "cuéntame más" no sabe de qué. Es memoria **corta y de
 * trabajo**, no historial: unas pocas frases, el lugar del que se habla y lo
 * último que se enumeró. Vive en el dominio para que la gramática y el modelo
 * la usen igual.
 */
class ConversationMemory(private val maxTurns: Int = 6) {

    /** Un intercambio: lo que se oyó y lo que se contestó. */
    data class Turn(val heard: String, val reply: String)

    private val _turns = ArrayDeque<Turn>()
    val turns: List<Turn> get() = _turns.toList()

    /** El lugar del que se está hablando: lo último narrado o elegido. */
    var focus: Place? = null
        private set

    /** Lo último que se enumeró en voz alta, en el mismo orden en que se dijo. */
    var listed: List<Place> = emptyList()
        private set

    /** Lugares ya narrados en esta sesión, para no repetirse sin que se pida. */
    private val _heardAbout = LinkedHashSet<String>()
    val heardAbout: Set<String> get() = _heardAbout

    fun remember(heard: String, reply: String) {
        _turns.addLast(Turn(heard, reply))
        while (_turns.size > maxTurns) _turns.removeFirst()
    }

    fun focusOn(place: Place) {
        focus = place
        _heardAbout += place.id
    }

    /**
     * Si lo último enumerado fue para **elegir** —"¿cuál de estos?"— y no para
     * contar. Cambia qué significa "el segundo": elegir ese sitio y preguntar
     * qué hacer, en vez de narrarlo.
     */
    var choosing: Boolean = false
        private set

    fun listed(places: List<Place>, choosing: Boolean = false) {
        listed = places
        this.choosing = choosing
    }

    fun forget() {
        _turns.clear()
        focus = null
        listed = emptyList()
        choosing = false
        _heardAbout.clear()
    }

    /**
     * La conversación reciente como texto, para el modelo. Corta: cada línea
     * son tokens que el modelo tiene que leer antes de contestar.
     */
    fun transcript(): String = buildString {
        if (_turns.isEmpty()) return ""
        appendLine("Recent exchange, oldest first:")
        _turns.forEach { turn ->
            appendLine("Driver: \"${turn.heard.take(80)}\"")
            appendLine("Assistant: \"${turn.reply.take(80)}\"")
        }
    }
}
