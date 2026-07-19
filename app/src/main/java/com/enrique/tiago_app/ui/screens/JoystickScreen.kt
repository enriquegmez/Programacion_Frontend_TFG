/**
 * @file JoystickView.kt
 * @brief Pantalla de teleoperación manual.
 * @details Proporciona un control de mando virtual (Joystick) para gobernar el movimiento
 *          base del robot. Implementa rutinas de seguridad estrictas, como el bloqueo de
 *          enrutamiento durante el movimiento y la necesidad de "armar" los motores previamente.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

// --- IMPORTS DE COMPONENTES Y LÓGICA ---
import com.enrique.tiago_app.ui.components.JoystickComponent
import com.enrique.tiago_app.ui.components.MonoValue
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.utils.AppConstants

/**
 * @brief Interfaz de control de movimiento base bidimensional.
 * @details Coordina la selección del tópico de destino (`geometry_msgs/Twist`), el estado de
 *          armado de los motores y la inyección de comandos espaciales generados por el joystick.
 * @param controlViewModel Instancia de [ControlViewModel] que gestiona la comunicación y estados de teleoperación.
 * @param teleopTopics Lista de tópicos disponibles en la red ROS 2 que aceptan comandos de velocidad.
 * @param isCompact Modo de renderizado. Si es true, oculta la cabecera y telemetría para maximizar el joystick.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoystickView(
    controlViewModel: ControlViewModel,
    teleopTopics: List<String>,
    isCompact: Boolean = false
) {
    // ========================================================================
    // 1. OBSERVADORES DE ESTADO
    // ========================================================================
    val movState by controlViewModel.movementState.collectAsState()
    val topicText by controlViewModel.targetTopic.collectAsState()
    val liveV by controlViewModel.liveV.collectAsState()
    val liveW by controlViewModel.liveW.collectAsState()

    // Banderas derivadas del estado de la red
    val isTeleopActive = movState == AppConstants.MovementState.ENVIANDO_INFO
    val isLoading = movState.startsWith("ESPERANDO_")

    // Estado local para el menú desplegable
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    // ========================================================================
    // 2. AUTO-SELECCIÓN DE ENRUTAMIENTO
    // ========================================================================
    // Si no hay un tópico seleccionado pero la red detecta nodos compatibles,
    // se auto-asigna el primero por comodidad operativa.
    LaunchedEffect(teleopTopics) {
        if (topicText.isBlank() && teleopTopics.isNotEmpty()) {
            controlViewModel.onTopicChange(teleopTopics.first())
        }
    }

    Column(
        Modifier.fillMaxSize().padding(if (isCompact) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {

            // ========================================================================
            // 3. CABECERA: ENRUTAMIENTO Y SEGURIDAD (Se oculta en modo compacto)
            // ========================================================================
            if (!isCompact) {
                // Selector de Tópico (Dropdown)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    // LÓGICA DE SEGURIDAD: Bloquea el despliegue si el robot está armado o procesando
                    onExpandedChange = { if (!isTeleopActive && !isLoading && teleopTopics.isNotEmpty()) expanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = if (teleopTopics.isEmpty()) "Ningún topic detectado" else topicText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Topic de Teleoperación") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        // Deshabilita visualmente el campo si los motores están armados
                        enabled = !isTeleopActive && !isLoading,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ).fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        teleopTopics.forEach { topic ->
                            DropdownMenuItem(
                                text = { Text(topic) },
                                onClick = {
                                    controlViewModel.onTopicChange(topic)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // ========================================================================
            // 4. INTERRUPTOR DE ARMADO
            // ========================================================================
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isTeleopActive) "Motores Armados" else "Motores Bloqueados",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
                Spacer(Modifier.width(14.dp))

                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = isTeleopActive,
                        onCheckedChange = { controlViewModel.toggleTeleop(it) },
                        // Solo permite armar si hay una ruta válida seleccionada
                        enabled = topicText.isNotBlank(),
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = cs.primary,
                            checkedThumbColor = Color.White,
                            checkedBorderColor = cs.primary,
                            uncheckedTrackColor = Color.Gray,
                            uncheckedThumbColor = cs.surface,
                            uncheckedBorderColor = Color.Gray
                        )
                    )
                }
            }
        }

        // ========================================================================
        // 5. ENTRADA DE CONTROL: JOYSTICK
        // ========================================================================
        JoystickComponent(
            // Adaptación ergonómica según el espacio en pantalla
            size = if (isCompact) 180.dp else 240.dp,
            // El joystick es ignorable físicamente si no está armado
            isEnabled = isTeleopActive,
            // Callback de inyección cinemática (Velocidad lineal 'v' y angular 'w')
            onVelocityChanged = { v, w -> controlViewModel.updateJoystick(v, w) }
        )

        // ========================================================================
        // 6. TELEMETRÍA EN VIVO
        // ========================================================================
        if (!isCompact) {
            // Lectura monoespaciada para evitar saltos visuales al cambiar los dígitos.
            // Garantiza que muestre 0.00 exacto si el sistema está bloqueado, por seguridad.
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

/**
 * @brief Formatea la lectura de velocidad evitando mostrar ruido y errores comunes.
 * @details Previene la aparición visual del valor "-0.00"
 * @param value Velocidad cruda en m/s o rad/s.
 * @return Cadena formateada a dos decimales lista para renderizar.
 */
private fun formatVel(value: Float): String {
    val v = if (value == 0f || (value > -0.005f && value < 0.005f)) 0f else value
    return String.format("%.2f", v)
}