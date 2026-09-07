package com.secretworld.auto.core.platform

import com.secretworld.auto.domain.Coordinates
import com.secretworld.auto.domain.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * De donde sale el estado del vehiculo.
 *
 * Esta capa existe para que los SDK de Pleos sean sustituibles y, sobre todo,
 * **falsificables**: sin un doble no se puede desarrollar el producto sin un
 * coche delante, y con un doble se puede probar un trayecto entero en segundos.
 */
interface VehicleSource {
    val state: StateFlow<VehicleState>
}

/**
 * Vehiculo simulado que avanza en linea recta. Suficiente para desarrollar y
 * demostrar todo el bucle: posicion, rumbo, velocidad y estado de conduccion.
 */
class SimulatedVehicleSource(
    private val start: Coordinates,
    private val headingDegrees: Double = 0.0,
    private val speedMetersPerSecond: Double = 25.0,
) : VehicleSource {

    private val _state = MutableStateFlow(
        VehicleState(start, headingDegrees, 0.0, parked = true),
    )
    override val state: StateFlow<VehicleState> = _state.asStateFlow()

    private var travelled = 0.0

    /** Avanza [seconds] segundos de trayecto. */
    fun advance(seconds: Double) {
        travelled += speedMetersPerSecond * seconds
        _state.value = VehicleState(
            position = positionAt(travelled),
            headingDegrees = headingDegrees,
            speedMetersPerSecond = speedMetersPerSecond,
            parked = false,
        )
    }

    fun park() {
        _state.value = _state.value.copy(speedMetersPerSecond = 0.0, parked = true)
    }

    fun drive() {
        _state.value = _state.value.copy(
            speedMetersPerSecond = speedMetersPerSecond,
            parked = false,
        )
    }

    private fun positionAt(meters: Double): Coordinates {
        val radians = Math.toRadians(headingDegrees)
        val north = meters * Math.cos(radians)
        val east = meters * Math.sin(radians)
        return Coordinates(
            latitude = start.latitude + north / 111_320.0,
            longitude = start.longitude + east / (111_320.0 * Math.cos(Math.toRadians(start.latitude))),
        )
    }
}
