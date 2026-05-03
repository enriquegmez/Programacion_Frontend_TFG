package com.enrique.tiago_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

// Importamos todas nuestras piezas
import com.enrique.tiago_app.protocol.MessageCodec
import com.enrique.tiago_app.communication.WebSocketClient
import com.enrique.tiago_app.logic.RobotRepository
import com.enrique.tiago_app.logic.SessionManager
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.ui.logic.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ARRANCAMOS EL MOTOR (Inyección de dependencias manual)
        val webSocketClient = WebSocketClient()
        val messageManager = MessageCodec()
        val sessionManager = SessionManager()
        val robotRepository = RobotRepository(webSocketClient, messageManager, sessionManager)

        // 2. CREAMOS EL FACTORY DEL VIEWMODEL
        val factory = MainViewModelFactory(robotRepository)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 3. PINTAMOS LA PANTALLA
                    MainScreen(factory)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModelFactory: MainViewModelFactory) {
    // Obtenemos nuestro ViewModel
    val viewModel: MainViewModel = viewModel(factory = viewModelFactory)

    // Observamos en tiempo real lo que nos dice el Repositorio
    val status by viewModel.connectionStatus.collectAsState()
    val logs by viewModel.lastLogs.collectAsState()

    // DISEÑO VISUAL
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "TIAGO Controller", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Muestra el Estado
        Text(text = "Estado: $status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(16.dp))

        // Botones
        Row {
            Button(onClick = { viewModel.connect() }) {
                Text("Conectar")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { viewModel.disconnect() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Desconectar")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Consola de eventos
        Text(text = "Consola de Red:", style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Ocupa el resto de la pantalla hacia abajo
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = logs,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}