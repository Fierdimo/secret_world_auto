package com.secretworld.auto.core.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El vehículo real: la posición del dispositivo.
 *
 * Es la implementación de [VehicleSource] para el banco de pruebas —un móvil o
 * tablet en un coche de verdad— y el respaldo en el vehículo hasta que el
 * Vehicle SDK de Pleos dé posición y velocidad por su vía. Usa el
 * `LocationManager` de Android, no Play Services: en una unidad de automoción
 * no hay Play Services.
 *
 * Empieza en [fallback] hasta que llega la primera posición, para que el mapa
 * tenga algo que enseñar: un mapa en blanco esperando al GPS es lo primero que
 * ve el usuario, y es peor que un mapa en Roma durante tres segundos.
 */
class DeviceVehicleSource(
    private val context: Context,
    fallback: Coordinates,
) : VehicleSource {

    private val _state = MutableStateFlow(VehicleState(fallback, headingDegrees = null, speedMetersPerSecond = 0.0, parked = true))
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    /** Si ya se ha recibido alguna posición real. Hasta entonces se está en [fallback]. */
    @Volatile
    var located: Boolean = false
        private set

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listening = false

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private val listener = LocationListener { location -> publish(location) }

    /** Empieza a seguir la posición. Sin permiso no hace nada, y lo dice. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (listening) return
        if (!hasPermission) {
            Log.w(TAG, "sin permiso de ubicación: el vehículo se queda en el punto de partida")
            return
        }
        val provider = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> {
                Log.w(TAG, "ningún proveedor de ubicación activo")
                return
            }
        }
        // La última posición conocida, para no esperar al primer fix.
        manager.getLastKnownLocation(provider)?.let(::publish)
        manager.requestLocationUpdates(provider, MIN_INTERVAL_MS, MIN_DISTANCE_M, listener, Looper.getMainLooper())
        listening = true
        Log.i(TAG, "siguiendo la posición por $provider")
    }

    fun stop() {
        if (!listening) return
        manager.removeUpdates(listener)
        listening = false
    }

    private fun publish(location: Location) {
        val speed = if (location.hasSpeed()) location.speed.toDouble() else 0.0
        val heading = if (location.hasBearing() && speed > MIN_SPEED_FOR_BEARING) location.bearing.toDouble() else _state.value.headingDegrees
        located = true
        _state.value = VehicleState(
            position = Coordinates(location.latitude, location.longitude),
            headingDegrees = heading,
            speedMetersPerSecond = speed,
            // Parado es parado de verdad, no "el GPS dice 0,3 m/s": por debajo de
            // esto la interfaz puede permitirse la densidad de aparcado.
            parked = speed < PARKED_BELOW_MPS,
        )
    }

    private companion object {
        const val TAG = "SWVehicle"
        const val MIN_INTERVAL_MS = 1_000L
        const val MIN_DISTANCE_M = 2f
        const val MIN_SPEED_FOR_BEARING = 1.0
        const val PARKED_BELOW_MPS = 0.8
    }
}
