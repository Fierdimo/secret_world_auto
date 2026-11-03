package com.secretworld.auto

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.secretworld.auto.domain.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Dónde está el usuario, dicho como se dice: el barrio y la ciudad de **su
 * posición**. Antes la cabecera tomaba la "ciudad" del lugar más cercano de
 * Wikipedia, que podía ser un barrio lejano —"Blas de Lezo (Cartagena de
 * Indias)"— con el usuario en otra punta.
 *
 * Devuelve "Barrio (Ciudad)", la misma forma que ya sabe partir la cabecera.
 */
class WhereAmI(private val context: Context) {

    private val cache = mutableMapOf<String, String?>()

    suspend fun describe(at: Coordinates, locale: Locale): String? {
        if (!Geocoder.isPresent()) return null
        // Un kilómetro, más o menos: no hace falta preguntar en cada calle.
        val key = String.format(Locale.ROOT, "%.2f,%.2f", at.latitude, at.longitude)
        synchronized(cache) { if (key in cache) return cache[key] }
        val address = runCatching { lookup(at, locale) }.getOrNull()
        val name = address?.let { a ->
            val city = a.locality ?: a.subAdminArea ?: a.adminArea
            val area = a.subLocality?.takeIf { it != city }
            when {
                city == null -> null
                area == null -> city
                else -> "$area ($city)"
            }
        }
        synchronized(cache) { cache[key] = name }
        return name
    }

    private suspend fun lookup(at: Coordinates, locale: Locale): Address? {
        val geocoder = Geocoder(context, locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(at.latitude, at.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(at.latitude, at.longitude, 1)?.firstOrNull()
            }
        }
    }
}
