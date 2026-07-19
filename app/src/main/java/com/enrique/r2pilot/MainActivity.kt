/**
 * @file MainActivity.kt
 * @brief Punto de entrada principal de la aplicación Android.
 * @details Orquesta la inyección de dependencias manual, la configuración del tema visual
 *          y la navegación reactiva de Jetpack Compose basada en la máquina de estados.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot

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

// --- IMPORTS DEL TEMA ---
import com.enrique.r2pilot.ui.theme.R2PilotTheme
import com.enrique.r2pilot.ui.theme.LightBg

// --- IMPORTS DE LA ARQUITECTURA (PROTOCOL) ---
import com.enrique.r2pilot.protocol.MessageCodec
import com.enrique.r2pilot.core.ProtocolDirector
import com.enrique.r2pilot.core.ProtocolStateManager
import com.enrique.r2pilot.communication.WebSocketClient
import com.enrique.r2pilot.communication.SessionManager
import com.enrique.r2pilot.utils.AppConstants

// --- IMPORTS DE VIEWMODELS ---
import com.enrique.r2pilot.ui.logic.MainViewModel
import com.enrique.r2pilot.ui.logic.LobbyViewModel
import com.enrique.r2pilot.ui.logic.ControlViewModel
import com.enrique.r2pilot.ui.logic.StreamViewModel
import com.enrique.r2pilot.ui.logic.PlayMotionViewModel
import com.enrique.r2pilot.ui.logic.InvestigationViewModel
import com.enrique.r2pilot.ui.logic.JointControlViewModel
import com.enrique.r2pilot.ui.logic.SensorViewModel

// --- IMPORTS DE PANTALLAS (SCREENS) ---
import com.enrique.r2pilot.ui.screens.SplashScreen
import com.enrique.r2pilot.ui.screens.WebsocketScreen
import com.enrique.r2pilot.ui.screens.LobbyScreen
import com.enrique.r2pilot.ui.screens.MainScreen

// ========================================================================
// 1. CAJA FUERTE DE DEPENDENCIAS (MANUAL DI CONTAINER)
// ========================================================================

/**
 * @object AppDependencies
 * @brief Contenedor Singleton para la inyección manual de dependencias (DI).
 * @details Garantiza que los componentes críticos de la capa de red y protocolo mantengan
 *          una única instancia viva durante todo el ciclo de vida de la aplicación,
 *          sobreviviendo a la rotación de pantalla o la recreación de la MainActivity.
 */
object AppDependencies {

    /**
     * @property appScope CoroutineScope global para tareas de red en segundo plano.
     * Utiliza un SupervisorJob para que el fallo de una corrutina no cancele las demás.
     */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    val webSocketClient = WebSocketClient()
    val codec = MessageCodec()
    val stateManager = ProtocolStateManager()
    val sessionManager = SessionManager()

    // El Director unifica todas las piezas anteriores
    val director = ProtocolDirector(
        scope = appScope,
        webSocketClient = webSocketClient,
        codec = codec,
        stateManager = stateManager,
        sessionManager = sessionManager
    )
}

// ========================================================================
// 2. ACTIVIDAD PRINCIPAL (ENTRY POINT)
// ========================================================================

/**
 * @class MainActivity
 * @brief Actividad raíz de la jerarquía de vistas de Android.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Envolvemos la app en el sistema de diseño personalizado
            R2PilotTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = LightBg
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// ========================================================================
// 3. ORQUESTADOR DE NAVEGACIÓN Y VIEWMODELS
// ========================================================================

/**
 * @brief Construye el grafo de navegación (NavHost) y gestiona los saltos de pantalla.
 * @details Implementa un paradigma de "Navegación Reactiva": la UI no fuerza los saltos
 *          de pantalla, sino que observa (collectAsState) la máquina de estados global
 *          y reacciona automáticamente cuando el servidor aprueba una conexión o desconexión.
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // --- 3.1 INYECCIÓN DE DEPENDENCIAS EN VIEWMODELS ---
    // Todos los ViewModels reciben exactamente la misma instancia del ProtocolDirector
    val mainViewModel: MainViewModel = viewModel { MainViewModel(AppDependencies.director) }
    val lobbyViewModel: LobbyViewModel = viewModel { LobbyViewModel(AppDependencies.director) }
    val controlViewModel: ControlViewModel = viewModel { ControlViewModel(AppDependencies.director) }
    val streamViewModel: StreamViewModel = viewModel { StreamViewModel(AppDependencies.director) }
    val playMotionViewModel: PlayMotionViewModel = viewModel { PlayMotionViewModel(AppDependencies.director) }
    val investigationViewModel: InvestigationViewModel = viewModel { InvestigationViewModel(AppDependencies.director) }
    val jointControlViewModel: JointControlViewModel = viewModel { JointControlViewModel(AppDependencies.director) }
    val sensorViewModel: SensorViewModel = viewModel { SensorViewModel(AppDependencies.director) }

    // --- 3.2 OBSERVADORES DE ESTADO GLOBAL ---
    val globalState by mainViewModel.globalState.collectAsState()
    val systemAlert by mainViewModel.systemAlert.collectAsState()

    // --- 3.3 LÓGICA DE NAVEGACIÓN REACTIVA ---
    LaunchedEffect(globalState) {
        val currentRoute = navController.currentDestination?.route

        // Excepción de diseño: Permitir que el SplashScreen termine su animación antes de redirigir
        if (currentRoute == "splash") return@LaunchedEffect

        when (globalState) {
            // Estado desconectado: Forzar retorno a la pantalla de Login
            AppConstants.GlobalState.IDLE -> {
                if (currentRoute != "login") {
                    navController.navigate("login") { popUpTo(0) } // popUpTo(0) purga el historial de retroceso
                }
            }
            // Estado intermedio: Navegar a la Sala de Espera (Host conectado, pero ROS 2 no enlazado)
            AppConstants.GlobalState.CONEXION_BACKEND -> {
                if (currentRoute != "conexion") {
                    navController.navigate("conexion") { popUpTo(0) }
                }
            }
            // Estado operativo: Acceso total a los mandos del robot
            AppConstants.GlobalState.SESION_INICIADA -> {
                if (currentRoute != "menu principal") {
                    navController.navigate("menu principal") { popUpTo(0) }
                }
            }
        }
    }

    // --- 3.4 SISTEMA DE ALERTAS GLOBALES ---
    // Este cuadro de diálogo puede sobreponerse en cualquier pantalla si la máquina de estados lo requiere.
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

    // --- 3.5 MAPA DE RUTAS ---
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                onAnimationFinished = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
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