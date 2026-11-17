package com.secretworld.auto.domain

/**
 * Las palabras con que se nombran los sitios de una ciudad, en los tres idiomas.
 *
 * Los nombres del catálogo vienen en el idioma de su fuente: "Castillo San
 * Felipe", "Convento de la Popa", "Palace of the Inquisition". Quien habla en
 * inglés dice "the castle" y quien habla en italiano "il castello", y los dos
 * se refieren al castillo. Sin esto no podían pedirlo con sus palabras.
 */
internal object Landmarks {

    /** ¿Nombran [a] y [b] la misma clase de sitio, en el idioma que sea? Sin acentos y en minúsculas. */
    fun same(a: String, b: String): Boolean = a != b && GROUPS.any { a in it && b in it }

    private val GROUPS: List<Set<String>> = listOf(
        setOf("castle", "castles", "castillo", "castillos", "castello", "castelli"),
        setOf("fort", "fortress", "fuerte", "fortaleza", "forte", "fortezza"),
        setOf("tower", "torre"),
        setOf("square", "plaza", "piazza", "plazoleta", "piazzetta"),
        setOf("palace", "palacio", "palazzo"),
        setOf("church", "iglesia", "chiesa"),
        setOf("cathedral", "catedral", "cattedrale", "duomo"),
        setOf("convent", "convento"),
        setOf("monastery", "monasterio", "monastero"),
        setOf("museum", "museo"),
        setOf("wall", "walls", "muralla", "murallas", "mura"),
        // Sin "porta": en italiano también es el verbo de "portami", y "porta mi
        // al castello" acababa en la Puerta del Reloj.
        setOf("gate", "puerta", "portone"),
        setOf("bridge", "puente", "ponte"),
        setOf("park", "parque", "parco"),
        setOf("garden", "gardens", "jardin", "jardines", "giardino", "giardini"),
        setOf("beach", "playa", "spiaggia"),
        setOf("market", "mercado", "mercato"),
        setOf("theatre", "theater", "teatro"),
        setOf("street", "calle", "via"),
        setOf("clock", "reloj", "orologio"),
        setOf("island", "isla", "isola"),
        setOf("lighthouse", "faro"),
        setOf("hill", "cerro", "colle", "collina"),
        setOf("viewpoint", "lookout", "mirador", "belvedere"),
        setOf("monument", "monumento"),
        setOf("statue", "estatua", "statua"),
    )
}
