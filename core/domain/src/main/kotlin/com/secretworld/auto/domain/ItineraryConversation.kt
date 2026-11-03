package com.secretworld.auto.domain

/**
 * La conversación para armar un itinerario (SPEC RF-8.3, RF-8.4): pedir,
 * preguntar lo que falta, proponer y confirmar, ajustar o descartar.
 *
 * Solo decide **qué toca hacer**; hacerlo —buscar candidatos, planificar,
 * hablar— es cosa de quien la usa. Así se prueba sin red ni voz.
 */
class ItineraryConversation {

    sealed interface State {
        data object Idle : State
        /** Se preguntó lo que faltaba y se espera la respuesta. */
        data class Asking(val request: ItineraryRequest) : State
        /** Se propuso un itinerario y se espera el sí, el no o un ajuste. */
        data class Proposed(val proposal: ItineraryProposal) : State
        /**
         * Se confirmó y quedó guardado; se preguntó si empezar ya. Confirmar no
         * es arrancar: se puede armar el itinerario en el hotel y salir después.
         */
        data class Saved(val proposal: ItineraryProposal) : State
    }

    /** Lo que hay que hacer a continuación. */
    sealed interface Step {
        /** Preguntar lo que falta, en una sola frase, y escuchar. */
        data class Ask(val request: ItineraryRequest, val missing: Set<ItineraryRequest.Missing>) : Step
        /** Buscar candidatos y planificar; al terminar, [proposed]. */
        data class Plan(val request: ItineraryRequest) : Step
        /** Proponer de nuevo, sin replanificar: se cambió [change], y basta con decir eso. */
        data class Repropose(val proposal: ItineraryProposal, val change: ItineraryEdit) : Step
        /** Dijo que sí a la propuesta: guardarla y preguntar si empezar ya. */
        data class Save(val proposal: ItineraryProposal) : Step
        /**
         * Dijo que empezar: arrancar el recorrido y abrir el navegador. Puede
         * llegar sin pasar por [Save] —"inicia el itinerario" a la propuesta—, así
         * que quien lo haga también lo guarda.
         */
        data class Start(val proposal: ItineraryProposal) : Step
        /** Guardado —también si aún no lo estaba—, pero ahora no: se queda para después. */
        data class Shelved(val proposal: ItineraryProposal) : Step
        data object Discarded : Step
    }

    var state: State = State.Idle
        private set

    val busy: Boolean get() = state != State.Idle

    /** Una petición nueva. Lo que falte y no sepa el perfil, se pregunta. */
    fun request(request: ItineraryRequest, profile: TravellerProfile): Step {
        val missing = request.missing(profile)
        if (missing.isEmpty()) {
            state = State.Idle
            return Step.Plan(request)
        }
        state = State.Asking(request)
        return Step.Ask(request, missing)
    }

    /** El planificador terminó y se va a proponer esto. */
    fun proposed(proposal: ItineraryProposal) {
        state = State.Proposed(proposal)
    }

    /** Se retoma un itinerario guardado: se pregunta si empezar. */
    fun resumed(proposal: ItineraryProposal) {
        state = State.Saved(proposal)
    }

    fun reset() {
        state = State.Idle
    }

