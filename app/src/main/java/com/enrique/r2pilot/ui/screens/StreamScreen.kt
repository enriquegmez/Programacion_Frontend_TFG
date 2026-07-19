/**
 * @file StreamScreen.kt
 * @brief Visor de vídeo en tiempo real y configuración de cámara.
 * @details Proporciona una interfaz para seleccionar un tópico de imagen de ROS 2,
 *          configurar la resolución del streaming y renderizar la transmisión en vivo.
 *          Implementa interoperabilidad con vistas nativas de Android (WebView) para
 *          decodificar el flujo de vídeo de forma eficiente, y gestiona de manera estricta
 *          el ciclo de vida para evitar congestiones en la red cuando la vista se destruye.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.screens

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

// --- IMPORTS DE LÓGICA DE NEGOCIO ---
import com.enrique.r2pilot.ui.logic.StreamViewModel
import com.enrique.r2pilot.utils.AppConstants

/**
 * @brief Componente principal de visualización de cámaras del robot.
 * @details Posee dos estados visuales principales: "Formulario" (cuando el stream
 *          está apagado y se requiere configuración) y "Visor" (cuando el stream
 *          está activo y se renderiza el WebView).
 *
 * @param streamViewModel ViewModel encargado de gestionar las peticiones al backend para iniciar/detener el flujo de vídeo.
 * @param cameraTopics Lista inmutable de tópicos de imagen descubiertos dinámicamente en el hardware del robot.
 * @param isCompact Bandera booleana que adapta la densidad de la interfaz cuando se renderiza en modo pantalla dividida (Split-Screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreamView(
    streamViewModel: StreamViewModel,
    cameraTopics: List<String>,
    isCompact: Boolean = false
) {
    // ========================================================================
    // 1. OBSERVADORES DE ESTADO
    // ========================================================================
    val monitorState by streamViewModel.monitorState.collectAsState()
    val streamUrl by streamViewModel.streamUrl.collectAsState()

    val currentResource by streamViewModel.currentResource.collectAsState()
    val currentTopic by streamViewModel.currentTopic.collectAsState()
    val currentQuality by streamViewModel.currentQuality.collectAsState()

    // ========================================================================
    // 2. VARIABLES DE UI COMPUTADAS
    // ========================================================================
    val isLoading = monitorState.startsWith("ESPERANDO_")
    val isStreaming = monitorState == AppConstants.MonitorState.RECIBIENDO_STREAM && streamUrl != null

    // Variables de estado local para el control de la expansión de los desplegables
    var topicMenuExpanded by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    // Diccionario de mapeo entre la interfaz de usuario y las constantes del backend
    val qualityOptions = mapOf(
        "Alta" to AppConstants.CameraQuality.HIGH,
        "Media" to AppConstants.CameraQuality.MEDIUM,
        "Baja" to AppConstants.CameraQuality.LOW
    )

    // ========================================================================
    // 3. EFECTOS DE CICLO DE VIDA
    // ========================================================================

    // Inicialización de seguridad: El recurso siempre debe apuntar a la cámara.
    LaunchedEffect(Unit) {
        if (currentResource.isBlank()) streamViewModel.updateResource("CAMERA")
    }

    // Autocompletado inteligente: Si se detectan cámaras en la red, se preselecciona
    // la primera automáticamente para agilizar el flujo de trabajo del usuario.
    LaunchedEffect(cameraTopics) {
        if (currentTopic.isBlank() && cameraTopics.isNotEmpty()) {
            streamViewModel.updateTopic(cameraTopics.first())
        }
    }

    // Prevención de cuellos de botella de red: Cuando la pantalla desaparece
    // (el Composable se destruye), se emite inmediatamente una señal al backend
    // para detener la suscripción al tópico de vídeo.
    DisposableEffect(Unit) {
        onDispose {
            streamViewModel.onScreenDisposed()
        }
    }

    // ========================================================================
    // 4. ESTRUCTURA DE RENDERIZADO
    // ========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isCompact) 4.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isStreaming) {
            // ------------------------------------------------------------------------
            // MODO FORMULARIO (Sensor Apagado)
            // ------------------------------------------------------------------------
            Text(
                text = "Configuración de la Cámara",
                style = if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 24.dp))

            // --- DESPLEGABLE: TOPIC DE LA CÁMARA ---
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

            // --- DESPLEGABLE: CALIDAD DE TRANSMISIÓN ---
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

            // --- BOTÓN INICIAR Y VALIDACIÓN DEFENSIVA ---
            Button(
                onClick = {
                    if (currentResource.isBlank() || currentTopic.isBlank() || currentQuality.isBlank()) {
                        streamViewModel.showValidationError("Por favor, selecciona una cámara válida antes de iniciar.")
                    } else {
                        streamViewModel.toggleStream()
                    }
                },
                // Seguridad extra: No deja pulsar si no hay cámara
                enabled = !isLoading && cameraTopics.isNotEmpty(),
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
            // ------------------------------------------------------------------------
            // MODO VISUALIZACIÓN (Sensor Encendido)
            // ------------------------------------------------------------------------
            val qualityLabel = qualityOptions.entries.find { it.value == currentQuality }?.key ?: "Media"

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = Color(0xFF14171C),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(Modifier.fillMaxSize()) {

                    // --- NÚCLEO NATIVO DE ANDROID: WebView Interop ---
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
                            // Previene Memory Leaks limpiando el recurso de Android nativo al destruirse el Composable
                            onRelease = { webView -> webView.destroy() }
                        )
                    } else {
                        Text(
                            "Error: URL no disponible",
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // --- Etiqueta Semántica OSD (On-Screen Display): LIVE ---
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

                    // --- Información del origen de datos en el visor ---
                    Text(
                        text = "$currentTopic · $qualityLabel",
                        color = if (isCompact) Color.White else Color.DarkGray.copy(alpha = 0.75f),
                        style = com.enrique.r2pilot.ui.theme.MonoLabel,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )

                    // --- MODO MULTITAREA: Botón superpuesto al visor ---
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

            // --- MODO PANTALLA COMPLETA: Botón estándar en la parte inferior ---
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