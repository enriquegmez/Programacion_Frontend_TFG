/**
 * @file StreamViewModel.kt
 * @brief ViewModel encargado de la gestión de flujos multimedia en tiempo real (Cámaras).
 * @details Orquesta la negociación de parámetros (Tópico, Calidad) con el backend para
 *          levantar un servidor de streaming temporal, y expone la URL resultante a la UI.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- IMPORTS DE LA ARQUITECTURA ---
import com.enrique.tiago_app.core.ProtocolDirector

/**
 * @class StreamViewModel
 * @brief Orquesta el ciclo de vida del visor de cámara y la configuración del flujo de vídeo.
 * @param director Inyección del núcleo de comunicaciones.
 */
class StreamViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. ESTADOS OBSERVABLES POR LA INTERFAZ (UI State)
    // ========================================================================

    /**
     * @brief Máquina de estados dedicada al streaming (MonitorState).
     * @details Expone a la UI si estamos IDLE, RECIBIENDO_STREAM, o negociando la conexión,
     *          permitiendo mostrar spinners de carga o botones de play/stop.
     */
    val monitorState: StateFlow<String> = director.stateManager.monitorState

    /**
     * @brief Endpoint (URL) generado dinámicamente por el backend.
     * @details Se alimenta a un reproductor en Jetpack Compose
     *          para renderizar los fotogramas en vivo.
     */
    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()


    // ========================================================================
    // 2. PREFERENCIAS DE CONFIGURACIÓN DEL FLUJO
    // ========================================================================

    /** @brief Categoría del recurso a solicitar. */
    private val _currentResource = MutableStateFlow("CAMERA")
    val currentResource: StateFlow<String> = _currentResource.asStateFlow()

    /** @brief Tópico exacto de ROS 2 del que extraer los fotogramas. */
    private val _currentTopic = MutableStateFlow("/head_front_camera/rgb/image_raw")
    val currentTopic: StateFlow<String> = _currentTopic.asStateFlow()

    /** @brief Calidad de compresión para el stream (LOW, MEDIUM, HIGH). */
    private val _currentQuality = MutableStateFlow(AppConstants.CameraQuality.MEDIUM)
    val currentQuality: StateFlow<String> = _currentQuality.asStateFlow()


    // ========================================================================
    // 3. INICIALIZACIÓN Y VIGILANCIA REACTIVA
    // ========================================================================

    init {
        // --- 1. Sincronización de la URL de vídeo ---
        viewModelScope.launch {
            director.cameraStreamUrl.collect { url ->
                _streamUrl.value = url
            }
        }

        // --- 2. Auto-limpieza visual ---
        // Si el estado del monitor vuelve a IDLE (por desconexión de red o parada manual),
        // borramos inmediatamente la URL para que el reproductor se destruya y no muestre
        // un fotograma congelado engañando al usuario.
        viewModelScope.launch {
            monitorState.collect { state ->
                if (state == AppConstants.MonitorState.IDLE) {
                    _streamUrl.value = null
                }
            }
        }
    }


    // ========================================================================
    // 4. EVENTOS DE INTERACCIÓN
    // ========================================================================

    /**
     * @brief Alterna el estado del flujo multimedia (Play / Stop).
     * @details Delega la petición al Director empaquetando la configuración actual
     *          (tópico y calidad) seleccionada en la UI.
     */
    fun toggleStream() {
        if (monitorState.value == AppConstants.MonitorState.IDLE) {
            // Levantamos el puente de vídeo en el backend
            director.sendStartStream(
                resource = _currentResource.value,
                topic = _currentTopic.value,
                quality = _currentQuality.value
            )
        } else if (monitorState.value == AppConstants.MonitorState.RECIBIENDO_STREAM) {
            // Destruimos el proceso de retransmisión
            director.sendStopStream(
                resource = _currentResource.value,
                topic = _currentTopic.value
            )
        }
    }

    /**
     * @brief Fin de ciclo de vida invocado cuando la pantalla de cámara se oculta.
     * @details Previene el colapso de la red Wi-Fi del robot cortando
     *          el pesado flujo de vídeo de forma automática si el usuario navega a
     *          otra pestaña de la app.
     */
    fun onScreenDisposed() {
        if (monitorState.value != AppConstants.MonitorState.IDLE) {
            director.sendStopStream(
                resource = _currentResource.value,
                topic = _currentTopic.value
            )
        }
    }

    /**
     * @brief Lanza una alerta global a través del gestor de estados.
     * @param message Descripción del error (ej. "El campo del tópico no puede estar vacío").
     */
    fun showValidationError(message: String) {
        director.stateManager.showSystemAlert(message)
    }

    // --- ACTUALIZADORES DE ESTADO (Formulario de la UI) ---

    /** @brief Actualiza la categoría del sensor. */
    fun updateResource(newResource: String) {
        _currentResource.value = newResource
    }

    /** @brief Actualiza la ruta del tópico ROS 2 destino. */
    fun updateTopic(newTopic: String) {
        _currentTopic.value = newTopic
    }

    /** @brief Modifica la compresión deseada para ahorrar ancho de banda. */
    fun updateQuality(newQuality: String) {
        _currentQuality.value = newQuality
    }
}