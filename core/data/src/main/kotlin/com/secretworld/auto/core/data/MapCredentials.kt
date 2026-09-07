package com.secretworld.auto.core.data

import kotlinx.serialization.Serializable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class KeyResponse(val key: String? = null)

/**
 * Claves de servicios de mapas, pedidas al backend en tiempo de ejecución.
 *
 * El mapa de Mapbox es **solo para el MVP**: en el vehículo se usará el servicio
 * de mapas que traiga el dispositivo. Por eso esta clase vive en datos y no en la
 * interfaz — la fuente del mapa es una decisión de plataforma, no de pantalla.
 */
class MapCredentials(private val api: SecretWorldApi) {

    private var cached: String? = null

    /** Clave pública de Mapbox, o null si el backend no la da. */
    suspend fun mapboxToken(): String? {
        cached?.let { return it }
        return runCatching {
            withContext(Dispatchers.IO) { api.key(MAPBOX).key }
        }.getOrNull()?.takeIf { it.isNotBlank() }?.also { cached = it }
    }

    private companion object {
        const val MAPBOX = "mapbox_api_key"
    }
}
