package com.secretworld.auto.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

/**
 * Las medidas que usa la interfaz.
 *
 * **No se construyen a mano**: las calcula [CarMetrics] a partir del tamaño de
 * ventana. Esta clase solo les pone nombre.
 */
data class CarDimens(
    /**
     * Objetivo táctil mínimo. Material usa 48 dp; en automoción se recomienda 76 y
     * en una pantalla grande hace falta más, porque se toca de reojo y en marcha.
     */
    val minTouchTarget: Dp,
    /** Ancho mínimo de un botón de acción. Manda la altura, no el ancho. */
    val actionMinWidth: Dp,
    val spaceXs: Dp,
    val spaceS: Dp,
    val spaceM: Dp,
    val spaceL: Dp,
    val screenPadding: Dp,
    val cardCorner: Dp,
    /** Ancho máximo de una columna de texto: leer líneas de 3840 px es imposible. */
    val maxContentWidth: Dp,
    /**
     * Alto de una barra de utilidad —controles secundarios, no acciones del
     * producto—. Tiene nombre porque el mapa necesita saberlo para no esconder
     * sus adornos debajo.
     */
    val utilityBarHeight: Dp,
)

data class CarTextSizes(
    val display: TextUnit,
    val title: TextUnit,
    val body: TextUnit,
    val caption: TextUnit,
)
