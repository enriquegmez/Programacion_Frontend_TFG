package com.enrique.tiago_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.AlertDialog // ¡NUEVO IMPORT!
import androidx.compose.material3.Button // ¡NUEVO IMPORT!
import androidx.compose.material3.Text // ¡NUEVO IMPORT!

// --- IMPORTS DE TU ARQUITECTURA (PROTOCOL) ---
import com.enrique.tiago_app.protocol.MessageCodec
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.logic.ProtocolStateManager
import com.enrique.tiago_app.communication.SessionManager
import com.enrique.tiago_app.communication.WebSocketClient
import com.enrique.tiago_app.utils.AppConstants

// --- IMPORTS DE TUS VIEWMODELS ---
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.ui.logic.LobbyViewModel
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.ui.logic.StreamViewModel // ¡NUEVO IMPORT!
import com.enrique.tiago_app.ui.logic.PlayMotionViewModel
import com.enrique.tiago_app.ui.logic.InvestigationViewModel
import com.enrique.tiago_app.ui.logic.JointControlViewModel
import com.enrique.tiago_app.ui.logic.SensorViewModel

// --- IMPORTS DE TUS PANTALLAS (SCREENS) ---
import com.enrique.tiago_app.ui.screens.WebsocketScreen
import com.enrique.tiago_app.ui.screens.LobbyScreen
import com.enrique.tiago_app.ui.screens.MainScreen

/**
 * 1. CAJA FUERTE DE DEPENDENCIAS
 * Mantiene la conexión viva y compartida entre todos los ViewModels.
 */
object AppDependencies {
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    val webSocketClient = WebSocketClient()
    val codec = MessageCodec()
    val stateManager = ProtocolStateManager()
    val sessionManager = SessionManager()

    val director = ProtocolDirector(
        scope = appScope,
        webSocketClient = webSocketClient,
        codec = codec,
        stateManager = stateManager,
        sessionManager = sessionManager
    )
}

/**
 * 2. ACTIVIDAD PRINCIPAL
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

/**
 * 3. ORQUESTADOR DE NAVEGACIÓN
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Instanciamos los dos cerebros pasándoles el mismo Director
    val mainViewModel: MainViewModel = viewModel {
        MainViewModel(AppDependencies.director)
    }
    val lobbyViewModel: LobbyViewModel = viewModel {
        LobbyViewModel(AppDependencies.director)
    }
    val controlViewModel: ControlViewModel = viewModel {
        ControlViewModel(AppDependencies.director)
    }
    // ¡NUEVO! Instanciamos el cerebro de los sensores
    val streamViewModel: StreamViewModel = viewModel {
        StreamViewModel(AppDependencies.director)
    }

    // ¡NUEVO! Instanciamos el cerebro de las acciones predefinidas
    val playMotionViewModel: PlayMotionViewModel = viewModel {
        PlayMotionViewModel(AppDependencies.director)
    }

    // ¡NUEVO! Instanciamos el cerebro de la investigación
    val investigationViewModel: InvestigationViewModel = viewModel {
        InvestigationViewModel(AppDependencies.director)
    }

    val jointControlViewModel: JointControlViewModel = viewModel {
        JointControlViewModel(AppDependencies.director)
    }

    val sensorViewModel: SensorViewModel = viewModel {
        SensorViewModel(AppDependencies.director)
    }


    // Observamos el semáforo global para movernos entre pantallas
    val globalState by mainViewModel.globalState.collectAsState()

    val systemAlert by mainViewModel.systemAlert.collectAsState()

    // Lógica de navegación reactiva
    LaunchedEffect(globalState) {
        // Obtenemos la pantalla exacta que está viendo el usuario ahora mismo
        val currentRoute = navController.currentDestination?.route

        when (globalState) {
            AppConstants.GlobalState.IDLE -> {
                // Viajamos solo si NO estamos ya en "login"
                if (currentRoute != "login") {
                    navController.navigate("login") {
                        popUpTo(0) // Limpia el historial para no volver atrás
                    }
                }
            }
            AppConstants.GlobalState.CONEXION_BACKEND -> {
                if (currentRoute != "conexion") {
                    navController.navigate("conexion") {
                        popUpTo(0)
                    }
                }
            }
            AppConstants.GlobalState.SESION_INICIADA -> {
                // ¡LA MAGIA! Solo destruimos y recreamos la pantalla si venimos de otro lado.
                // Si el estado pasó a "ESPERANDO_INFO" y volvió a "SESION_INICIADA",
                // el currentRoute seguirá siendo "control", el 'if' se salta,
                // y la pantalla se queda 100% intacta.
                if (currentRoute != "menu principal") {
                    navController.navigate("menu principal") {
                        popUpTo(0)
                    }
                }
            }
            // Los estados "ESPERANDO_..." no están en el 'when',
            // así que el GPS simplemente se queda quieto y no toca la pantalla.
        }
    }

    // Lógica Visual del Dialogo de Alerta
    if (systemAlert != null) {
        AlertDialog(
            onDismissRequest = { mainViewModel.clearAlert() },
            title = { Text(systemAlert!!.title) },     // ¡NUEVO! Lee el título dinámico
            text = { Text(systemAlert!!.message) },    // ¡NUEVO! Lee el mensaje
            confirmButton = {
                Button(onClick = { mainViewModel.clearAlert() }) {
                    Text("Entendido")
                }
            }
        )
    }

    // MAPA DE RUTAS OFICIAL
    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // Pantalla 1: Login
        composable("login") {
            WebsocketScreen(viewModel = mainViewModel)
        }

        // Pantalla 2: Menú Intermedio
        composable("conexion") {
            LobbyScreen(viewModel = lobbyViewModel)
        }

        // Pantalla 3: Teleoperación con Joystick
        composable("menu principal") {
            MainScreen(
                controlViewModel = controlViewModel,
                mainViewModel = mainViewModel,
                streamViewModel = streamViewModel,
                playMotionViewModel = playMotionViewModel,
                investigationViewModel = investigationViewModel,
                jointControlViewModel = jointControlViewModel,
                sensorViewModel = sensorViewModel
            )
        }
    }
}