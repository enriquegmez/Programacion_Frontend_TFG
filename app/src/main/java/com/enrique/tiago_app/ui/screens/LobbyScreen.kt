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
import com.enrique.tiago_app.ui.components.DangerButton
import com.enrique.tiago_app.ui.components.PrimaryActionButton
import com.enrique.tiago_app.ui.components.SectionTitle
import com.enrique.tiago_app.ui.components.SteelCard
import com.enrique.tiago_app.ui.logic.LobbyViewModel
import com.enrique.tiago_app.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(viewModel: LobbyViewModel) {
    val globalState by viewModel.globalState.collectAsState()
    val telemetry by viewModel.hostTelemetry.collectAsState()

    val isBusy = globalState.startsWith("ESPERANDO_")
    val isLoggingIn = globalState == AppConstants.GlobalState.ESPERANDO_INICIO_SESION
    val isSavingNetwork = globalState == AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA

    // Estados para los pop-ups y formularios
    var showRebootDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }
    var showNetworkSavedDialog by remember { mutableStateOf(false) }

    // Estados del formulario de Red
    var editedDomainId by remember { mutableStateOf("") }
    var editedDds by remember { mutableStateOf("") }
    var editedUseDiscovery by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // ¡MAGIA! Nada más entrar a la pantalla, pedimos la telemetría a Python
    LaunchedEffect(Unit) {
        viewModel.fetchTelemetry()
    }

    // Cuando llegan los datos, actualizamos el formulario por defecto
    LaunchedEffect(telemetry) {
        if (telemetry != null && editedDomainId.isEmpty()) {
            editedDomainId = telemetry!!.rosDomainId ?: ""
            editedDds = telemetry!!.currentDds ?: ""
            editedUseDiscovery = telemetry!!.useDiscovery ?: false
        }
    }

    // --- POP-UP: Confirmar Reinicio (General o tras guardar red) ---
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- CABECERA ---
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

                // Desplegable de DDS
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
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
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

                val hasChanges = editedDomainId != t.rosDomainId ||
                        editedDds != t.currentDds ||
                        editedUseDiscovery != t.useDiscovery

                PrimaryActionButton(
                    text = "Guardar y Aplicar",
                    onClick = {
                        viewModel.saveNetworkConfig(editedDomainId, editedDds, editedUseDiscovery)
                        showNetworkSavedDialog = true
                    },
                    enabled = hasChanges && !isBusy,
                    icon = Icons.Default.Save,
                    loading = isSavingNetwork,
                    onContainer = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TARJETA: ENERGÍA ---
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

        // --- BOTÓN GIGANTE: ENTRAR A ROS 2 ---
        PrimaryActionButton(
            text = "CONECTAR AL ROBOT",
            onClick = { viewModel.connectToRobot() },
            enabled = !isBusy && telemetry != null,
            loading = isLoggingIn,
            container = MaterialTheme.colorScheme.tertiary,
            onContainer = MaterialTheme.colorScheme.onTertiary
        )

        Spacer(modifier = Modifier.height(12.dp))

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