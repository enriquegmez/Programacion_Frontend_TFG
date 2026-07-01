package com.enrique.tiago_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text

// --- IMPORTS DEL TEMA AXON (¡NUEVO!) ---
import com.enrique.tiago_app.ui.theme.AxonTheme
import com.enrique.tiago_app.ui.theme.DarkBg
import com.enrique.tiago_app.ui.theme.LightBg

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
import com.enrique.tiago_app.ui.logic.StreamViewModel
import com.enrique.tiago_app.ui.logic.PlayMotionViewModel
import com.enrique.tiago_app.ui.logic.InvestigationViewModel
import com.enrique.tiago_app.ui.logic.JointControlViewModel
import com.enrique.tiago_app.ui.logic.SensorViewModel

// --- IMPORTS DE TUS PANTALLAS (SCREENS) ---
import com.enrique.tiago_app.ui.screens.SplashScreen
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
            // ¡MAGIA! Envolvemos la app en el nuevo tema oscuro y premium
            AxonTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LightBg // Usamos el fondo obsidiana del nuevo tema
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
    val streamViewModel: StreamViewModel = viewModel {
        StreamViewModel(AppDependencies.director)
    }
    val playMotionViewModel: PlayMotionViewModel = viewModel {
        PlayMotionViewModel(AppDependencies.director)
    }
    val investigationViewModel: InvestigationViewModel = viewModel {
        InvestigationViewModel(AppDependencies.director)
    }
    val jointControlViewModel: JointControlViewModel = viewModel {
        JointControlViewModel(AppDependencies.director)
    }
    val sensorViewModel: SensorViewModel = viewModel {
        SensorViewModel(AppDependencies.director)
    }

    val globalState by mainViewModel.globalState.collectAsState()
    val systemAlert by mainViewModel.systemAlert.collectAsState()

    // Lógica de navegación reactiva
    LaunchedEffect(globalState) {
        val currentRoute = navController.currentDestination?.route

        // Mientras se muestra el splash, no redirigimos: dejamos que la
        // animación termine y sea ella quien navegue a "login".
        if (currentRoute == "splash") return@LaunchedEffect

        when (globalState) {
            AppConstants.GlobalState.IDLE -> {
                if (currentRoute != "login") {
                    navController.navigate("login") { popUpTo(0) }
                }
            }
            AppConstants.GlobalState.CONEXION_BACKEND -> {
                if (currentRoute != "conexion") {
                    navController.navigate("conexion") { popUpTo(0) }
                }
            }
            AppConstants.GlobalState.SESION_INICIADA -> {
                if (currentRoute != "menu principal") {
                    navController.navigate("menu principal") { popUpTo(0) }
                }
            }
        }
    }

    // Lógica Visual del Dialogo de Alerta
    if (systemAlert != null) {
        AlertDialog(
            onDismissRequest = { mainViewModel.clearAlert() },
            title = { Text(systemAlert!!.title) },
            text = { Text(systemAlert!!.message) },
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
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onAnimationFinished = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true } // el splash no vuelve con "atrás"
                    }
                }
            )
        }
        composable("login") { WebsocketScreen(viewModel = mainViewModel) }
        composable("conexion") { LobbyScreen(viewModel = lobbyViewModel) }
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