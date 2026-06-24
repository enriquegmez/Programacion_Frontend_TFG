package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- CABECERA ---
        Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text("Conexión Establecida", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Sala de configuración previa", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))

        if (telemetry == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text("Leyendo sensores del PC...")
        } else {
            val t = telemetry!!

            // --- TARJETA 1: TELEMETRÍA DEL HOST ---
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Computer, contentDescription = "PC")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salud del PC (Host)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // --- CPU ---
                    val safeCpu = t.cpuPct ?: 0.0
                    Text(if (t.cpuPct != null) "Carga CPU: ${t.cpuPct}%" else "Carga CPU: Desconocida")
                    LinearProgressIndicator(
                        progress = { (safeCpu / 100).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (safeCpu > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // --- RAM ---
                    val safeRamPct = t.ramPct ?: 0.0
                    Text(if (t.ramPct != null && t.ramUsedGb != null)
                        "RAM: ${t.ramUsedGb} GB / ${t.ramTotalGb} GB (${t.ramPct}%)"
                    else "RAM: Desconocida")
                    LinearProgressIndicator(
                        progress = { (safeRamPct / 100).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (safeRamPct > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // --- TEMPERATURA ---
                    if (t.tempC != null && t.tempC > 0) {
                        Text("Temperatura: ${t.tempC} ºC", color = if (t.tempC > 75) MaterialTheme.colorScheme.error else Color.Unspecified)
                    } else {
                        Text("Temperatura: Sensor no detectado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TARJETA 2: RED ROS 2 ---
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NetworkWifi, contentDescription = "Red")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entorno de Red (${t.rosDistro ?: "Desconocido"})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editedDomainId,
                        onValueChange = { editedDomainId = it },
                        label = { Text("ROS_DOMAIN_ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                            modifier = Modifier.menuAnchor().fillMaxWidth()
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
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Checkbox(
                                checked = editedUseDiscovery,
                                onCheckedChange = { editedUseDiscovery = it }
                            )
                            Text("Habilitar FastDDS Discovery Server", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val hasChanges = editedDomainId != t.rosDomainId ||
                            editedDds != t.currentDds ||
                            editedUseDiscovery != t.useDiscovery
                    Button(
                        onClick = {
                            viewModel.saveNetworkConfig(editedDomainId, editedDds, editedUseDiscovery)
                            showNetworkSavedDialog = true
                        },
                        enabled = hasChanges && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSavingNetwork) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardar y Aplicar")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- TARJETA 3: ENERGÍA ---
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { showRebootDialog = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reiniciar")
                    }

                    Button(
                        onClick = { showShutdownDialog = true },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apagar")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- BOTÓN GIGANTE: ENTRAR A ROS 2 ---
        Button(
            onClick = { viewModel.connectToRobot() },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            enabled = !isBusy && telemetry != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Arrancando Nodos ROS 2...")
            } else {
                Text("CONECTAR AL ROBOT", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.disconnectFromServer() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isBusy
        ) {
            Text("Volver al Inicio (Desconectar)", color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}