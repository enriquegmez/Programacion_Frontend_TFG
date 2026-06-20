package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.horizontalScroll // ¡NUEVO IMPORT!
import androidx.compose.foundation.rememberScrollState // ¡NUEVO IMPORT!
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enrique.tiago_app.protocol.JointLimit
import com.enrique.tiago_app.ui.logic.JointControlViewModel

@OptIn(ExperimentalMaterial3Api::class) // Ya no necesitamos el ExperimentalLayoutApi del FlowRow
@Composable
fun JointControlScreen(viewModel: JointControlViewModel) {

    val robotInfo by viewModel.capabilities.collectAsState()
    val activeJoints by viewModel.activeJoints.collectAsState()
    val jointValues by viewModel.jointValues.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenDisposed()
        }
    }

    val controlableJoints = robotInfo?.capabilities?.controlableJoints ?: emptyList()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("Selecciona las articulaciones:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (controlableJoints.isEmpty()) {
            Text(
                text = "No se han detectado articulaciones móviles en este robot.",
                color = MaterialTheme.colorScheme.error
            )
        } else {
            // 1. ZONA DE SELECCIÓN (Fila con Scroll Horizontal Seguro)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()), // Permite deslizar a los lados
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                controlableJoints.forEach { joint ->
                    val isSelected = activeJoints.contains(joint.name)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleJoint(joint.name, !isSelected) },
                        label = { Text(joint.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // 2. ZONA DE SLIDERS
            if (activeJoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Activa al menos una articulación arriba para controlarla.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    val activeJointLimits = controlableJoints.filter { activeJoints.contains(it.name) }

                    items(items = activeJointLimits) { jointLimit ->
                        JointSliderItem(
                            jointLimit = jointLimit,
                            // 1º Prioridad: Donde tenga el dedo el usuario ahora mismo (jointValues)
                            // 2º Prioridad: La posición real física del robot (jointLimit.currentValue)
                            // 3º Prioridad: El punto medio (por si falla la red)
                            currentValue = jointValues[jointLimit.name] ?: jointLimit.currentValue ?: ((jointLimit.min + jointLimit.max) / 2f),
                            onValueChange = { newValue -> viewModel.updateJointValue(jointLimit.name, newValue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JointSliderItem(
    jointLimit: JointLimit,
    currentValue: Float,
    onValueChange: (Float) -> Unit
) {
    // Blindaje de límites matemáticos
    val isInvalidRange = jointLimit.min >= jointLimit.max
    val safeMin = if (isInvalidRange) -3.14f else jointLimit.min
    val safeMax = if (isInvalidRange) 3.14f else jointLimit.max
    val safeCurrentValue = currentValue.coerceIn(safeMin, safeMax)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = jointLimit.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(text = String.format("%.2f", safeCurrentValue), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = safeCurrentValue,
                onValueChange = onValueChange,
                valueRange = safeMin..safeMax,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Min: ${String.format("%.2f", safeMin)}", fontSize = 12.sp)
                Text(text = "Max: ${String.format("%.2f", safeMax)}", fontSize = 12.sp)
            }
        }
    }
}