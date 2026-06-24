package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.protocol.HostTelemetryData

/**
 * LobbyViewModel
 * Gestiona la Sala de Espera: telemetría del PC, configuración de red y energía.
 */
class LobbyViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // 1. Observamos el estado global (para saber si estamos cargando)
    val globalState: StateFlow<String> = director.stateManager.globalState

    // 2. Observamos la telemetría que nos manda Python
    val hostTelemetry: StateFlow<HostTelemetryData?> = director.hostTelemetry

    // ==========================================
    // MÉTODOS DE LA UI -> DIRECTOR
    // ==========================================

    fun fetchTelemetry() {
        director.requestHostTelemetry()
    }

    fun saveNetworkConfig(domainId: String, dds: String, useDiscovery: Boolean) {
        director.sendChangeVars(domainId, dds, useDiscovery)
    }

    fun rebootRobot() {
        director.sendRebootRobot()
    }

    fun shutdownRobot() {
        director.sendShutdownRobot()
    }

    fun connectToRobot() {
        director.sendConnectToRobot()
    }

    fun disconnectFromServer() {
        director.sendEndProtocol()
    }
}
