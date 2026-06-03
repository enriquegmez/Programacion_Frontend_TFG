package com.enrique.tiago_app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// IMPORTS
import com.enrique.tiago_app.ui.logic.StreamViewModel
import com.enrique.tiago_app.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class) // Necesario para los DropdownMenu de Material 3
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreamView(streamViewModel: StreamViewModel,
               isCompact: Boolean = false // ¡NUEVO!)
) {

    // 1. Observamos los estados
    val monitorState by streamViewModel.monitorState.collectAsState()
    val streamUrl by streamViewModel.streamUrl.collectAsState()

    val currentResource by streamViewModel.currentResource.collectAsState()
    val currentTopic by streamViewModel.currentTopic.collectAsState()
    val currentQuality by streamViewModel.currentQuality.collectAsState()

    // 2. Variables de UI
    val isLoading = monitorState.startsWith("ESPERANDO_")
    val isStreaming = monitorState == AppConstants.MonitorState.RECIBIENDO_STREAM

    // 3. Variables para controlar si los menús están abiertos o cerrados
    var resourceMenuExpanded by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    // Opciones de los menús
    val resourceOptions = listOf("camera") // En el futuro puedes añadir "lidar", "imu", etc.

    // Mapeamos lo que lee el usuario con lo que necesita tu Constants interno
    val qualityOptions = mapOf(
        "Alta" to AppConstants.CameraQuality.HIGH,
        "Media" to AppConstants.CameraQuality.MEDIUM,
        "Baja" to AppConstants.CameraQuality.LOW
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 4.dp else 16.dp), // Menos margen
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isStreaming) {
            // ==========================================
            // MODO FORMULARIO (Sensor Apagado)
            // ==========================================
            Text(
                text = "Configuración de Sensores",
                style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 24.dp))

            // --- DESPLEGABLE: RECURSO ---
            ExposedDropdownMenuBox(
                expanded = resourceMenuExpanded,
                onExpandedChange = { if (!isLoading) resourceMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = currentResource,
                    onValueChange = {},
                    readOnly = true, // El usuario no puede escribir, solo seleccionar
                    label = { Text("Recurso a visualizar") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resourceMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = resourceMenuExpanded,
                    onDismissRequest = { resourceMenuExpanded = false }
                ) {
                    resourceOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                streamViewModel.updateResource(option)
                                resourceMenuExpanded = false
                            }
                        )
                    }
                }
            }
            // ¡MEJORA! Espacio minúsculo si es compacto
            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 12.dp))

            // --- TEXTO LIBRE: TOPIC ---
            OutlinedTextField(
                value = currentTopic,
                onValueChange = { streamViewModel.updateTopic(it) },
                label = { Text("Topic de ROS 2") },
                placeholder = { Text("Ej: /head_front_camera/rgb/image_raw") },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // --- DESPLEGABLE: CALIDAD ---
            ExposedDropdownMenuBox(
                expanded = qualityMenuExpanded,
                onExpandedChange = { if (!isLoading) qualityMenuExpanded = it }
            ) {
                // Buscamos qué texto mostrar dependiendo del valor guardado en el ViewModel
                val qualityTextUI = qualityOptions.entries.find { it.value == currentQuality }?.key ?: "Media"

                OutlinedTextField(
                    value = qualityTextUI,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Calidad de vídeo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = qualityMenuExpanded,
                    onDismissRequest = { qualityMenuExpanded = false }
                ) {
                    qualityOptions.forEach { (uiText, constantValue) ->
                        DropdownMenuItem(
                            text = { Text(uiText) },
                            onClick = {
                                streamViewModel.updateQuality(constantValue)
                                qualityMenuExpanded = false
                            }
                        )
                    }
                }
            }
            // ¡MEJORA! Ahorramos mucho espacio antes del botón
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 32.dp))

            // --- BOTÓN INICIAR Y VALIDACIÓN ---
            Button(
                onClick = {
                    // 1. VALIDACIÓN LOCAL: Comprobamos que no haya borrado el topic
                    if (currentResource.isBlank() || currentTopic.isBlank() || currentQuality.isBlank()) {
                        streamViewModel.showValidationError("Por favor, rellena todos los campos antes de ver el sensor.")
                    } else {
                        // 2. Si todo está bien, mandamos la orden al servidor
                        streamViewModel.toggleStream()
                    }
                },
                enabled = !isLoading,
                // ¡MEJORA! Botón un pelín más fino en modo compacto
                modifier = Modifier.fillMaxWidth().height(if (isCompact) 45.dp else 50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Ver Sensor")
                }
            }

        } else {
            // ==========================================
            // MODO VISUALIZACIÓN (Sensor Encendido)
            // ==========================================
            // ¡NUEVO! Ocultamos el título si estamos en pantalla dividida para ahorrar espacio
            if (!isCompact) {
                Text(
                    text = "Visualizando: $currentResource",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (streamUrl != null) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                loadUrl(streamUrl!!)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { webView -> webView.destroy() } // <-- Esto libera toda la RAM
                    )
                } else {
                    Text("Error: URL no disponible", modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 16.dp))

            Button(
                onClick = { streamViewModel.toggleStream() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(if (isCompact) 40.dp else 50.dp) // Botón más fino
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                } else {
                    Text("Detener Sensor")
                }
            }
        }
    }
}