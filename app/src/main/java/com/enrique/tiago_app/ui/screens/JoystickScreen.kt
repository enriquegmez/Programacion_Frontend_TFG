package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.enrique.tiago_app.ui.components.JoystickComponent
import com.enrique.tiago_app.ui.components.MonoValue
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.utils.AppConstants

/* ============================================================================
 *  TELEOPERACIÓN (REFACTOR VISUAL AXON)
 *  Lógica intacta: movementState, targetTopic, onTopicChange, toggleTeleop,
 *  updateJoystick, auto-selección del primer topic, mismo isCompact.
 *  Diseño alineado con la maqueta: caja de topic con label flotante, switch
 *  en azul de marca, joystick claro y lectura v/w en vivo (monoespaciada).
 * ========================================================================== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoystickView(
    controlViewModel: ControlViewModel,
    teleopTopics: List<String>,
    isCompact: Boolean = false
) {
    val movState by controlViewModel.movementState.collectAsState()
    val topicText by controlViewModel.targetTopic.collectAsState()
    val liveV by controlViewModel.liveV.collectAsState()
    val liveW by controlViewModel.liveW.collectAsState()
    val isTeleopActive = movState == AppConstants.MovementState.ENVIANDO_INFO
    val isLoading = movState.startsWith("ESPERANDO_")
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(teleopTopics) {
        if (topicText.isBlank() && teleopTopics.isNotEmpty()) controlViewModel.onTopicChange(teleopTopics.first())
    }

    Column(
        Modifier.fillMaxSize().padding(if (isCompact) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (!isCompact) {
                // Caja de topic con label flotante (como en la maqueta)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isTeleopActive && !isLoading && teleopTopics.isNotEmpty()) expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (teleopTopics.isEmpty()) "Ningún topic detectado" else topicText,
                        onValueChange = {}, readOnly = true,
                        label = { Text("Topic de Teleoperación") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        enabled = !isTeleopActive && !isLoading,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        teleopTopics.forEach { topic ->
                            DropdownMenuItem(text = { Text(topic) }, onClick = { controlViewModel.onTopicChange(topic); expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Interruptor de motores — AZUL de marca cuando armado
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isTeleopActive) "Motores Armados" else "Motores Bloqueados",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.width(14.dp))
                if (isLoading) CircularProgressIndicator(Modifier.size(24.dp))
                else Switch(
                    checked = isTeleopActive,
                    onCheckedChange = { controlViewModel.toggleTeleop(it) },
                    enabled = topicText.isNotBlank(),
                    colors = SwitchDefaults.colors(
                        // Cuando está ENCENDIDO (Armado)
                        checkedTrackColor = cs.primary,     // Azul (el que ya tenías)
                        checkedThumbColor = Color.White,    // Blanco
                        checkedBorderColor = cs.primary,

                        // Cuando está APAGADO (Bloqueado)
                        uncheckedTrackColor = Color.Gray,   // <--- CAMBIA ESTO A COLOR.GRAY O EL GRIS QUE QUIERAS
                        uncheckedThumbColor = cs.surface,
                        uncheckedBorderColor = Color.Gray   // <--- OPCIONAL: Cambia el borde también para que sea gris
                    )
                )
            }
        }

        JoystickComponent(
            size = if (isCompact) 180.dp else 240.dp,
            isEnabled = isTeleopActive,
            onVelocityChanged = { v, w -> controlViewModel.updateJoystick(v, w) }
        )

        if (!isCompact) {
            // Lectura en vivo de velocidades (monoespaciada). Muestra el valor real
            // que se está enviando; 0.00 cuando el joystick está centrado o bloqueado.
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                MonoValue(
                    value = formatVel(if (isTeleopActive) liveV else 0f),
                    label = "linear v (m/s)",
                    tint = cs.onSurface
                )
                MonoValue(
                    value = formatVel(if (isTeleopActive) liveW else 0f),
                    label = "angular w (rad/s)",
                    tint = cs.onSurface
                )
            }
        }
    }
}

/** Formatea una velocidad a 2 decimales, evitando el "-0.00". */
private fun formatVel(value: Float): String {
    val v = if (value == 0f || (value > -0.005f && value < 0.005f)) 0f else value
    return String.format("%.2f", v)
}