package com.secretworld.auto.core.designsystem

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Las tres ventanas que este proyecto ha visto de verdad, más una hipotética.
 * Si aparece una pantalla nueva, se añade aquí antes que en la interfaz.
 */
class CarMetricsTest {

    // La densidad importa: un móvil reporta 2,5; la imagen de Pleos reporta 1,0
    // para un panel de 12,8 pulgadas, y eso hay que compensarlo.
    private val phoneLandscape = CarMetrics(597.dp, 270.dp, density = 2.5f)
    private val vehicleWindow = CarMetrics(1658.dp, 1400.dp, density = 1f)
    private val connectWide = CarMetrics(3840.dp, 1440.dp, density = 1f)
    private val halfScreen = CarMetrics(1920.dp, 1440.dp, density = 1f)

    @Test
    fun `el texto crece con la pantalla, pero menos que ella`() {
        // Comparado a igual densidad: mezclar pantallas que declaran densidades
        // distintas compara peras con manzanas, porque un dp no mide lo mismo.
        val small = CarMetrics(900.dp, 600.dp, density = 1f)
        val large = CarMetrics(3840.dp, 1440.dp, density = 1f)

        val sizeRatio = 1440f / 600f
        val textRatio = large.text.body.value / small.text.body.value

        assertTrue(textRatio > 1f, "una pantalla mayor se mira de más lejos: crece")
        assertTrue(
            textRatio < sizeRatio,
            "el crecimiento es sublineal: era $textRatio frente a $sizeRatio",
        )
    }

    @Test
    fun `la banda siempre cabe con sus dos acciones en fila`() {
        listOf(phoneLandscape, vehicleWindow, connectWide, halfScreen).forEach { metrics ->
            val width = metrics.panelWidth(expanded = false)
            val needed = metrics.dimens.actionMinWidth * 2 +
                metrics.dimens.spaceXs * 3 + metrics.dimens.spaceM * 2

            assertTrue(
                width >= needed,
                "en ${metrics.widthDp} x ${metrics.heightDp} la banda mide $width y necesita $needed",
            )
        }
    }

    @Test
    fun `la banda nunca se come la pantalla`() {
        listOf(vehicleWindow, connectWide, halfScreen).forEach { metrics ->
            assertTrue(
                metrics.panelCoverFraction(expanded = false) < 0.4f,
                "en ${metrics.widthDp} la banda tapa demasiado mapa",
            )
        }
    }

    @Test
    fun `en una pantalla enorme la banda no crece sin fin`() {
        // Sin tope, el 34 % de 3840 dp serían 1300 dp de línea de texto: ilegible.
        // Se comprueba la proporción, no una cifra concreta: los valores se
        // recalibran, la regla no.
        assertTrue(
            connectWide.panelCoverFraction(expanded = false) < 0.32f,
            "en una pantalla enorme la banda debe ser una franja, no un tercio",
        )
        assertTrue(
            connectWide.panelWidth(expanded = false) < connectWide.widthDp * 0.34f,
            "el tope tiene que morder antes que la fracción",
        )
    }

    @Test
    fun `en marcha siempre caben tres, sea cual sea la pantalla`() {
        listOf(phoneLandscape, vehicleWindow, connectWide).forEach { metrics ->
            assertEquals(
                3,
                metrics.listItems(driving = true),
                "en marcha el límite es la atención, no el espacio",
            )
        }
    }

    @Test
    fun `parado caben mas elementos cuanto mas alta es la ventana`() {
        // A igual densidad: 1440 dp de alto tienen que dar para más que 1080.
        val tall = CarMetrics(1920.dp, 1440.dp, density = 1f)
        val short = CarMetrics(1920.dp, 900.dp, density = 1f)

        assertTrue(
            tall.listItems(driving = false) >= short.listItems(driving = false),
            "más alto nunca puede caber menos",
        )
    }

    @Test
    fun `dos paneles del mismo tamaño escalan distinto si mienten sobre su densidad`() {
        val honest = CarMetrics(1658.dp, 1400.dp, density = 2.0f)
        val misdeclared = CarMetrics(1658.dp, 1400.dp, density = 1.0f)

        assertTrue(
            misdeclared.scale > honest.scale,
            "un panel que declara 160 dpi siendo el doble necesita compensación",
        )
    }

    @Test
    fun `una ventana diminuta no produce medidas absurdas`() {
        val tiny = CarMetrics(320.dp, 200.dp, density = 2.5f)

        assertTrue(tiny.text.body.value > 12f, "por debajo de esto no se lee")
        assertTrue(tiny.dimens.minTouchTarget >= 44.dp, "por debajo de esto no se acierta")
        assertTrue(tiny.isCompact)
    }

    @Test
    fun `una pantalla gigante no produce letras de cartel`() {
        val huge = CarMetrics(7680.dp, 2880.dp, density = 1f)
        val enormous = CarMetrics(15360.dp, 5760.dp, density = 1f)

        // La comprobación no es un número: es que la escala **deja de crecer**.
        assertEquals(huge.scale, enormous.scale, absoluteTolerance = 0.001f)
        assertTrue(
            huge.text.body.value < huge.dimens.minTouchTarget.value,
            "el texto nunca debe acercarse al tamaño de un botón entero",
        )
    }

    @Test
    fun `scaled sirve para medidas no previstas`() {
        val reference = 100.dp

        assertTrue(vehicleWindow.scaled(reference) > phoneLandscape.scaled(reference))
        assertEquals(
            vehicleWindow.dimens.minTouchTarget.value,
            vehicleWindow.scaled(84.dp).value,
            absoluteTolerance = 0.01f,
        )
    }

    @Test
    fun `en la mano, medidas de movil`() {
        // Un móvil corriente apaisado: 915 x 411 dp a densidad 2,6.
        val inHand = CarMetrics(915.dp, 411.dp, density = 2.6f, handheld = true)
        val onDash = CarMetrics(915.dp, 411.dp, density = 2.6f)

        assertTrue(inHand.text.body < onDash.text.body, "en la mano la letra es menor")
        assertEquals(15f, inHand.text.body.value, absoluteTolerance = 0.5f)
        assertTrue(inHand.dimens.minTouchTarget >= 48.dp, "nunca por debajo de Material")
    }

    @Test
    fun `en la mano, ni el movil mas bajo baja de 48 dp`() {
        val tiny = CarMetrics(597.dp, 270.dp, density = 2.5f, handheld = true)

        assertTrue(tiny.dimens.minTouchTarget.value >= 47.99f, "era ${tiny.dimens.minTouchTarget}")
    }
}
