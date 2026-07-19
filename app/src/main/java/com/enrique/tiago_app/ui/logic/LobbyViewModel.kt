/**
 * @file LobbyViewModel.kt
 * @brief ViewModel para la "Sala de Espera" de la aplicación.
 * @details Gestiona la configuración inicial del middleware de ROS 2 (DDS, Domain ID),
 *          y el ciclo de vida del sistema operativo (Apagado / Reinicio).
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import com.enrique.tiago_app.core.ProtocolDirector
import com.enrique.tiago_app.protocol.HostTelemetryData

/**
 * @class LobbyViewModel
 * @brief Actúa como controlador de la pantalla principal antes de iniciar el control motriz.
 * @param director Inyección del núcleo de comunicaciones.
 */
class LobbyViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. ESTADO EXPUESTO A LA INTERFAZ GRÁFICA (UI State)
    // ========================================================================

    /** 
     * @brief Semáforo global de la aplicación.
     * @details Se utiliza en la UI para bloquear botones y mostrar indicadores de carga
     *          cuando el sistema transita entre estados (ej. esperando respuesta del servidor).
     */
    val globalState: StateFlow<String> = director.stateManager.globalState

    /** 
     * @brief Datos de telemetría del hardware de la máquina anfitriona (Host PC).
     */
    val hostTelemetry: StateFlow<HostTelemetryData?> = director.hostTelemetry


    // ========================================================================
    // 2. EVENTOS DE INTERACCIÓN (Intentions desde Jetpack Compose)
    // ========================================================================

    /**
     * @brief Solicita al servidor una lectura fresca de los sensores del PC.
     */
    fun fetchTelemetry() {
        director.requestHostTelemetry()
    }

    /**
     * @brief Aplica la configuración de red y middleware de ROS 2.
     * @param domainId Identificador de dominio de ROS 2 (ROS_DOMAIN_ID) para aislar tráfico.
     * @param dds Implementación de Data Distribution Service a utilizar (ej. FastRTPS, CycloneDDS).
     * @param useDiscovery Indica si se debe usar el Discovery Server o el modo Multicast por defecto.
     */
    fun saveNetworkConfig(domainId: String, dds: String, useDiscovery: Boolean) {
        director.sendChangeVars(domainId, dds, useDiscovery)
    }

    /**
     * @brief Envía la señal de reinicio a nivel de sistema operativo (Host OS).
     * @details Acción crítica. Generalmente protegida en la UI por un diálogo de confirmación.
     */
    fun rebootRobot() {
        director.sendRebootRobot()
    }

    /**
     * @brief Envía la señal de apagado seguro a nivel de sistema operativo (Host OS).
     */
    fun shutdownRobot() {
        director.sendShutdownRobot()
    }

    /**
     * @brief Inicia el proceso de creación del nodo ROS 2 en el backend.
     * @details Pide al servidor de Python que instancie el puente de comunicaciones con el hardware.
     */
    fun connectToRobot() {
        director.sendConnectToRobot()
    }

    /**
     * @brief Cierra la sesión activa y desconecta los WebSockets.
     * @details Devuelve la aplicación a la pantalla de Login (Escaneo de red).
     */
    fun disconnectFromServer() {
        director.sendEndProtocol()
    }
}