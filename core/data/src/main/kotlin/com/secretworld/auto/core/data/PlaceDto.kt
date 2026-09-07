package com.secretworld.auto.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que devuelve `POST /places/cards`.
 *
 * Es deliberadamente **un subconjunto**: la respuesta real trae más de treinta
 * campos —autor, likes, vistas, traducciones, medios— y el coche no necesita casi
 * ninguno. Con `ignoreUnknownKeys` activado, que el backend añada campos no rompe
 * nada aquí.
 */
@Serializable
data class PlacesResponse(
    val data: List<PlaceDto> = emptyList(),
)

@Serializable
data class PlaceDto(
    val oid: Long,
    val name: String = "",
    val description: String? = null,
    @SerialName("historical_descr") val historicalDescription: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("place_address") val address: PlaceAddressDto? = null,
    @SerialName("place_media") val media: List<PlaceMediaDto> = emptyList(),
)

@Serializable
data class PlaceMediaDto(val media: MediaDto? = null)

@Serializable
data class MediaDto(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("file_extension") val fileExtension: String? = null,
)

@Serializable
data class PlaceAddressDto(
    val lat: Double? = null,
    val lon: Double? = null,
    val city: NamedDto? = null,
    val nation: NamedDto? = null,
)

@Serializable
data class NamedDto(val name: String? = null)

/** Petición de lugares por posición y radio. */
@Serializable
data class PlacesRequest(
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 30,
    val lat: Double,
    val long: Double,
    @SerialName("min_range") val minRange: Int = 0,
    @SerialName("max_range") val maxRange: Int = 30,
    @SerialName("content_type") val contentType: Int = 1,
    val lang: String = "en",
)
