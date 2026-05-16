package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField

// IMPORTS DE TU ARQUITECTURA
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.utils.AppConstants
import com.enrique.tiago_app.ui.components.JoystickComponent // Importa tu propio Joystick

/**
 * ControlScreen (Pantalla 3 - Teleoperación)
 * La cabina de mando del robot.
 */
@Composable
fun ControlScreen(
    controlViewModel: ControlViewModel,
    mainViewModel: MainViewModel // Lo necesitamos para el botón de "Desconectar"
) {
    // 1. Observamos el estado del semáforo de movimiento
    val movState by controlViewModel.movementState.collectAsState()
    val topicText by controlViewModel.targetTopic.collectAsState()

    // 2. Variables derivadas para simplificar la UI
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)
    val isLoading = movState.startsWith("ESPERANDO_")

    // 3. UI: Contenedor vertical
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween // Separa los elementos arriba, centro y abajo
    ) {

        // --- BLOQUE SUPERIOR: Estado y Controles ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Control Manual",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 🎯 AQUÍ VA LA CAJA DE TEXTO PARA EL TÓPICO
            OutlinedTextField(
                value = topicText,
                onValueChange = { controlViewModel.onTopicChange(it) },
                label = { Text("Target Topic (Opcional)") },
                placeholder = { Text("Ej: /joy_vel o /key_vel") },
                singleLine = true,
                // Se bloquea para que no puedan cambiarlo mientras conducen
                enabled = !isTeleopActive && !isLoading,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fila con el Switch de Habilitación
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isTeleopActive) "Motores Armados" else "Motores Bloqueados",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(16.dp))

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = isTeleopActive,
                        onCheckedChange = { isChecked ->
                            controlViewModel.toggleTeleop(isChecked)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Texto técnico para ver exactamente qué dice la máquina de estados
            Text(
                text = "Estado: $movState",
                color = when {
                    isTeleopActive -> Color(0xFF4CAF50) // Verde
                    isLoading -> Color(0xFFFF9800)      // Naranja
                    else -> MaterialTheme.colorScheme.onSurfaceVariant // Gris
                },
                style = MaterialTheme.typography.bodySmall
            )
        }

        // --- BLOQUE CENTRAL: El Joystick ---
        JoystickComponent(
            size = 250.dp,
            isEnabled = isTeleopActive,
            onVelocityChanged = { v, w ->
                controlViewModel.updateJoystick(v, w)
            }
        )

        // --- BLOQUE INFERIOR: Botón de Salida ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = { mainViewModel.disconnectFromRobot() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp),
                enabled = !isLoading // No dejamos que se desconecte a medias de otra cosa
            ) {
                Text("Desconectar Robot")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}