    /**
     * Lo que se oye mientras hay algo pendiente. `null` si no va con el
     * itinerario: entonces se atiende como una orden cualquiera, y lo pendiente
     * se olvida, porque el conductor pasó a otra cosa.
     *
     * @param command lo que la gramática entendió de la misma frase. Una
     *   respuesta como "tres horas, me gustan los museos" puede parecerle una
     *   búsqueda de museos; aquí se lee primero como respuesta.
     */
    fun hear(heard: String, command: VoiceCommand, profile: TravellerProfile): Step? {
        when (val current = state) {
            State.Idle -> return null

            is State.Asking -> {
                if (command.isRefusal() || ItineraryRequests.refuses(heard)) return discard()
                // Ir a un sitio o cambiar de idioma mandan: "llévame al castillo" no
                // contesta qué le gusta.
                if (command.isCommanding()) {
                    state = State.Idle
                    return null
                }
                if (command is VoiceCommand.PlanItinerary) return request(current.request.plus(command.request), profile)
                // Lo demás, si contesta a lo preguntado, es respuesta: en "me gustan
                // los castillos" la gramática ve el Castillo de San Felipe, pero lo
                // que se dijo es qué le gusta.
                val merged = current.request.plus(ItineraryRequests.details(heard))
                if (merged == current.request) {
                    // No contestó a lo preguntado: lo que diga se atiende como orden.
                    state = State.Idle
                    return null
                }
                return request(merged, profile)
            }

            is State.Proposed -> {
                val proposal = current.proposal
                // Callar no es contestar: "para" corta lo que se esté diciendo y la
                // propuesta sigue ahí.
                if (command == VoiceCommand.Stop) return null
                // "Mejor guárdalo para luego" contesta; "mejor no", no.
                val reply = ItineraryRequests.reply(heard)
                if (reply == null && (command.isRefusal() || ItineraryRequests.refuses(heard))) return discard()
                // Cambiar el orden, la estancia o añadir un sitio: se rehace sin replanificar.
                // Va antes que la respuesta: "empieza por el castillo" no es empezar.
                ItineraryRequests.edit(heard, proposal.stops, command.namedPlace())?.let { return reproposed(proposal, it) }
                if (command.isCommanding()) {
                    state = State.Idle
                    return null
                }
                // "Cuéntame del castillo", siendo una parada: es querer oírlo, no
                // pedir más castillos.
                if (command == VoiceCommand.TellMeMore || (command is VoiceCommand.TellMeAbout && proposal.stops.any { it.id == command.place.id })) return null
                val answered = reply ?: when (command) {
                    VoiceCommand.StartItinerary -> ItineraryReply.START
                    VoiceCommand.Yes -> ItineraryReply.SAVE
                    else -> null
                }
                answered?.let { return answer(it) }
                // "Quita el museo": fuera esa parada, sin replanificar el resto.
                if (ItineraryRequests.removes(heard)) {
                    VoiceGrammar.resolve(heard, proposal.stops)?.let { place ->
                        if (proposal.stops.size == 1) return discard()
                        return reproposed(proposal, ItineraryEdit.Remove(place))
                    }
                }
                val asked = (command as? VoiceCommand.PlanItinerary)?.request ?: ItineraryRequests.details(heard)
                val adjusted = proposal.request.plus(asked)
                if (adjusted == proposal.request) {
                    // Que le cuenten un sitio, o algo que no se entendió —ruido, otra
                    // conversación en el coche—, no es pasar a otra cosa: la
                    // propuesta sigue esperando.
                    if (!command.keepsPending()) state = State.Idle
                    return null
                }
                state = State.Idle
                return Step.Plan(adjusted)
            }

            is State.Saved -> {
                val proposal = current.proposal
                if (command == VoiceCommand.Stop || command.isAside()) return null
                val reply = ItineraryRequests.reply(heard)
                // Un cambio a lo guardado vuelve a ser una propuesta que confirmar.
                ItineraryRequests.edit(heard, proposal.stops, command.namedPlace())?.let { return reproposed(proposal, it) }
                if (command == VoiceCommand.Yes || command == VoiceCommand.StartItinerary) return answer(ItineraryReply.START)
                reply?.let { return answer(it) }
                // Que no empiece ahora no es tirarlo: queda guardado.
                if (command.isRefusal() || ItineraryRequests.refuses(heard)) return answer(ItineraryReply.LATER)
                if (!command.keepsPending()) state = State.Idle
                return null
            }
        }
    }

