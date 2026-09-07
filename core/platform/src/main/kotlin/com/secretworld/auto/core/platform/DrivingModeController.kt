package com.secretworld.auto.core.platform

import com.secretworld.auto.domain.DrivingMode
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted

/**
 * Unica fuente de verdad sobre que superficie puede estar visible (PLAN §3.2).
 *
 * Ninguna pantalla decide por su cuenta si "puede" mostrarse: todas observan
 * esto. Concentrarlo en un sitio es lo que hace auditable el cumplimiento de las
 * reglas de distraccion, en lugar de repartir la responsabilidad por la UI.
 */
class DrivingModeController(
    vehicle: VehicleSource,
    scope: CoroutineScope,
) {
    val mode: StateFlow<DrivingMode> = vehicle.state
        .map { it.drivingMode }
        .stateIn(scope, SharingStarted.Eagerly, vehicle.state.value.drivingMode)
}
