package com.secretworld.auto.feature.discover

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.secretworld.auto.core.designsystem.CarTheme

/**
 * Dónde va el panel que flota sobre el mapa: a la izquierda, con su parte del
 * ancho; o, en una ventana apilada (alta y estrecha), abajo a todo el ancho y
 * con el mapa encima. Los paneles de inicio, de "Mis itinerarios" y del
 * itinerario se colocan igual, y el mapa encuadra en lo que dejan libre.
 */
@Composable
internal fun BoxScope.panelPlacement(): Modifier {
    val m = CarTheme.metrics
    return if (m.stacked) {
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(m.sheetShare)
    } else {
        Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(m.overlayShare)
    }
}

/** Los márgenes del panel: apilado, casi ninguno; el ancho es lo que falta. */
@Composable
internal fun panelMargins(): PaddingValues {
    val d = CarTheme.dimens
    return if (CarTheme.metrics.stacked) {
        PaddingValues(start = d.spaceXs, end = d.spaceXs, bottom = d.spaceXs)
    } else {
        PaddingValues(start = d.spaceM, end = d.spaceM, bottom = d.spaceXs)
    }
}

/**
 * El fondo del panel: cristal sobre el mapa a un lado; opaco si va abajo, donde
 * los puntos del mapa se transparentaban sobre el texto.
 */
@Composable
internal fun panelGlass(alpha: Float = 0.84f): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = if (CarTheme.metrics.stacked) 1f else alpha)
