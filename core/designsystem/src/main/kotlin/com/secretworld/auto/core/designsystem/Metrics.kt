package com.secretworld.auto.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Todas las medidas se **derivan del tamaño de ventana**, no se eligen a mano.
 *
 * El motivo es concreto: los valores fijos se ajustan a las pantallas que uno
 * tiene delante y fallan con la siguiente. Este proyecto ya ha visto tres tamaños
 * muy distintos —móvil apaisado de 597 dp, ventana de coche de 1658 dp y pantalla
 * Connect‑W de 3840— y habrá más: Connect‑S, Connect‑WF, media pantalla, vertical.
 *
 * ## Por qué la escala es la raíz cuadrada y no proporcional
 *
 * Duplicar el ancho de pantalla **no** debe duplicar el tamaño del texto. Lo que
 * cambia entre un móvil en la mano y una pantalla de coche no es solo el espacio:
 * es la **distancia de lectura**, que crece mucho más despacio que la diagonal.
 *
 * Con escala lineal, la pantalla de coche tendría letras cinco veces mayores que
 * el móvil: ridículo. Con raíz cuadrada, la relación queda en torno a 2,3×, que es
 * justo lo que salió al ajustar ambas pantallas a ojo por separado.
 */
class CarMetrics(
    val widthDp: Dp,
    val heightDp: Dp,
    /**
     * Densidad de la pantalla (1,0 = mdpi). Se usa para **corregir una mentira**:
     * la imagen de Pleos declara 160 dpi para un panel de 12,8 pulgadas, cuando la
     * densidad física ronda el doble. Consecuencia: en el coche un dp mide la mitad
     * de lo que debería, y hay que compensarlo. Un móvil, que reporta 2,5–3, no
     * necesita corrección.
     */
    private val density: Float = 1f,
    /**
     * Ajuste de tamaño de letra del sistema. **Se neutraliza a propósito.**
     *
     * Un `sp` se dibuja multiplicado por este factor, mientras que los anchos se
     * calculan en `dp`. Con las dos escalas a la vez, la cuenta deja de cuadrar:
     * en un móvil con 1,15 la etiqueta de un botón crecía un 15 % sobre el ancho
     * mínimo que se le había reservado y **se partía en tres líneas**. Aquí el
     * tamaño de letra ya sale de la pantalla, que es lo que pidió el diseño, así
     * que no debe escalarse dos veces.
     */
    private val fontScale: Float = 1f,
    /**
     * La pantalla se sostiene en la mano, a un palmo de la cara, y no en un
     * salpicadero a un brazo de distancia. La misma ventana necesita entonces
     * letras y botones más pequeños: se mide contra otra referencia.
     */
    private val handheld: Boolean = false,
) {
    /** El lado corto manda: es el que limita cuánto cabe sin apretar. */
    private val shortSideDp: Float = min(widthDp.value, heightDp.value)

    /**
     * Cuánto se agranda todo respecto a la ventana de referencia.
     *
     * Acotado por los dos extremos: por debajo, el texto dejaría de leerse; por
     * encima, una pantalla enorme tendría letras de cartel.
     */
    /**
     * Compensación de la densidad declarada, aplicada **en rampa**.
     *
     * Con un escalón —"a partir de 900 dp, compensa"— aparecía un salto absurdo:
     * una ventana de 900 dp de alto cabía más elementos que una de 1440, porque la
     * segunda cruzaba el umbral y todo se agrandaba de golpe. Una rampa elimina el
     * borde y hace que ventanas parecidas se comporten parecido.
     */
    private val densityCompensation: Float =
        if (density >= LOW_DENSITY) {
            1f
        } else {
            val ramp = ((shortSideDp - RAMP_START) / (RAMP_END - RAMP_START)).coerceIn(0f, 1f)
            1f + (DENSITY_BOOST - 1f) * ramp
        }

    private val referenceShortSide: Float =
        if (handheld) HANDHELD_REFERENCE_SHORT_SIDE else REFERENCE_SHORT_SIDE

    val scale: Float = (sqrt(shortSideDp / referenceShortSide) * densityCompensation)
        .coerceIn(if (handheld) HANDHELD_MIN_SCALE else MIN_SCALE, MAX_SCALE)

    /** Escala cualquier medida no prevista. Es la puerta de salida para casos nuevos. */
    fun scaled(reference: Dp): Dp = reference * scale

    fun scaled(reference: TextUnit): TextUnit =
        reference * scale / fontScale.coerceAtLeast(MIN_FONT_SCALE)

    /** Ventana estrecha: móvil, media pantalla del vehículo, o una Connect pequeña. */
    val isCompact: Boolean get() = shortSideDp < COMPACT_SHORT_SIDE

    val dimens: CarDimens = CarDimens(
        minTouchTarget = scaled(84.dp),
        actionMinWidth = scaled(130.dp),
        spaceXs = scaled(9.dp),
        spaceS = scaled(17.dp),
        spaceM = scaled(28.dp),
        spaceL = scaled(45.dp),
        screenPadding = scaled(40.dp),
        cardCorner = scaled(20.dp),
        maxContentWidth = scaled(700.dp),
        utilityBarHeight = scaled(60.dp),
    )

    val text: CarTextSizes = CarTextSizes(
        display = scaled(50.sp),
        title = scaled(36.sp),
        body = scaled(24.sp),
        caption = scaled(20.sp),
    )

    /**
     * Ancho de la banda lateral.
     *
     * **No es un porcentaje.** El mínimo sale de lo que tiene que caber —el nombre
     * de un lugar y sus dos acciones en fila—, y el máximo de cuánto texto se lee
     * cómodo sin que la línea se haga interminable. Entre ambos, se pide una
     * fracción de la ventana.
     */
    fun panelWidth(expanded: Boolean): Dp {
        val minimum = dimens.actionMinWidth * 2 + dimens.spaceXs * 3 + dimens.spaceM * 2
        // En la mano la pantalla es más estrecha en dp y el mapa se mira
        // menos: la banda se lleva más, para que quepan las tres acciones.
        val maximum = if (expanded) scaled(700.dp) else scaled(if (handheld) 600.dp else 520.dp)
        val share = when {
            expanded -> EXPANDED_SHARE
            handheld -> HANDHELD_PANEL_SHARE
            else -> PANEL_SHARE
        }
        val preferred = widthDp * share
        return preferred.coerceIn(minimum, maxOf(minimum, maximum))
    }

    /**
     * Ancho de la banda mientras se navega: solo lleva la maniobra, así que no
     * necesita el de la lista, y cada dp que no ocupa es mapa.
     */
    fun navigationPanelWidth(): Dp = panelWidth(expanded = false).coerceAtMost(scaled(NAVIGATION_PANEL))

    /**
     * ¿Caben tres acciones en fila dentro de la banda?
     *
     * Se pregunta en vez de suponerlo por el tamaño de pantalla: una banda de
     * 564 dp con botones de 186 necesita 584 y **se queda a veinte**. El síntoma
     * es un botón recortado fuera de la tarjeta, que es peor que no ponerlo.
     */
    fun fitsThreeActions(expanded: Boolean): Boolean = fitsActions(3, expanded)

    /**
     * ¿Caben [count] acciones en fila dentro de la banda?
     *
     * El ancho de referencia incluye la etiqueta: un botón que se queda sin sitio
     * no se recorta, parte el texto en tres líneas y **triplica el alto de la
     * tarjeta**, que era el síntoma que se veía en pantalla.
     */
    fun fitsActions(count: Int, expanded: Boolean): Boolean {
        val inner = panelWidth(expanded) - dimens.spaceM * 2 - dimens.spaceS * 2
        return inner >= dimens.actionMinWidth * count + dimens.spaceXs * (count - 1)
    }

    /** Qué fracción del mapa tapa la banda, para que el mapa desplace su foco. */
    fun panelCoverFraction(expanded: Boolean): Float =
        (panelWidth(expanded) / widthDp).coerceIn(0f, 0.9f)

    /**
     * Cuántos lugares caben en la lista.
     *
     * En marcha el límite no es el espacio sino la atención: tres, siempre. Con el
     * vehículo parado, lo que quepa de verdad según el alto disponible.
     */
    fun listItems(driving: Boolean): Int {
        // En marcha el límite es la atención, no el espacio: tres, siempre.
        if (driving) return DRIVING_ITEMS
        return itemsIn(heightDp - scaled(HEADER_AND_FOOTER), driving = false)
            .coerceAtLeast(2)
    }

    /**
     * Alto de una tarjeta de lugar.
     *
     * No es solo su botón: lleva nombre —que puede irse a dos líneas—, distancia,
     * acciones y separación. Medido sobre la pantalla real ronda el doble del
     * objetivo táctil más un margen. Quedarse corto aquí se paga en marcha, que es
     * cuando se corta la última tarjeta y aparece media fila ilegible.
     */
    val itemHeight: Dp = dimens.minTouchTarget * ITEM_HEIGHT_FACTOR + dimens.spaceM

    /**
     * Cuántas tarjetas caben en un hueco **ya medido**.
     *
     * Es la versión honesta de [listItems]: en vez de restarle al alto de pantalla
     * una cabecera supuesta, recibe el espacio que de verdad sobró tras componer
     * lo demás. Así el panel se llena en cualquier pantalla sin reservar de más
     * —lista de un solo elemento con medio panel vacío— ni de menos —última
     * tarjeta cortada—.
     */
    fun itemsIn(available: Dp, driving: Boolean): Int {
        val fits = (available / itemHeight).toInt().coerceIn(1, MAX_ITEMS)
        return if (driving) minOf(fits, DRIVING_ITEMS) else fits
    }

    private companion object {
        /**
         * Ventana de referencia. Con ella, un móvil apaisado sale en torno a 15 sp
         * de cuerpo, que es lo cómodo en la mano. Las pantallas de vehículo suben
         * además por la corrección de densidad.
         */
        const val REFERENCE_SHORT_SIDE = 700f
        /**
         * Referencia en la mano. Un móvil apaisado de 411 dp de alto sale con
         * 15 sp de cuerpo y 52 dp de objetivo táctil: lo de una app de móvil.
         */
        const val HANDHELD_REFERENCE_SHORT_SIDE = 1050f
        /** Ni en el móvil más bajo baja el objetivo táctil de los 48 dp de Material. */
        const val HANDHELD_MIN_SCALE = 48f / 84f
        const val MIN_SCALE = 0.55f
        const val MAX_SCALE = 2.2f
        /** Por debajo de esto, la pantalla declara menos densidad de la que tiene. */
        const val LOW_DENSITY = 1.5f
        /** Tramo en el que la compensación entra progresivamente. */
        const val RAMP_START = 600f
        const val RAMP_END = 1200f
        const val DENSITY_BOOST = 1.35f
        const val COMPACT_SHORT_SIDE = 600f
        const val PANEL_SHARE = 0.34f
        const val HANDHELD_PANEL_SHARE = 0.42f
        val NAVIGATION_PANEL = 480.dp
        const val EXPANDED_SHARE = 0.46f
        const val DRIVING_ITEMS = 3
        const val MAX_ITEMS = 8
        const val MIN_FONT_SCALE = 0.5f
        const val ITEM_HEIGHT_FACTOR = 2.0f
        /** Cabecera, título de sección y pie de "hay más". */
        val HEADER_AND_FOOTER = 260.dp
    }
}

val LocalCarMetrics = staticCompositionLocalOf { CarMetrics(1024.dp, 700.dp) }

/** Si el tema activo es oscuro. Lo fija [SecretWorldTheme]. */
val LocalDarkTheme = staticCompositionLocalOf { true }

/** Acceso corto a las medidas activas. */
object CarTheme {
    val metrics: CarMetrics
        @Composable @ReadOnlyComposable get() = LocalCarMetrics.current

    val dimens: CarDimens
        @Composable @ReadOnlyComposable get() = LocalCarMetrics.current.dimens

    val text: CarTextSizes
        @Composable @ReadOnlyComposable get() = LocalCarMetrics.current.text

    val compact: Boolean
        @Composable @ReadOnlyComposable get() = LocalCarMetrics.current.isCompact

    /**
     * Lo que no pinta Compose —el estilo del mapa, sus marcadores— necesita
     * saberlo para no quedarse oscuro sobre una interfaz clara.
     */
    val dark: Boolean
        @Composable @ReadOnlyComposable get() = LocalDarkTheme.current
}
