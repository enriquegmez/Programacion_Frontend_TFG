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

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreamView(
    streamViewModel: StreamViewModel,
    cameraTopics: List<String>, // ¡NUEVO! Recibimos la lista de cámaras detectadas
    isCompact: Boolean = false
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
    var topicMenuExpanded by remember { mutableStateOf(false) } // ¡NUEVO! Para el topic
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    // Opciones de los menús
    val resourceOptions = listOf("camera")
    val qualityOptions = mapOf(
        "Alta" to AppConstants.CameraQuality.HIGH,
        "Media" to AppConstants.CameraQuality.MEDIUM,
        "Baja" to AppConstants.CameraQuality.LOW
    )

    // ¡MAGIA! Auto-selección de la cámara principal por defecto
    LaunchedEffect(cameraTopics) {
        if (currentTopic.isBlank() && cameraTopics.isNotEmpty()) {
            streamViewModel.updateTopic(cameraTopics.first())
        }
    }

    // ==========================================
    // ¡NUEVO! APAGADO AUTOMÁTICO AL SALIR
    // ==========================================
    DisposableEffect(Unit) {
        onDispose {
            streamViewModel.onScreenDisposed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 4.dp else 16.dp),
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
                    readOnly = true,
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

            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 12.dp))

            // --- ¡NUEVO! DESPLEGABLE: TOPIC DE LA CÁMARA ---
            ExposedDropdownMenuBox(
                expanded = topicMenuExpanded,
                onExpandedChange = {
                    if (!isLoading && cameraTopics.isNotEmpty()) topicMenuExpanded = it
                }
            ) {
                OutlinedTextField(
                    value = if (cameraTopics.isEmpty()) "Ninguna cámara detectada" else currentTopic,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Topic de ROS 2") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = topicMenuExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    enabled = !isLoading,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = topicMenuExpanded,
                    onDismissRequest = { topicMenuExpanded = false }
                ) {
                    cameraTopics.forEach { topic ->
                        DropdownMenuItem(
                            text = { Text(topic) },
                            onClick = {
                                streamViewModel.updateTopic(topic)
                                topicMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 12.dp))

            // --- DESPLEGABLE: CALIDAD ---
            ExposedDropdownMenuBox(
                expanded = qualityMenuExpanded,
                onExpandedChange = { if (!isLoading) qualityMenuExpanded = it }
            ) {
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

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 32.dp))

            // --- BOTÓN INICIAR Y VALIDACIÓN ---
            Button(
                onClick = {
                    if (currentResource.isBlank() || currentTopic.isBlank() || currentQuality.isBlank()) {
                        streamViewModel.showValidationError("Por favor, selecciona una cámara válida antes de iniciar.")
                    } else {
                        streamViewModel.toggleStream()
                    }
                },
                enabled = !isLoading && cameraTopics.isNotEmpty(), // ¡Seguridad extra! No deja pulsar si no hay cámara
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
                        onRelease = { webView -> webView.destroy() }
                    )
                } else {
                    Text("Error: URL no disponible", modifier = Modifier.align(Alignment.Center))
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 16.dp))

            Button(
                onClick = { streamViewModel.toggleStream() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(if (isCompact) 40.dp else 50.dp)
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