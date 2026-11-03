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
    }

    /** Lo que hay que hacer a continuación. */
    sealed interface Step {
        /** Preguntar lo que falta, en una sola frase, y escuchar. */
        data class Ask(val request: ItineraryRequest, val missing: Set<ItineraryRequest.Missing>) : Step
        /** Buscar candidatos y planificar; al terminar, [proposed]. */
        data class Plan(val request: ItineraryRequest) : Step
        /** Proponer de nuevo, sin replanificar: se quitó una parada. */
        data class Repropose(val proposal: ItineraryProposal) : Step
        /** El conductor dijo que sí: empezar. */
        data class Start(val proposal: ItineraryProposal) : Step
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
                if (command.isRefusal() || ItineraryRequests.refuses(heard)) return discard()
                if (command.isCommanding()) {
                    state = State.Idle
                    return null
                }
                if (command == VoiceCommand.Yes || ItineraryRequests.accepts(heard)) {
                    state = State.Idle
                    return Step.Start(proposal)
                }
                // "Quita el museo": fuera esa parada, sin replanificar el resto.
                if (ItineraryRequests.removes(heard)) {
                    VoiceGrammar.resolve(heard, proposal.stops)?.let { place ->
                        if (proposal.stops.size == 1) return discard()
                        val shorter = ItineraryDesigner.without(proposal, place)
                        state = State.Proposed(shorter)
                        return Step.Repropose(shorter)
                    }
                }
                val asked = (command as? VoiceCommand.PlanItinerary)?.request ?: ItineraryRequests.details(heard)
                val adjusted = proposal.request.plus(asked)
                if (adjusted == proposal.request) {
                    state = State.Idle
                    return null
                }
                state = State.Idle
                return Step.Plan(adjusted)
            }
        }
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

    private fun VoiceCommand.isRefusal(): Boolean =
        this == VoiceCommand.No || this == VoiceCommand.Stop || this == VoiceCommand.EndRoute
}
