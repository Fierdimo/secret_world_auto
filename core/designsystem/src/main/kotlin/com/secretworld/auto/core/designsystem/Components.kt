package com.secretworld.auto.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Contenedor de pantalla. Limita el ancho de la columna de contenido: una linea
 * de texto de 3840 px de ancho no se lee, se escanea mal y marea.
 */
@Composable
fun CarScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val d = CarTheme.dimens
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = d.screenPadding, vertical = d.spaceL),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.widthIn(max = d.maxContentWidth * 2)) { content() }
    }
}

/**
 * Un lugar en la lista de conducción. **Dos acciones y solo dos** (SPEC §4.2):
 * escuchar y navegar. Cualquier tercera cosa que se quiera añadir aqui va, por
 * definicion, al modo detenido.
 */
@Composable
fun PlaceRow(
    name: String,
    distance: String,
    listenLabel: String,
    navigateLabel: String,
    onListen: () -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(d.cardCorner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = d.minTouchTarget + d.spaceS)
                .padding(d.spaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.spaceS),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = distance,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CarButton(listenLabel, onListen)
            CarSecondaryButton(navigateLabel, onNavigate)
        }
    }
}

/**
 * @param dense acción dentro de una lista **con el vehículo detenido**. El objetivo
 * táctil completo existe para tocar de reojo y en marcha; parado, mantenerlo en
 * cada tarjeta cuesta una fila entera de altura y se ven la mitad de lugares. En
 * marcha nunca se usa.
 */
@Composable
fun CarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val d = CarTheme.dimens
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(
            minWidth = d.actionMinWidth,
            minHeight = if (dense) d.minTouchTarget * DENSE_TARGET else d.minTouchTarget,
        ),
        shape = RoundedCornerShape(CarTheme.dimens.cardCorner),
        contentPadding = buttonPadding(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * @param compact para barras de utilidad, no para acciones del producto. Un botón
 * de acción en marcha necesita el objetivo táctil completo; una barra secundaria
 * que se usa con el coche parado no, y a cambio devuelve alto a lo que importa.
 */
@Composable
fun CarSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    dense: Boolean = false,
) {
    val d = CarTheme.dimens
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(
            minWidth = if (compact) d.actionMinWidth * 0.7f else d.actionMinWidth,
            minHeight = when {
                compact -> d.minTouchTarget * 0.5f
                dense -> d.minTouchTarget * DENSE_TARGET
                else -> d.minTouchTarget
            },
        ),
        shape = RoundedCornerShape(d.cardCorner),
        contentPadding = buttonPadding(),
    ) {
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * El relleno lateral de los botones. Material lo fija en 24 dp: en el coche
 * sobra sitio, pero en un móvil son casi la mitad del botón y la segunda acción
 * de cada tarjeta se salía por el borde. Escala hacia abajo, nunca hacia
 * arriba: en pantallas grandes queda como estaba.
 */
@Composable
private fun buttonPadding(): PaddingValues {
    val side = CarTheme.metrics.scaled(MATERIAL_BUTTON_PADDING).coerceAtMost(MATERIAL_BUTTON_PADDING)
    return PaddingValues(horizontal = side, vertical = ButtonDefaults.ContentPadding.calculateTopPadding())
}

private val MATERIAL_BUTTON_PADDING = 24.dp

@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = CarTheme.dimens.spaceM)) {
        Text(text, style = MaterialTheme.typography.displayMedium)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cuánto se reduce un objetivo táctil de lista con el vehículo detenido. */
private const val DENSE_TARGET = 0.68f

/**
 * Los márgenes de seguridad de una banda **pegada al borde izquierdo**: el
 * izquierdo, el de arriba y el de abajo, nunca el derecho. En un móvil apaisado
 * la barra de navegación del sistema está a la derecha, y con todos los lados
 * su ancho se le sumaba a la banda como relleno: un hueco vacío que tapaba mapa.
 */
@Composable
fun Modifier.panelInsetsPadding(): Modifier = windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
)
