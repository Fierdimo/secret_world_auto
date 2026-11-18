package com.secretworld.auto.feature.parked

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.secretworld.auto.domain.PlaceKindNames
import com.secretworld.auto.domain.VoiceLanguage

/** El tipo de un sitio en el idioma de la pantalla: "Restaurante", no "food". */
@Composable
@ReadOnlyComposable
internal fun kindLabel(kind: String?): String? {
    val tag = LocalConfiguration.current.locales[0]?.language
    return PlaceKindNames.label(kind, VoiceLanguage.fromTag(tag) ?: VoiceLanguage.ENGLISH)
}
