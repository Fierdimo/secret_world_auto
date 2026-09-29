package com.secretworld.auto.domain

/**
 * Cuál de lo encontrado es el sitio que se pidió por su nombre.
 *
 * Los buscadores devuelven lo que se parece, y lo primero no siempre es lo
 * pedido. Medido en Cartagena: "Castillo San Felipe" daba primero el
 * **Parqueadero** Castillo San Felipe; "Centro Comercial Caribe Plaza", una
 * tienda **Efecty** de dentro. Se elige el que contiene todo lo pedido y
 * menos añade, y lo que añade pesa más si es otro tipo de sitio.
 */
object NameMatcher {

    /** Contiene todo lo pedido, aunque sea con una letra de diferencia. */
    fun matches(asked: String, found: String): Boolean {
        val want = core(asked)
        val have = core(found)
        if (want.isEmpty() || have.isEmpty()) return false
        return want.all { word -> have.any { same(it, word) } }
    }

    /** El mejor de [candidates] para lo pedido, o `null` si ninguno lo contiene. */
    fun best(asked: String, candidates: List<Place>): Place? {
        val want = core(asked)
        return candidates
            .filter { matches(asked, it.name) }
            .minByOrNull { place ->
                core(place.name).filter { word -> want.none { same(word, it) } }
                    .sumOf { extra -> if (VoiceGrammar.isCategoryWord(extra)) OTHER_KIND_COST else 1 }
            }
    }

    private fun same(a: String, b: String) = a == b || (b.length >= 5 && Script.editDistance(a, b) <= 1)

    private fun core(text: String): List<String> =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.length > 1 && it !in NOISE }

    /** Una palabra de otro tipo de sitio —"parqueadero", "cajero"— cuenta como tres. */
    private const val OTHER_KIND_COST = 3

    private val NOISE = setOf(
        "el", "la", "los", "las", "de", "del", "il", "lo", "di", "the", "of", "and", "y", "e", "by", "para", "per", "al", "a", "all", "dell", "nell", "sull",
        "hotel", "restaurante", "restaurant", "ristorante", "bar", "cafe", "calle", "carrera", "via",
    )
}
