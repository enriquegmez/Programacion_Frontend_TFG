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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Importamos tu ViewModel y Constantes
import com.enrique.tiago_app.ui.logic.InvestigationViewModel
import com.enrique.tiago_app.ui.theme.MonoData
import com.enrique.tiago_app.ui.theme.MonoLabel
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
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.small,
            // Bloqueamos el botón si ya estamos cargando datos
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
                // El texto del botón cambia dinámicamente según lo seleccionado
                Text(
                    "Listar ${selectedResource.replaceFirstChar { it.uppercase() }}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // --- SECCIÓN 3: BARRA DE BÚSQUEDA ---
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
            // Habilitamos la barra solo si hay datos en la lista para buscar
            enabled = filteredList.isNotEmpty() || searchText.isNotEmpty()
        )

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
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = cs.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Nombre del Topic/Service/Action en monoespaciada negrita
            Text(
                text = name,
                style = MonoData.copy(fontSize = 15.sp),
                color = cs.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Tipos: "Tipo:" en gris + valor en cian, todo monoespaciado
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