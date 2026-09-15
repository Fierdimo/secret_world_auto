package com.secretworld.auto.feature.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.secretworld.auto.core.designsystem.CarSecondaryButton
import com.secretworld.auto.core.designsystem.CarTheme

/**
 * Cuenta y ajustes, con el vehículo detenido.
 *
 * Deliberadamente corto y **sin nada inventado**: lo que no existe se dice que no
 * existe. Una pantalla de ajustes llena de conmutadores falsos es la forma más
 * rápida de que una demostración pierda credibilidad.
 */
@Composable
fun SettingsPanel(
    voiceLanguage: String,
    voiceInstalled: Boolean,
    voiceSizeMb: Long,
    catalogueSource: String,
    onRemoveVoice: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = CarTheme.dimens
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.93f))
            .safeDrawingPadding()
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

        SettingRow(stringResource(R.string.narration_language), voiceLanguage)
        SettingRow(
            label = stringResource(R.string.installed_voice),
            value = if (voiceInstalled) stringResource(R.string.voice_on_device, voiceSizeMb) else stringResource(R.string.voice_missing),
        )
        SettingRow(stringResource(R.string.catalogue), catalogueSource)

        Spacer(Modifier.height(d.spaceS))
        if (voiceInstalled) {
            CarSecondaryButton(label = stringResource(R.string.remove_voice), onClick = onRemoveVoice)
        }

        Spacer(Modifier.height(d.spaceL))
        Text(stringResource(R.string.account), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(d.spaceXs))
        Text(
            text = stringResource(R.string.account_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
