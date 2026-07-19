/**
 * @file ActionsScreen.kt
 * @brief Pantalla para la ejecución de animaciones y movimientos pregrabados del robot.
 * @details Implementa una interfaz de dos caras: un menú de selección de acciones
 *          (cuando está en reposo) y un panel de telemetría/progreso (cuando el robot se está moviendo).
 *          Soporta un modo "Compacto" para incrustarse en otras vistas.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign

// --- IMPORTS DE LA LÓGICA Y CONSTANTES ---
import com.enrique.r2pilot.ui.logic.PlayMotionViewModel
import com.enrique.r2pilot.utils.AppConstants

/**
 * @brief Renderiza la vista de ejecución de movimientos predefinidos (Play Motion).
 * @details Muestra una lista interactiva de animaciones disponibles en estado de reposo,
 *          y un panel de ejecución y parada de emergencia cuando hay una acción en progreso.
 *          Se adapta dinámicamente al espacio disponible mediante el parámetro [isCompact].
 * @param viewModel Instancia de [PlayMotionViewModel] que inyecta los flujos de estado
 *                  y procesa las intenciones del usuario.
 * @param isCompact Indica si la vista se está renderizando en un contenedor reducido
 *                  (pantalla dividida). Si es true, reduce paddings
 *                  y oculta textos secundarios para maximizar el área útil.
 */
@Composable
fun PlayMotionScreen(
    viewModel: PlayMotionViewModel,
    isCompact: Boolean = false // Modo adaptativo: true reduce márgenes y oculta textos secundarios
) {
    // ========================================================================
    // OBSERVADORES DE ESTADO
    // ========================================================================
    val availableActions by viewModel.availableActions.collectAsState()
    val movementState by viewModel.movementState.collectAsState()
    val selectedAction by viewModel.selectedAction.collectAsState()
    val currentFeedback by viewModel.currentFeedback.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Padding dinámico: menor espacio si está el modo dividido
            .padding(if (isCompact) 4.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ========================================================================
        // 1. CABECERA INFORMATIVA
        // ========================================================================
        // Se oculta en el modo compacto para maximizar el espacio útil para los botones.
        if (!isCompact) {
            Text(
                text = "Movimientos Predefinidos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Selecciona una acción y el robot la ejecutará de forma autónoma.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ========================================================================
        // 2. ENRUTADOR VISUAL DE ESTADOS
        // ========================================================================
        // Evaluamos si el semáforo global está bloqueado por una acción en curso
        val isActionState = movementState == AppConstants.MovementState.ESPERANDO_EJECUTAR_ACCION ||
                movementState == AppConstants.MovementState.ESPERANDO_DETENER_ACCION

        if (!isActionState) {
            // --------------------------------------------------------------------
            // VISTA A: REPOSO (LISTADO DE MOVIMIENTOS)
            // --------------------------------------------------------------------

            // Botón superior de escaneo (Solo visible en pantalla completa)
            if (!isCompact) {
                Button(
                    onClick = { viewModel.fetchAvailableActions() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Obtener Movimientos del Robot")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Área central: Lista o Mensaje de estado vacío
            if (availableActions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isCompact) "Sal de la pantalla dividida para escanear movimientos."
                        else "No hay movimientos cargados.\nPulsa el botón superior para buscarlos.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Lista scrolleable de acciones disponibles
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableActions) { actionName ->
                        val isSelected = (actionName == selectedAction)
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.selectAction(actionName) },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isSelected) Color.LightGray else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = actionName.uppercase(),
                                modifier = Modifier.padding(if (isCompact) 12.dp else 16.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 8.dp else 16.dp))

            // Botón inferior: Ejecutar (Deshabilitado si no hay selección)
            Button(
                onClick = { viewModel.executeSelectedAction() },
                enabled = selectedAction != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50), // Verde de confirmación
                    disabledContainerColor = Color.LightGray,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isCompact) 45.dp else 50.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Ejecutar")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ejecutar Movimiento")
            }

        } else {
            // --------------------------------------------------------------------
            // VISTA B: EJECUCIÓN (MONITORIZACIÓN Y PARADA DE EMERGENCIA)
            // --------------------------------------------------------------------
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(if (isCompact) 4.dp else 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(if (isCompact) 16.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ejecutando: ${selectedAction?.uppercase()}",
                            style = if (isCompact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Feedback textual (ej. "Moviendo brazo izquierdo...")
                        Text(
                            text = currentFeedback?.details ?: "Enviando orden al robot...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 24.dp))

                        // Feedback visual (Barra de progreso del action server)
                        val progressValue = (currentFeedback?.progress ?: 0) / 100f
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${currentFeedback?.progress ?: 0}%")

                        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 32.dp))

                        // Botón destructivo: Parada de emergencia
                        // Se bloquea automáticamente al pulsarlo una vez para evitar saturar el canal (Spam).
                        val isStopping = movementState == AppConstants.MovementState.ESPERANDO_DETENER_ACCION
                        Button(
                            onClick = { viewModel.stopCurrentAction() },
                            enabled = !isStopping,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                disabledContainerColor = Color.Gray,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompact) 45.dp else 50.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Detener")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isStopping) "Deteniendo..." else "Detener Acción")
                        }
                    }
                }
            }
        }
    }
}