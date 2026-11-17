package com.secretworld.auto.core.designsystem

import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8B04B),
    onPrimary = Color(0xFF221A08),
    secondary = Color(0xFF7FB0A5),
    background = Color(0xFF101112),
    onBackground = Color(0xFFF2F2F0),
    surface = Color(0xFF1B1D1F),
    onSurface = Color(0xFFF2F2F0),
    surfaceVariant = Color(0xFF272A2D),
    onSurfaceVariant = Color(0xFFC3C6C8),
    outline = Color(0xFF44494D),
)

private val LightColors = lightColorScheme(
    // El ambar de la paleta oscura se ensucia al pasarlo a claro, asi que este
    // tiene valor propio en vez de derivarse.
    primary = Color(0xFFB07A12),
    onPrimary = Color.White,
    secondary = Color(0xFF2F6459),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF17181A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181A),
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF4A4E52),
    outline = Color(0xFFC6C3BC),
)

/**
 * Todos los estilos que usa la app salen de la pantalla. Uno que faltara se
 * quedaría con el valor fijo de Material —14 sp— y en el coche, con todo lo
 * demás escalado, era letra de nota a pie de página.
 */
private fun typographyFor(sizes: CarTextSizes) = Typography(
    displayMedium = TextStyle(fontSize = sizes.display, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = sizes.display * 0.85f, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = sizes.title, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = sizes.title * 0.8f, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = sizes.body, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = sizes.body),
    bodyMedium = TextStyle(fontSize = sizes.caption * 1.1f),
    bodySmall = TextStyle(fontSize = sizes.caption),
    labelLarge = TextStyle(fontSize = sizes.body, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = sizes.caption, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = sizes.caption * 0.9f, fontWeight = FontWeight.Medium),
)

@Composable
fun SecretWorldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Medidas de móvil en vez de pantalla de coche. Ver [CarMetrics]. */
    handheld: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Todo se deriva del tamaño de ventana. Una pantalla nueva no necesita código
    // nuevo: necesita entrar por aquí.
    //
    // El tamaño es el de la **ventana**, no el de la configuración: Pleos abre las
    // apps en sus huecos (1640×1400 y 800×1400) y les pasa la configuración de
    // toda la pantalla, 2320×1324. Con ella, la app medía mal y no cambiaba al
    // pasar de un hueco a otro.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val window = LocalWindowInfo.current.containerSize
    val widthDp = if (window.width > 0) window.width / density else configuration.screenWidthDp.toFloat()
    val heightDp = if (window.height > 0) window.height / density else configuration.screenHeightDp.toFloat()
    val metrics = remember(
        widthDp,
        heightDp,
        density,
        fontScale,
        handheld,
    ) {
        CarMetrics(
            widthDp = widthDp.dp,
            heightDp = heightDp.dp,
            density = density,
            fontScale = fontScale,
            handheld = handheld,
        )
    }

    val colors = if (darkTheme) DarkColors else LightColors
    val reducedMotion = remember { systemReducesMotion() }
    CompositionLocalProvider(LocalCarMetrics provides metrics, LocalDarkTheme provides darkTheme, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = colors,
            typography = typographyFor(metrics.text),
        ) {
            // MaterialTheme fija la paleta pero **no el color del contenido**: eso
            // lo hace Surface, y la app pinta sus fondos con `background(...)`
            // porque el mapa va debajo. Sin esto, todo Text sin color explícito
            // hereda el negro por defecto de Compose: negro sobre negro.
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                content = content,
            )
        }
    }
}
