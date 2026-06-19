package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.background
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

import com.enrique.tiago_app.ui.logic.PlayMotionViewModel
import com.enrique.tiago_app.utils.AppConstants

@Composable
fun PlayMotionScreen(viewModel: PlayMotionViewModel) {
    // 1. Observamos los estados desde el ViewModel
    val availableActions by viewModel.availableActions.collectAsState()
    val movementState by viewModel.movementState.collectAsState()
    val selectedAction by viewModel.selectedAction.collectAsState()
    val currentFeedback by viewModel.currentFeedback.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- CABECERA ---
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

        // --- LÓGICA DE PANTALLAS (Según el estado del semáforo) ---
        if (movementState == AppConstants.MovementState.IDLE) {
            // ESTADO: REPOSO (Mostrar la lista)

            Button(
                onClick = { viewModel.fetchAvailableActions() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Obtener Movimientos del Robot")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (availableActions.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay movimientos cargados.\nPulsa el botón superior para buscarlos.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                // Lista de movimientos (LazyColumn es el equivalente moderno al RecyclerView)
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
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = actionName.uppercase(),
                                modifier = Modifier.padding(16.dp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de Ejecutar
            Button(
                onClick = { viewModel.executeSelectedAction() },
                enabled = selectedAction != null, // Solo se activa si has tocado uno
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50), // Verde
                    disabledContainerColor = Color.LightGray
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Ejecutar")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ejecutar Movimiento")
            }

        } else {
            // ESTADO: EJECUTANDO ACCIÓN O ESPERANDO PARAR
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Ejecutando: ${selectedAction?.uppercase()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Estado / Detalles devueltos por ROS 2
                        Text(
                            text = currentFeedback?.details ?: "Enviando orden al robot...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Barra de progreso
                        val progressValue = (currentFeedback?.progress ?: 0) / 100f
                        LinearProgressIndicator(
                            progress = { progressValue },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${currentFeedback?.progress ?: 0}%")

                        Spacer(modifier = Modifier.height(32.dp))

                        // Botón de Detener
                        val isStopping = movementState == AppConstants.MovementState.ESPERANDO_DETENER_ACCION
                        Button(
                            onClick = { viewModel.stopCurrentAction() },
                            enabled = !isStopping, // Si ya le hemos dado, lo bloqueamos para no spamear
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                disabledContainerColor = Color.Gray
                            ),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
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