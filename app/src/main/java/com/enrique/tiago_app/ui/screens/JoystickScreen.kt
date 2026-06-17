package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.utils.AppConstants
import com.enrique.tiago_app.ui.components.JoystickComponent

@OptIn(ExperimentalMaterial3Api::class) // Necesario para el ExposedDropdownMenuBox
@Composable
fun JoystickView(
    controlViewModel: ControlViewModel,
    teleopTopics: List<String>, // ¡NUEVO! Recibimos la lista de topics seguros
    isCompact: Boolean = false
) {
    // 1. Observamos el estado
    val movState by controlViewModel.movementState.collectAsState()
    val topicText by controlViewModel.targetTopic.collectAsState()

    // 2. Variables derivadas
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)
    val isLoading = movState.startsWith("ESPERANDO_")

    // Estado para saber si el desplegable está abierto o cerrado
    var expanded by remember { mutableStateOf(false) }

    // ¡MAGIA! Si la lista tiene elementos pero no hemos seleccionado nada,
    // auto-seleccionamos el primero (que es el de máxima prioridad según Python)
    LaunchedEffect(teleopTopics) {
        if (topicText.isBlank() && teleopTopics.isNotEmpty()) {
            controlViewModel.onTopicChange(teleopTopics.first())
        }
    }

    // 3. UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 4.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // --- BLOQUE SUPERIOR ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            if (!isCompact) {
                Text(
                    text = "Teleoperación",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // --- EL NUEVO MENÚ DESPLEGABLE ---
            if (!isCompact) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = {
                        // Solo permitimos abrirlo si los motores están apagados y no está cargando
                        if (!isTeleopActive && !isLoading && teleopTopics.isNotEmpty()) {
                            expanded = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    OutlinedTextField(
                        // Mostrar un aviso si el robot por algún motivo no tiene topics
                        value = if (teleopTopics.isEmpty()) "Ningún topic detectado" else topicText,
                        onValueChange = {},
                        readOnly = true, // Evita que aparezca el teclado del móvil
                        label = { Text("Topic de Teleoperación") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        enabled = !isTeleopActive && !isLoading,
                        modifier = Modifier.menuAnchor() // Imprescindible para que el menú se ancle aquí
                    )

                    // La lista de opciones que cae hacia abajo
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
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
                Spacer(modifier = Modifier.height(16.dp))
            }

            // INTERRUPTOR DE MOTORES
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isTeleopActive) "Motores Armados" else "Motores Bloqueados",
                    style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(16.dp))
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Switch(
                        checked = isTeleopActive,
                        onCheckedChange = { controlViewModel.toggleTeleop(it) },
                        modifier = if (isCompact) Modifier.scale(0.8f) else Modifier,
                        // ¡NUEVO! Bloqueamos el switch si no hay topic seleccionado
                        enabled = topicText.isNotBlank()
                    )
                }
            }
        }

        // --- BLOQUE CENTRAL (Joystick) ---
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