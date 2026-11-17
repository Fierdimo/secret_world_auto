package com.secretworld.auto.core.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Avisa cuando vuelve la conexión: al arrancar en un garaje, al salir de un
 * túnel. Lo que se pidió sin red —el catálogo, la clave del mapa, las fotos—
 * se quedaba en el respaldo hasta reabrir la app; con esto se vuelve a pedir.
 *
 * [comebacks] sube cada vez que se pasa de no tener internet a tenerlo.
 */
class NetworkWatcher(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val _comebacks = MutableStateFlow(0)
    val comebacks: StateFlow<Int> = _comebacks.asStateFlow()

    /** Validada: con internet de verdad, no solo una red a la que se está conectado. */
    private var online = connectivity?.let { cm -> cm.getNetworkCapabilities(cm.activeNetwork)?.validated() } ?: false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val now = capabilities.validated()
            if (now && !online) _comebacks.update { it + 1 }
            online = now
        }

        override fun onLost(network: Network) {
            online = false
        }
    }

    fun start() {
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    private fun NetworkCapabilities.validated() =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
