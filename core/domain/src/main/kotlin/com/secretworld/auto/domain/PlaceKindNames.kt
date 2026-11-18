package com.secretworld.auto.domain

/**
 * El tipo de un negocio dicho en el idioma de la app: "Restaurante", no "food"
 * ni "restaurant". Los buscadores lo dan en su propio vocabulario —OpenStreetMap
 * con sus etiquetas en inglés (`fast_food`, `fuel`), y lo que quedó en caché de
 * Mapbox con sus categorías ("food")—; aquí se traduce lo conocido.
 *
 * Lo que no se conoce se enseña tal cual, con mayúscula: mejor eso que nada.
 */
object PlaceKindNames {

    fun label(kind: String?, language: VoiceLanguage): String? {
        val raw = kind?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val key = raw.lowercase().replace('_', ' ')
        val names = NAMES[key] ?: return raw.replaceFirstChar { it.uppercase() }
        return language.say(en = names.first, es = names.second, it = names.third)
    }

    /** Inglés, español e italiano. */
    private val NAMES: Map<String, Triple<String, String, String>> = buildMap {
        fun put(keys: List<String>, en: String, es: String, ita: String) = keys.forEach { key -> put(key, Triple(en, es, ita)) }
        put(listOf("restaurant", "food", "food and drink", "seafood restaurant", "pizza restaurant"), "Restaurant", "Restaurante", "Ristorante")
        put(listOf("fast food"), "Fast food", "Comida rápida", "Fast food")
        put(listOf("cafe", "coffee", "coffee shop"), "Café", "Cafetería", "Caffè")
        put(listOf("bar", "pub", "nightlife"), "Bar", "Bar", "Bar")
        put(listOf("ice cream"), "Ice cream", "Heladería", "Gelateria")
        put(listOf("bakery"), "Bakery", "Panadería", "Panetteria")
        put(listOf("fuel", "gas station", "gas", "petrol station"), "Petrol station", "Gasolinera", "Distributore")
        put(listOf("parking", "parking lot", "parking entrance"), "Parking", "Aparcamiento", "Parcheggio")
        put(listOf("hotel", "lodging"), "Hotel", "Hotel", "Hotel")
        put(listOf("hostel"), "Hostel", "Hostal", "Ostello")
        put(listOf("guest house", "guesthouse"), "Guest house", "Casa de huéspedes", "Pensione")
        put(listOf("apartment"), "Apartment", "Apartamento", "Appartamento")
        put(listOf("pharmacy", "chemist"), "Pharmacy", "Farmacia", "Farmacia")
        put(listOf("hospital"), "Hospital", "Hospital", "Ospedale")
        put(listOf("clinic", "doctors"), "Clinic", "Clínica", "Clinica")
        put(listOf("atm"), "Cash machine", "Cajero", "Bancomat")
        put(listOf("bank"), "Bank", "Banco", "Banca")
        put(listOf("supermarket", "grocery"), "Supermarket", "Supermercado", "Supermercato")
        put(listOf("convenience", "convenience store"), "Shop", "Tienda", "Negozio")
        put(listOf("police", "police station"), "Police", "Policía", "Polizia")
        put(listOf("cinema"), "Cinema", "Cine", "Cinema")
        put(listOf("viewpoint"), "Viewpoint", "Mirador", "Belvedere")
        put(listOf("beach"), "Beach", "Playa", "Spiaggia")
        put(listOf("car repair", "auto repair"), "Car repair", "Taller", "Officina")
        put(listOf("museum"), "Museum", "Museo", "Museo")
        put(listOf("park"), "Park", "Parque", "Parco")
        put(listOf("arts centre", "arts center"), "Arts centre", "Centro cultural", "Centro culturale")
    }
}