    /**
     * Una respuesta a lo pendiente, se haya entendido con la gramática o con el
     * modelo. `null` si no hay nada pendiente que contestar.
     *
     * Ya guardado, "guárdalo" es dejarlo para después; y aprobarlo otra vez
     * ("sí", "me gusta") contesta al "¿empezamos?".
     */
    fun answer(reply: ItineraryReply): Step? {
        val proposal = when (val current = state) {
            is State.Proposed -> current.proposal
            is State.Saved -> current.proposal
            else -> return null
        }
        val saved = state is State.Saved
        return when (reply) {
            ItineraryReply.START -> Step.Start(proposal).also { state = State.Idle }
            ItineraryReply.LATER -> Step.Shelved(proposal).also { state = State.Idle }
            ItineraryReply.SAVE -> if (saved) {
                Step.Start(proposal).also { state = State.Idle }
            } else {
                Step.Save(proposal).also { state = State.Saved(proposal) }
            }
        }
    }

    /**
     * Para contestar con el modelo lo que la gramática no entendió: lo
     * pendiente vuelve a estar pendiente aunque [hear] lo diera por olvidado.
     */
    fun restore(pending: State) {
        state = pending
    }

    private fun reproposed(proposal: ItineraryProposal, edit: ItineraryEdit): Step {
        val changed = applied(proposal, edit)
        state = State.Proposed(changed)
        return Step.Repropose(changed, edit)
    }

    private fun applied(proposal: ItineraryProposal, edit: ItineraryEdit): ItineraryProposal = when (edit) {
        is ItineraryEdit.Move -> ItineraryDesigner.moved(proposal, edit.place, if (edit.first) 0 else proposal.stops.lastIndex)
        is ItineraryEdit.Relative -> {
            val without = proposal.stops - edit.place
            val anchorAt = without.indexOf(edit.anchor)
            ItineraryDesigner.moved(proposal, edit.place, if (edit.before) anchorAt else anchorAt + 1)
        }
        is ItineraryEdit.Add -> ItineraryDesigner.added(proposal, edit.place)
        is ItineraryEdit.Stay -> ItineraryDesigner.restayed(proposal, edit.place, if (edit.longer) LONGER_FACTOR else SHORTER_FACTOR)
        is ItineraryEdit.Remove -> ItineraryDesigner.without(proposal, edit.place)
    }

    /** El lugar que la gramática encontró en la frase, si encontró uno. */
    private fun VoiceCommand.namedPlace(): Place? = when (this) {
        is VoiceCommand.TellMeAbout -> place
        is VoiceCommand.ChooseAction -> place
        is VoiceCommand.TakeMeThere -> place
        is VoiceCommand.WhichOne -> places.firstOrNull()
        else -> null
    }

    private fun discard(): Step {
        state = State.Idle
        return Step.Discarded
    }

    /**
     * Órdenes que mandan aunque la frase nombre un interés: se atienden como
     * tales y lo pendiente se olvida. El resto —contar un sitio nombrado, una
     * búsqueda— solo si la frase no contesta ni ajusta nada.
     */
    private fun VoiceCommand.isCommanding(): Boolean =
        this is VoiceCommand.TakeMeThere || this is VoiceCommand.NavigateToNamed || this == VoiceCommand.WhatIsNearby ||
            this is VoiceCommand.SwitchLanguage || this == VoiceCommand.ForgetProfile

    private companion object {
        const val LONGER_FACTOR = 1.5
        const val SHORTER_FACTOR = 0.6
    }

    /**
     * Lo que no aparta del itinerario a la vista: un inciso, callar, o lo que no
     * se entendió. Cualquier otra orden sí: el conductor pasó a otra cosa.
     */
    private fun VoiceCommand.keepsPending(): Boolean =
        isAside() || this == VoiceCommand.Stop || this is VoiceCommand.NotUnderstood

    /** Contar un sitio, o más de él: un inciso que no cambia lo pendiente. */
    private fun VoiceCommand.isAside(): Boolean = this is VoiceCommand.TellMeAbout || this == VoiceCommand.TellMeMore

    private fun VoiceCommand.isRefusal(): Boolean =
        this == VoiceCommand.No || this == VoiceCommand.Stop || this == VoiceCommand.EndRoute
}
