/**
 * @file InvestigationScreen.kt
 * @brief Pantalla de exploración del grafo de ROS 2.
 * @details Proporciona una interfaz para descubrir y filtrar dinámicamente los recursos
 *          (Topics, Services, Actions) expuestos por el robot. Implementa un motor
 *          de búsqueda local y reciclaje de vistas para manejar grandes volúmenes de datos.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- IMPORTS DE LA LÓGICA Y CONSTANTES ---
import com.enrique.r2pilot.ui.logic.InvestigationViewModel
import com.enrique.r2pilot.ui.theme.MonoData
import com.enrique.r2pilot.ui.theme.MonoLabel
import com.enrique.r2pilot.utils.AppConstants

/**
 * @brief Renderiza la vista del explorador de red ROS 2.
 * @details Coordina la interacción entre la selección de categorías, la solicitud de escaneo
 *          a la red y el filtrado reactivo de los resultados.
 * @param viewModel Instancia de [InvestigationViewModel] que expone los flujos de datos
 *                  filtrados y gestiona las peticiones de descubrimiento al backend.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestigationScreen(viewModel: InvestigationViewModel) {
    // ========================================================================
    // OBSERVADORES DE ESTADO (Flujo Unidireccional de Datos - UDF)
    // ========================================================================
    val selectedResource by viewModel.selectedResource.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val filteredList by viewModel.filteredList.collectAsState()
    val isLoading by viewModel.isLoadingLocal.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ========================================================================
        // 1. SELECTOR DE CATEGORÍA
        // ========================================================================
        // Permite al usuario conmutar entre los tres pilares de comunicación de ROS 2.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilterChip(
                selected = selectedResource == AppConstants.Resource.TOPICS,
                onClick = { viewModel.selectResource(AppConstants.Resource.TOPICS) },
                label = { Text("Topics") }
            )
            FilterChip(
                selected = selectedResource == AppConstants.Resource.SERVICES,
                onClick = { viewModel.selectResource(AppConstants.Resource.SERVICES) },
                label = { Text("Services") }
            )
            FilterChip(
                selected = selectedResource == AppConstants.Resource.ACTIONS,
                onClick = { viewModel.selectResource(AppConstants.Resource.ACTIONS) },
                label = { Text("Actions") }
            )
        }

        // ========================================================================
        // 2. DISPARADOR DE RED
        // ========================================================================
        // El botón se bloquea automáticamente (enabled = !isLoading) y muestra feedback visual
        // durante las peticiones para evitar que el usuario lance peticiones superpuestas
        // que puedan colapsar el puente WebSocket o el nodo puente.
        Button(
            onClick = { viewModel.fetchNetworkInfo() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Obteniendo datos...", color = Color.White, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Listar", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                // Generación dinámica del texto según el contexto actual
                Text(
                    "Listar ${selectedResource.replaceFirstChar { it.uppercase() }}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ========================================================================
        // 3. BARRA DE BÚSQUEDA REACTIVA (Buscador Local)
        // ========================================================================
        // Realiza filtrado en caliente sobre la lista alojada en memoria.
        // Se desactiva inteligentemente si no hay datos base para buscar.
        OutlinedTextField(
            value = searchText,
            onValueChange = { viewModel.updateSearchText(it) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            placeholder = {
                Text(
                    "Buscar (ej: pose, twist)…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = MaterialTheme.shapes.medium,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            enabled = filteredList.isNotEmpty() || searchText.isNotEmpty()
        )

        // ========================================================================
        // 4. LISTA DE RESULTADOS
        // ========================================================================
        Box(modifier = Modifier.fillMaxSize()) {
            if (filteredList.isEmpty()) {
                // Manejo de estado vacío para orientar al usuario
                Text(
                    text = if (searchText.isNotBlank()) "No hay coincidencias para '$searchText'"
                    else "Pulsa 'Listar' para obtener los datos actuales del robot.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Empleo de LazyColumn: Solo instancia en RAM las tarjetas visibles en pantalla,
                // logrando mantener 60 FPS estables incluso si ROS devuelve +5000 tópicos.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredList) { item ->
                        RosNodeCard(name = item.name, types = item.types)
                    }
                }
            }
        }
    }
}

/**
 * @brief Componente visual independiente para presentar un nodo/tópico de ROS.
 * @details Muestra la firma de la interfaz (Nombre y Tipos de Mensajes asociados)
 *          con un estilo monoespaciado tipo terminal para facilitar la lectura técnica.
 * @param name Ruta del recurso en el grafo de ROS (ej. "/cmd_vel").
 * @param types Lista de tipos de mensaje soportados (ej. ["geometry_msgs/msg/Twist"]).
 */
@Composable
fun RosNodeCard(name: String, types: List<String>) {
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = cs.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Identificador principal
            Text(
                text = name,
                style = MonoData.copy(fontSize = 15.sp),
                color = cs.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Iteración de los tipos de datos soportados por el tópico/servicio
            types.forEach { type ->
                Row {
                    Text(
                        text = "Tipo: ",
                        style = MonoLabel,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = type,
                        style = MonoLabel,
                        color = cs.primary
                    )
                }
            }
        }
    }
}