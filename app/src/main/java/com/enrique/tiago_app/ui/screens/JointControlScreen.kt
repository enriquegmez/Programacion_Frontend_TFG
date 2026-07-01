package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enrique.tiago_app.protocol.JointLimit
import com.enrique.tiago_app.ui.components.SteelCard
import com.enrique.tiago_app.ui.logic.JointControlViewModel
import com.enrique.tiago_app.ui.theme.MonoData
import com.enrique.tiago_app.ui.theme.MonoLabel

/* ============================================================================
 *  ARTICULACIONES (REFACTOR)
 *  Lógica intacta: capabilities, activeJoints, jointValues, toggleJoint,
 *  updateJointValue, onJointDragFinished, onScreenDisposed, mismo blindaje de
 *  rangos inválidos. Diseño: chips de selección + tarjetas con slider de
 *  pulgar grande (ergonómico) y valor en monoespaciada.
 * ========================================================================== */
@Composable
fun JointControlScreen(viewModel: JointControlViewModel, isCompact: Boolean = false) {
    val robotInfo by viewModel.capabilities.collectAsState()
    val activeJoints by viewModel.activeJoints.collectAsState()
    val jointValues by viewModel.jointValues.collectAsState()
    val cs = MaterialTheme.colorScheme

    DisposableEffect(Unit) { onDispose { viewModel.onScreenDisposed() } }

    val controlableJoints = robotInfo?.capabilities?.controlableJoints ?: emptyList()

    Column(Modifier.fillMaxSize().padding(if (isCompact) 8.dp else 16.dp)) {
        // --- SOLO MOSTRAR SI NO ES MODO COMPACTO ---
        if (!isCompact) {
            Text("Selecciona las articulaciones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
        }

        if (controlableJoints.isEmpty()) {
            Text("No se han detectado articulaciones móviles en este robot.", color = cs.error)
            return
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            controlableJoints.forEach { joint ->
                val selected = activeJoints.contains(joint.name)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleJoint(joint.name, !selected) },
                    label = { Text(joint.name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cs.primary.copy(alpha = 0.14f),
                        selectedLabelColor = cs.primary
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp)); HorizontalDivider(color = cs.outline); Spacer(Modifier.height(16.dp))

        if (activeJoints.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Activa al menos una articulación arriba para controlarla.", color = cs.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val active = controlableJoints.filter { activeJoints.contains(it.name) }
                items(active) { jl ->
                    JointSliderItem(
                        jointLimit = jl,
                        currentValue = jointValues[jl.name] ?: jl.currentValue ?: ((jl.min + jl.max) / 2f),
                        onValueChange = { viewModel.updateJointValue(jl.name, it) },
                        onDragFinished = { viewModel.onJointDragFinished(jl.name) }
                    )
                }
            }
        }
    }
}

@Composable
fun JointSliderItem(jointLimit: JointLimit, currentValue: Float, onValueChange: (Float) -> Unit, onDragFinished: () -> Unit) {
    val invalid = jointLimit.min >= jointLimit.max
    val min = if (invalid) -3.14f else jointLimit.min
    val max = if (invalid) 3.14f else jointLimit.max
    val value = currentValue.coerceIn(min, max)
    val cs = MaterialTheme.colorScheme

    SteelCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(jointLimit.name, fontWeight = FontWeight.Bold, color = cs.primary, style = MaterialTheme.typography.titleMedium)
            Text(String.format("%.2f", value), style = MonoData)
        }
        Spacer(Modifier.height(6.dp))
        Slider(
            value = value, onValueChange = onValueChange, onValueChangeFinished = onDragFinished,
            valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = cs.primary, activeTrackColor = cs.primary, inactiveTrackColor = cs.surfaceContainerHighest)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Min ${String.format("%.2f", min)}", style = MonoLabel, color = cs.onSurfaceVariant)
            Text("Max ${String.format("%.2f", max)}", style = MonoLabel, color = cs.onSurfaceVariant)
        }
    }
}