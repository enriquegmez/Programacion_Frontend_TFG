package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// IMPORTS DE TU ARQUITECTURA
import com.enrique.tiago_app.ui.components.PrimaryActionButton
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.utils.AppConstants

/**
 * LoginScreen (Pantalla 1)
 * Recoge la IP y el Puerto para establecer la conexión física (WebSocket).
 */
@Composable
fun WebsocketScreen(viewModel: MainViewModel) {
    // 1. Nos suscribimos a los datos del cerebro (ViewModel)
    val ip by viewModel.ipAddress.collectAsState()
    val port by viewModel.port.collectAsState()
    val globalState by viewModel.globalState.collectAsState()

    // Variable derivada para saber si estamos en pleno proceso de conexión
    val isConnecting = (globalState == AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND)

    // 2. UI: Contenedor vertical centrado
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Título
        Text(
            text = "Conexión Websocket",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Campo de Texto: IP
        OutlinedTextField(
            value = ip,
            onValueChange = { newValue -> viewModel.onIpChange(newValue) },
            label = { Text("Dirección IP del Robot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isConnecting // Se bloquea si está cargando
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Campo de Texto: Puerto
        OutlinedTextField(
            value = port,
            onValueChange = { newValue -> viewModel.onPortChange(newValue) },
            label = { Text("Puerto") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), // Muestra el teclado numérico
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isConnecting // Se bloquea si está cargando
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Botón de Conectar
        PrimaryActionButton(
            text = "Abrir WebSocket",
            onClick = { viewModel.connectToWebSocket() },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isConnecting, // Evita el doble click accidental
            loading = isConnecting,
            onContainer = Color.White
        )
    }
}