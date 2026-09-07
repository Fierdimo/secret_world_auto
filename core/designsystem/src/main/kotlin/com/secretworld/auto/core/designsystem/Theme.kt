package com.secretworld.auto.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
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

private fun typographyFor(sizes: CarTextSizes) = Typography(
    displayMedium = TextStyle(fontSize = sizes.display, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = sizes.title, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = sizes.body),
    labelLarge = TextStyle(fontSize = sizes.body, fontWeight = FontWeight.Medium),
    bodySmall = TextStyle(fontSize = sizes.caption),
)

@Composable
fun SecretWorldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Todo se deriva del tamaño de ventana. Una pantalla nueva no necesita código
    // nuevo: necesita entrar por aquí.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val metrics = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density,
        fontScale,
    ) {
        CarMetrics(
            widthDp = configuration.screenWidthDp.dp,
            heightDp = configuration.screenHeightDp.dp,
            density = density,
            fontScale = fontScale,
        )
    }

    CompositionLocalProvider(LocalCarMetrics provides metrics) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = typographyFor(metrics.text),
            content = content,
        )
    }
}
