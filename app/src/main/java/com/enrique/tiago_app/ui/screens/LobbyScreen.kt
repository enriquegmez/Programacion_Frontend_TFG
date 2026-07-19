/**
 * @file LobbyScreen.kt
 * @brief Sala de espera y configuración previa.
 * @details Pantalla intermedia entre la conexión WebSocket y la inmersión en la teleoperación.
 *          Permite configurar el middleware de ROS 2 (Domain ID, DDS, Discovery Server)
 *          y gestionar el estado energético del host remoto (Apagar/Reiniciar) antes de
 *          saturar la red con suscripciones de tópicos.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- IMPORTS DE COMPONENTES Y LÓGICA ---
import com.enrique.tiago_app.ui.components.DangerButton
import com.enrique.tiago_app.ui.components.PrimaryActionButton
import com.enrique.tiago_app.ui.components.SectionTitle
import com.enrique.tiago_app.ui.components.SteelCard
import com.enrique.tiago_app.ui.logic.LobbyViewModel
import com.enrique.tiago_app.utils.AppConstants

/**
 * @brief Interfaz principal del Lobby de configuración.
 * @details Coordina la obtención de telemetría del host físico, la validación de
 *          formularios de red y la inyección de comandos de sistema operativo (Syscalls).
 * @param viewModel Instancia de [LobbyViewModel] encargada de la lógica de negocio y comunicación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(viewModel: LobbyViewModel) {
    // ========================================================================
    // 1. ESTADO GLOBAL Y TELEMETRÍA
    // ========================================================================
    val globalState by viewModel.globalState.collectAsState()
    val telemetry by viewModel.hostTelemetry.collectAsState()

    val isBusy = globalState.startsWith("ESPERANDO_")
    val isLoggingIn = globalState == AppConstants.GlobalState.ESPERANDO_INICIO_SESION
    val isSavingNetwork = globalState == AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA

    // ========================================================================
    // 2. ESTADOS LOCALES
    // ========================================================================
    // Banderas para cuadros de diálogo de confirmación (Seguridad Activa)
    var showRebootDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    var showNetworkSavedDialog by remember { mutableStateOf(false) }

    // Memoria del formulario de configuración de Red ROS 2
    var editedDomainId by remember { mutableStateOf("") }
    var editedDds by remember { mutableStateOf("") }
    var editedUseDiscovery by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // ========================================================================
    // 3. EFECTOS DE CICLO DE VIDA
    // ========================================================================
    // Al entrar a la pantalla, se lanza una petición asíncrona para obtener el estado del host.
    LaunchedEffect(Unit) {
        viewModel.fetchTelemetry()
    }

    // Auto-completado del formulario: Cuando llegan los datos de telemetría,
    // volcamos los valores actuales del robot en el formulario editable.
    LaunchedEffect(telemetry) {
        if (telemetry != null && editedDomainId.isEmpty()) {
            editedDomainId = telemetry!!.rosDomainId ?: ""
            editedDds = telemetry!!.currentDds ?: ""
            editedUseDiscovery = telemetry!!.useDiscovery ?: false
        }
    }

    // ========================================================================
    // 4. LÓGICA DEFENSIVA: CUADROS DE DIÁLOGO MODALES
    // ========================================================================
    // --- POP-UP: Confirmar Reinicio ---
    if (showRebootDialog || showNetworkSavedDialog) {
        AlertDialog(
            onDismissRequest = {
                showRebootDialog = false
                showNetworkSavedDialog = false
            },
            title = { Text(if (showNetworkSavedDialog) "Cambios Guardados" else "Reiniciar Robot") },
            text = {
                Text(if (showNetworkSavedDialog)
                    "La red ROS 2 se ha actualizado. Es OBLIGATORIO reiniciar el ordenador del robot para que los nodos físicos adopten la nueva configuración.\n\n¿Deseas reiniciar ahora?"
                else
                    "¿Estás seguro de que deseas reiniciar el sistema operativo del robot? Se perderá la conexión."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rebootRobot()
                        showRebootDialog = false
                        showNetworkSavedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reiniciar Ahora")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRebootDialog = false
                    showNetworkSavedDialog = false
                }) {
                    Text("Más tarde")
                }
            }
        )
    }

    // --- POP-UP: Confirmar Apagado ---
    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text("Apagar Robot") },
            text = { Text("¿Estás seguro de que deseas APAGAR el sistema operativo del robot? Tendrás que encenderlo físicamente la próxima vez.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.shutdownRobot()
                        showShutdownDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Apagar Completamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // ========================================================================
    // 5. CONSTRUCCIÓN DE LA INTERFAZ
    // ========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- CABECERA DE ÉXITO DE CONEXIÓN ---
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "OK",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Enlace Establecido",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            "Sala de configuración previa",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Si la telemetría aún no ha llegado, mostramos carga
        if (telemetry == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Leyendo configuración del robot...")
        } else {
            val t = telemetry!!

            // --- TARJETA: ENTORNO DE RED ROS 2 ---
            SteelCard {
                SectionTitle("Entorno de Red (${t.rosDistro ?: "Desconocido"})", icon = Icons.Outlined.Wifi)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = editedDomainId,
                    onValueChange = { editedDomainId = it },
                    label = { Text("ROS_DOMAIN_ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Desplegable de selección de Middleware (DDS)
                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = editedDds,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Middleware (DDS)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        modifier = Modifier.menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        ).fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    )
                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        val safeDdsList = t.availableDds ?: emptyList()

                        if (safeDdsList.isEmpty()) {
                            DropdownMenuItem(text = { Text("No se detectaron DDS") }, onClick = { })
                        } else {
                            safeDdsList.forEach { ddsOption ->
                                DropdownMenuItem(
                                    text = { Text(ddsOption) },
                                    onClick = {
                                        editedDds = ddsOption
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Renderizado condicional: Solo mostramos la opción de Discovery Server
                // si el middleware seleccionado es compatible (FastRTPS / FastDDS).
                if (editedDds.contains("fastrtps", ignoreCase = true)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Checkbox(
                            checked = editedUseDiscovery,
                            onCheckedChange = { editedUseDiscovery = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Habilitar FastDDS Discovery Server", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Evaluación de estado computado para evitar peticiones redundantes
                val hasChanges = editedDomainId != t.rosDomainId ||
                        editedDds != t.currentDds ||
                        editedUseDiscovery != t.useDiscovery

                PrimaryActionButton(
                    text = "Guardar y Aplicar",
                    onClick = {
                        viewModel.saveNetworkConfig(editedDomainId, editedDds, editedUseDiscovery)
                        showNetworkSavedDialog = true
                    },
                    // Bloqueo inteligente del botón
                    enabled = hasChanges && !isBusy,
                    icon = Icons.Default.Save,
                    loading = isSavingNetwork,
                    onContainer = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TARJETA: CONTROL DE ENERGÍA DE HOST ---
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showRebootDialog = true },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reiniciar", fontWeight = FontWeight.Bold)
                    }

                    DangerButton(
                        text = "Apagar",
                        onClick = { showShutdownDialog = true },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PowerSettingsNew,
                        enabled = !isBusy
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- BOTÓN PRINCIPAL: TRANSICIÓN A LA APP CENTRAL ---
        PrimaryActionButton(
            text = "CONECTAR AL ROBOT",
            onClick = { viewModel.connectToRobot() },
            // Evitamos la transición si no tenemos la configuración base de la máquina
            enabled = !isBusy && telemetry != null,
            loading = isLoggingIn,
            container = MaterialTheme.colorScheme.tertiary,
            onContainer = MaterialTheme.colorScheme.onTertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Opción de abortar
        OutlinedButton(
            onClick = { viewModel.disconnectFromServer() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isBusy,
            shape = MaterialTheme.shapes.small,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text("Volver al Inicio (Desconectar)", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}