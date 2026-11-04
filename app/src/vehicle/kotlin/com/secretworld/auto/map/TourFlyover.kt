package com.secretworld.auto.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.secretworld.auto.feature.discover.FlyoverState

/**
 * En el vehículo aún no hay mapa para el itinerario: este build no lleva Mapbox
 * y el mapa nativo de Pleos espera a la librería de NaviService. Sin
 * presentación; la vista del itinerario queda sobre el fondo.
 */
@Composable
fun TourFlyover(
    state: FlyoverState,
    mapboxToken: String?,
    onIntroDone: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    onAddPlace: (com.secretworld.auto.domain.Place) -> Unit = {},
) {
    LaunchedEffect(state.intro) { if (state.intro) onIntroDone() }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { overlay() }
}
