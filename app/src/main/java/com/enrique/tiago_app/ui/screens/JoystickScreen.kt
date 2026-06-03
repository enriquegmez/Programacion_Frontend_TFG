package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.utils.AppConstants
import com.enrique.tiago_app.ui.components.JoystickComponent

@Composable
fun JoystickView(controlViewModel: ControlViewModel,
                 isCompact: Boolean = false // ¡NUEVO! Detecta si está en pantalla dividida)
) {
    // 1. Observamos el estado del semáforo de movimiento
    val movState by controlViewModel.movementState.collectAsState()
    val topicText by controlViewModel.targetTopic.collectAsState()

    // 2. Variables derivadas
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)
    val isLoading = movState.startsWith("ESPERANDO_")

    // 3. UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 4.dp else 16.dp), // Menos margen si es compacto
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // --- BLOQUE SUPERIOR ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Si NO está dividido, mostramos el título grande
            if (!isCompact) {
                Text(
                    text = "Teleoperación",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // CAJA DEL TOPIC: Solo se muestra si NO está compactado
            if (!isCompact) {
                OutlinedTextField(
                    value = topicText,
                    onValueChange = { controlViewModel.onTopicChange(it) },
                    label = { Text("Target Topic") },
                    placeholder = { Text("Topic (Ej: /cmd_vel)") },
                    singleLine = true,
                    enabled = !isTeleopActive && !isLoading,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // INTERRUPTOR DE MOTORES (Este se muestra siempre)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isTeleopActive) "Motores Armados" else "Motores Bloqueados",
                    // ¡MEJORA! Texto más pequeño en modo compacto
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(16.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = isTeleopActive,
                        onCheckedChange = { controlViewModel.toggleTeleop(it) },
                        // ¡MEJORA! Hacemos el interruptor un 20% más pequeño si está dividido
                        modifier = if (isCompact) Modifier.scale(0.8f) else Modifier
                    )
                }
            }
        }

        // --- BLOQUE CENTRAL (Joystick) ---
        // Hacemos el joystick más pequeño si la pantalla está dividida
        // ¡MEJORA! Como hemos ahorrado mucho espacio arriba, podemos subir a 190-200.dp el mando
        val joystickSize = 250.dp

        JoystickComponent(
            size = joystickSize,
            isEnabled = isTeleopActive,
            onVelocityChanged = { v, w ->
                controlViewModel.updateJoystick(v, w)
            }
        )
    }
}