package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// IMPORTS DE TU ARQUITECTURA
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.utils.AppConstants

/**
 * MenuScreen (Pantalla 2 - Hall de Entrada)
 * El túnel WebSocket está abierto, pero la sesión lógica con el robot no ha iniciado.
 */
@Composable
fun MenuScreen(viewModel: MainViewModel) {
    // 1. Nos suscribimos al estado del cerebro
    val globalState by viewModel.globalState.collectAsState()

    // 2. Variables derivadas para saber si la UI debe mostrar "cargando"
    val isLoggingIn = (globalState == AppConstants.GlobalState.ESPERANDO_INICIO_SESION)
    val isClosing = (globalState == AppConstants.GlobalState.ESPERANDO_DESCONEXION_BACKEND)

    // Si alguna de las dos acciones está en proceso, bloqueamos la pantalla
    val isBusy = isLoggingIn || isClosing

    // 3. UI: Contenedor vertical centrado
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // --- CABECERA DE ÉXITO DE RED ---
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Conexión OK",
            tint = Color(0xFF4CAF50), // Un verde bonito de material design
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "WebSocket Conectado",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "¿Qué deseas hacer ahora?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // --- BOTÓN: CONECTAR AL ROBOT ---
        Button(
            onClick = { viewModel.connectToRobot() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            enabled = !isBusy // Evitamos que pulse si ya está cargando algo
        ) {
            if (isLoggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Iniciando Sesión...")
            } else {
                Text("Conectar al Robot")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- BOTÓN: DESCONECTAR WEBSOCKET ---
        // Usamos OutlinedButton para que parezca una acción secundaria
        OutlinedButton(
            onClick = { viewModel.closeEverything() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(50.dp),
            enabled = !isBusy,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error // El texto se pone rojo
            )
        ) {
            if (isClosing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.error,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrando Túnel...")
            } else {
                Text("Cerrar Conexión")
            }
        }
    }
}