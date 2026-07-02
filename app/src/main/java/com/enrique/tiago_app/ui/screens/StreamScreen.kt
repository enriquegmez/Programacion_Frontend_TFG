package com.enrique.tiago_app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val isStreaming = monitorState == AppConstants.MonitorState.RECIBIENDO_STREAM && streamUrl != null

    // 3. Variables para controlar si los menús están abiertos o cerrados
    var topicMenuExpanded by remember { mutableStateOf(false) } // ¡NUEVO! Para el topic
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    // Opciones de los menús
    val qualityOptions = mapOf(
        "Alta" to AppConstants.CameraQuality.HIGH,
        "Media" to AppConstants.CameraQuality.MEDIUM,
        "Baja" to AppConstants.CameraQuality.LOW
    )

    // El recurso siempre es "camera" (ya no hay selector). Lo fijamos por si acaso.
    LaunchedEffect(Unit) {
        if (currentResource.isBlank()) streamViewModel.updateResource("camera")
    }

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
                text = "Configuración de la Cámara",
                style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 24.dp))

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
                    Text("Ver Cámara", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

        } else {
            // ==========================================
            // MODO VISUALIZACIÓN (Sensor Encendido)
            // ==========================================
            val qualityLabel = qualityOptions.entries.find { it.value == currentQuality }?.key ?: "Media"

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = Color(0xFF14171C),   // negro/gris muy oscuro tipo visor
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(Modifier.fillMaxSize()) {
                    // El vídeo, recortado a las esquinas de la tarjeta
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
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.large),
                            onRelease = { webView -> webView.destroy() }
                        )
                    } else {
                        Text(
                            "Error: URL no disponible",
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // --- Etiqueta LIVE (arriba izquierda) ---
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "LIVE",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // --- Franja inferior: topic · calidad ---
                    Text(
                        text = "$currentTopic · $qualityLabel",
                        color = if (isCompact) Color.White else Color.DarkGray.copy(alpha = 0.75f),
                        style = com.enrique.tiago_app.ui.theme.MonoLabel,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )

                    // --- Botón STOP compacto superpuesto (solo en pantalla dividida) ---
                    if (isCompact) {
                        FilledIconButton(
                            onClick = { streamViewModel.toggleStream() },
                            enabled = !isLoading,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(40.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Stop, contentDescription = "Detener Cámara")
                            }
                        }
                    }
                }
            }

            // En modo dividido (compacto) el stop va superpuesto sobre el vídeo,
            // así que el botón grande de abajo solo se muestra a pantalla completa.
            if (!isCompact) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { streamViewModel.toggleStream() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                    } else {
                        Text("Detener Cámara", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}