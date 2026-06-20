package com.enrique.tiago_app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Importamos tu ViewModel y Constantes
import com.enrique.tiago_app.ui.logic.InvestigationViewModel
import com.enrique.tiago_app.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestigationScreen(viewModel: InvestigationViewModel) {
    // 1. Observamos los estados del ViewModel
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
        // --- SECCIÓN 1: PESTAÑAS DE SELECCIÓN ---
        Text(
            text = "Explorador de Nodos ROS 2",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        // Usamos una fila de botones tipo "Chip" para elegir qué queremos ver
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

        // --- SECCIÓN 2: BOTÓN DE PETICIÓN ---
        Button(
            onClick = { viewModel.fetchNetworkInfo() },
            modifier = Modifier.fillMaxWidth(),
            // Bloqueamos el botón si ya estamos cargando datos
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Obteniendo datos...")
            } else {
                Icon(Icons.Default.Refresh, contentDescription = "Listar")
                Spacer(modifier = Modifier.width(8.dp))
                // El texto del botón cambia dinámicamente según lo seleccionado
                Text("Listar ${selectedResource.replaceFirstChar { it.uppercase() }}")
            }
        }

        // --- SECCIÓN 3: BARRA DE BÚSQUEDA ---
        OutlinedTextField(
            value = searchText,
            onValueChange = { viewModel.updateSearchText(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nombre o tipo (ej: pose, twist)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
            singleLine = true,
            // Habilitamos la barra solo si hay datos en la lista para buscar
            enabled = filteredList.isNotEmpty() || searchText.isNotEmpty()
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // --- SECCIÓN 4: LISTA DE RESULTADOS ---
        Box(modifier = Modifier.fillMaxSize()) {
            if (filteredList.isEmpty()) {
                // Mensaje cuando la lista está vacía
                Text(
                    text = if (searchText.isNotBlank()) "No hay coincidencias para '$searchText'"
                    else "Pulsa 'Listar' para obtener los datos actuales del robot.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Renderizado eficiente de la lista usando LazyColumn
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

// Componente visual independiente para cada fila de la lista
@Composable
fun RosNodeCard(name: String, types: List<String>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Nombre del Topic/Service/Action (Grande)
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Lista de tipos debajo (Pequeño)
            types.forEach { type ->
                Text(
                    text = "Tipo: $type",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}