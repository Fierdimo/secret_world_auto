package com.secretworld.auto.feature.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.secretworld.auto.feature.parked.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.secretworld.auto.core.designsystem.CarButton
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme
import com.secretworld.auto.core.designsystem.panelInsetsPadding

/**
 * Cuenta y ajustes, con el vehículo detenido.
 *
 * Deliberadamente corto y **sin nada inventado**: lo que no existe se dice que no
 * existe. Una pantalla de ajustes llena de conmutadores falsos es la forma más
 * rápida de que una demostración pierda credibilidad.
 */
/** Un botón de la sección de pruebas: el vehículo simulado, el silencio. */
data class SettingAction(val label: String, val onClick: () -> Unit)

/** Un idioma que se puede elegir, con su nombre en ese idioma. */
data class LanguageOption(val code: String, val label: String, val selected: Boolean)

@Composable
fun SettingsPanel(
    languages: List<LanguageOption>,
    onLanguage: (String) -> Unit,
    /** `null` si la voz no es nuestra —la de la plataforma, en Pleos—: no sale. */
    voiceInstalled: Boolean?,
    voiceSizeMb: Long,
    catalogueSource: String,
    onRemoveVoice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** "aparcado · 0 km/h": el vehículo, simulado o real. */
    testingStatus: String? = null,
    /** Lo que antes iba en una barra fija sobre la pantalla: simulador y silencio. */
    testing: List<SettingAction> = emptyList(),
    /** Los radios de acción que se pueden elegir, en km; vacío, no sale. */
    radii: List<Int> = emptyList(),
    radiusKm: Int = 0,
    onRadius: (Int) -> Unit = {},
    /** Si el tema es oscuro; `null`, no se puede elegir y no sale. */
    darkTheme: Boolean? = null,
    onDarkTheme: (Boolean) -> Unit = {},
) {
    val d = CarTheme.dimens
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .panelInsetsPadding()
            .verticalScroll(rememberScrollState())
            .padding(d.spaceM),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
            CarSecondaryButton(label = stringResource(R.string.close), onClick = onClose)
        }

        Spacer(Modifier.height(d.spaceM))

        // El idioma de toda la app —pantallas, voz, órdenes—, no solo el de la
        // narración. Al elegirlo la app se vuelve a abrir en él.
        SectionLabel(stringResource(R.string.narration_language))
        OptionGrid(languages.map { Option(it.label, it.selected) { onLanguage(it.code) } })
        // Hasta dónde mira el inicio: lo de alrededor y las búsquedas, dentro de este radio.
        if (radii.isNotEmpty()) {
            Spacer(Modifier.height(d.spaceM))
            SectionLabel(stringResource(R.string.action_radius))
            OptionGrid(radii.map { km -> Option(stringResource(R.string.radius_km, km), km == radiusKm) { onRadius(km) } })
        }
        // Claro u oscuro: el mapa y los paneles cambian juntos. Al elegirlo la app
        // se vuelve a abrir en él, como con el idioma.
        if (darkTheme != null) {
            Spacer(Modifier.height(d.spaceM))
            SectionLabel(stringResource(R.string.theme))
            OptionGrid(
                listOf(false to R.string.theme_light, true to R.string.theme_dark).map { (dark, name) ->
                    Option(stringResource(name), dark == darkTheme) { onDarkTheme(dark) }
                },
            )
        }
        Spacer(Modifier.height(d.spaceM))
        if (voiceInstalled != null) {
            SettingRow(
                label = stringResource(R.string.installed_voice),
                value = if (voiceInstalled) stringResource(R.string.voice_on_device, voiceSizeMb) else stringResource(R.string.voice_missing),
            )
        }
        SettingRow(stringResource(R.string.catalogue), catalogueSource)

        if (voiceInstalled == true) {
            Spacer(Modifier.height(d.spaceS))
            CarSecondaryButton(label = stringResource(R.string.remove_voice), onClick = onRemoveVoice)
        }

        Spacer(Modifier.height(d.spaceL))
        SectionLabel(stringResource(R.string.account))
        Text(
            text = stringResource(R.string.account_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (testing.isNotEmpty() || testingStatus != null) {
            Spacer(Modifier.height(d.spaceL))
            SectionLabel(stringResource(R.string.testing))
            testingStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(d.spaceXs))
            }
            OptionGrid(testing.map { Option(it.label, selected = false, onClick = it.onClick) })
        }
    }
}

/** El nombre de una sección: pequeño, en mayúsculas y del color de acento. */
@Composable
private fun SectionLabel(text: String) {
    val d = CarTheme.dimens
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(d.spaceXs))
}

private class Option(val label: String, val selected: Boolean, val onClick: () -> Unit)

/**
 * Las opciones de una sección en rejilla regular: todas del mismo ancho y con
 * tantas columnas como quepan. En una fila sin más, en el hueco pequeño de
 * Pleos "Español" salía cortado.
 */
@Composable
private fun OptionGrid(options: List<Option>) {
    if (options.isEmpty()) return
    val d = CarTheme.dimens
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minWidth = d.actionMinWidth
        val fits = ((maxWidth + d.spaceXs) / (minWidth + d.spaceXs)).toInt().coerceIn(1, options.size)
        val rows = (options.size + fits - 1) / fits
        val columns = (options.size + rows - 1) / rows
        Column(verticalArrangement = Arrangement.spacedBy(d.spaceXs)) {
            options.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.spaceXs)) {
                    row.forEach { option ->
                        if (option.selected) {
                            CarButton(label = option.label, onClick = {}, dense = true, modifier = Modifier.weight(1f))
                        } else {
                            CarSecondaryButton(label = option.label, onClick = option.onClick, dense = true, modifier = Modifier.weight(1f))
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    val d = CarTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.cardCorner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(d.spaceS),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(d.spaceXs))
}
