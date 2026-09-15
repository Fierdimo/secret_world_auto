package com.secretworld.auto.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Lo que se le pide a Mapbox por HTTP: rutas.
 *
 * Es el **puente** del banco de pruebas (docs/MAP-OPTIONS.md): en el vehículo,
 * rutas y destino irán por el navegador de Pleos cuando publique su librería.
 * Por eso esto habla por REST y no por su SDK de navegación: dos endpoints son
 * más fáciles de sustituir que una dependencia con ciclo de vida propio.
 */
interface MapboxApi {

    /**
     * Ruta en coche entre dos puntos. Las coordenadas van como `lon,lat;lon,lat`
     * dentro de la ruta del URL, que es como lo quiere Mapbox.
     */
    @GET("directions/v5/mapbox/driving/{coordinates}")
    suspend fun directions(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("access_token") token: String,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full",
        @Query("steps") steps: Boolean = true,
        @Query("language") language: String = "en",
    ): DirectionsResponse

    companion object {
        const val BASE_URL = "https://api.mapbox.com/"

        fun create(): MapboxApi {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MapboxApi::class.java)
        }
    }
}

@Serializable
data class DirectionsResponse(val code: String = "", val routes: List<DirectionsRoute> = emptyList())

@Serializable
data class DirectionsRoute(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val geometry: LineString? = null,
    val legs: List<DirectionsLeg> = emptyList(),
)

@Serializable
data class LineString(val coordinates: List<List<Double>> = emptyList())

@Serializable
data class DirectionsLeg(val steps: List<DirectionsStep> = emptyList())

@Serializable
data class DirectionsStep(
    val distance: Double = 0.0,
    val name: String = "",
    val maneuver: Maneuver = Maneuver(),
)

@Serializable
data class Maneuver(val instruction: String = "", val location: List<Double> = emptyList())
