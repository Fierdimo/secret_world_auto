package com.secretworld.auto.domain

/** Un punto sobre la Tierra. Sin dependencias de plataforma: esto es dominio puro. */
data class Coordinates(val latitude: Double, val longitude: Double) {

    /** Distancia en metros por la fórmula del haversine. */
    fun distanceTo(other: Coordinates): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    /** Rumbo inicial hacia otro punto, en grados [0, 360). */
    fun bearingTo(other: Coordinates): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0
    }
}

/**
 * Idioma de voz. La v1 sintetiza inglés e italiano (SPEC RF-1.7); el español
 * entró para poder probar en Colombia, donde está el banco de pruebas.
 */
enum class VoiceLanguage(val code: String) {
    ENGLISH("en"),
    ITALIAN("it"),
    SPANISH("es");

    companion object {
        fun fromTag(tag: String?): VoiceLanguage? =
            entries.firstOrNull { tag != null && tag.lowercase().startsWith(it.code) }
    }
}

/**
 * Un lugar narrable. [narrationSeconds] es la duración estimada del segmento;
 * el producto trabaja con 60 a 90 segundos (SPEC RF-1.5).
 */
data class Place(
    val id: String,
    val name: String,
    val position: Coordinates,
    val narrationSeconds: Int,
    val languages: Set<VoiceLanguage>,
    /** Ciudad, para situar al usuario sin abrir nada. */
    val city: String? = null,
    /** Foto de portada. En una pantalla de coche la imagen hace la mitad del trabajo. */
    val imageUrl: String? = null,
    /** Texto de la ficha. La narración se deriva de aquí. */
    val summary: String? = null,
    /**
     * Idioma en que está escrito [summary], si se sabe. Cuando no coincide con
     * el de la voz, hay que traducir antes de narrar.
     */
    val contentLanguage: VoiceLanguage? = null,
) {
    fun hasNarrationIn(language: VoiceLanguage): Boolean = language in languages
}

/** Estado del vehículo, tal y como lo necesita el dominio. */
data class VehicleState(
    val position: Coordinates,
    /** Rumbo en grados [0, 360). Null si el vehículo está parado y no hay rumbo fiable. */
    val headingDegrees: Double?,
    val speedMetersPerSecond: Double,
    val parked: Boolean,
) {
    val drivingMode: DrivingMode
        get() = if (parked) DrivingMode.PARKED else DrivingMode.DRIVING
}

enum class DrivingMode { PARKED, DRIVING }
