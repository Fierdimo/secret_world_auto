package com.secretworld.auto.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.secretworld.auto.domain.Coordinates

/** En el vehículo no hay mapa propio: la ficha va sin él. Ver [hasSpotMap]. */
@Composable
fun SpotMap(at: Coordinates, modifier: Modifier = Modifier) = Unit

/** Si esta build tiene mapa para la ficha. */
const val hasSpotMap = false
