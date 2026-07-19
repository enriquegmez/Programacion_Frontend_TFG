/**
 * @file SensorsViewModel.kt
 * @brief ViewModel encargado de la telemetría sensorial y la odometría.
 * @details Gestiona el descubrimiento dinámico de sensores, la suscripción a flujos de datos
 *          en tiempo real y el mantenimiento en memoria de trayectorias para el mapa 2D.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import com.enrique.tiago_app.core.ProtocolDirector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.enrique.tiago_app.utils.AppConstants

/**
 * @class SensorViewModel
 * @brief Orquesta la visualización de datos de los sensores de ROS 2.
 * @param director Inyección del núcleo de comunicaciones.
 */
class SensorViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. ESTADO GLOBAL DE SENSORES (Pasillos desde el Director)
    // ========================================================================

    /** @brief Inventario de sensores detectados por el backend en el ecosistema ROS 2. */
    val availableSensors = director.availableSensors

    /** @brief Flag que indica si ya se ha realizado el escaneo inicial de tópicos sensoriales. */
    val hasSearched = director.hasScannedSensors

    /** @brief Flujo continuo (Stream) con las lecturas brutas de cada sensor activo. */
    val activeSensorData = director.activeSensorData


    // ========================================================================
    // 2. ESTADO LOCAL DE LA INTERFAZ (UI State)
    // ========================================================================

    /**
     * @brief Conjunto (Set) de los tópicos que el usuario tiene activados (interruptor ON).
     * @details Se usa un Set para garantizar que no haya tópicos duplicados consumiendo memoria.
     */
    private val _activeSensorTopics = MutableStateFlow<Set<String>>(emptySet())
    val activeSensorTopics: StateFlow<Set<String>> = _activeSensorTopics.asStateFlow()


    // ========================================================================
    // 3. MEMORIA DE TRAYECTORIA (Odometry Trail)
    // ========================================================================

    /**
     * @brief Histórico de la trayectoria del robot en el plano 2D (Coordenadas X, Y).
     * @details Reside en el ViewModel para persistir a las rotaciones de pantalla y navegaciones breves.
     *          Los tramos discontinuos se separan usando coordenadas (NaN, NaN) para evitar
     *          que el renderizador (Canvas) dibuje líneas rectas irreales tras pausas de conexión.
     */
    private val _odomTrail = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val odomTrail: StateFlow<List<Pair<Float, Float>>> = _odomTrail.asStateFlow()

    /** @brief Límite de puntos en memoria para evitar desbordamiento. */
    private val maxTrailPoints = 1000

    /**
     * @brief Inserta un "corte" en la trayectoria geométrica.
     * @details Se invoca al reanudar la vista de odometría para indicar al dibujante que el
     *          siguiente punto no debe unirse físicamente con el anterior.
     */
    fun startNewTrailSegment() {
        val current = _odomTrail.value
        // Solo insertamos el separador si hay datos previos y el último punto no es ya un separador
        if (current.isNotEmpty() && !current.last().first.isNaN()) {
            _odomTrail.value = current + (Float.NaN to Float.NaN)
        }
    }

    /**
     * @brief Registra un nuevo punto de odometría si el desplazamiento es significativo.
     * @param x Coordenada X actual del robot respecto al marco de origen (map/odom).
     * @param y Coordenada Y actual del robot respecto al marco de origen.
     */
    fun addTrailPoint(x: Float, y: Float) {
        val current = _odomTrail.value
        // Buscamos la última coordenada válida (ignorando los separadores NaN)
        val last = current.lastOrNull { !it.first.isNaN() }

        // Solo guardamos el punto si el robot se ha movido
        // más de 1 cm (0.01 metros). Esto evita llenar la lista de puntos inútiles por
        // el simple ruido de los encoders o el láser.
        if (last == null || kotlin.math.hypot((x - last.first).toDouble(), (y - last.second).toDouble()) > 0.01) {
            val updated = current + (x to y)
            // Si superamos el máximo, borramos los puntos más antiguos
            _odomTrail.value = if (updated.size > maxTrailPoints) updated.drop(updated.size - maxTrailPoints) else updated
        }
    }

    /**
     * @brief Eliminación completa de la trayectoria. Se invoca al finalizar la sesión con el robot.
     */
    fun clearTrail() {
        _odomTrail.value = emptyList()
    }


    // ========================================================================
    // 4. EVENTOS DE INTERACCIÓN (Acciones del usuario)
    // ========================================================================

    /**
     * @brief Desencadena la búsqueda de tópicos de tipo sensor en el ecosistema ROS 2.
     */
    fun fetchSensors() {
        director.sendQuerySensorsReq()
    }

    /**
     * @brief Solicita al servidor suscribirse o desuscribirse de un flujo de datos concreto.
     * @param topic Nombre del tópico (ej. "/scan", "/odom").
     * @param isChecked Estado deseado del interruptor (true = Suscribir, false = Destruir suscriptor).
     */
    fun toggleSensor(topic: String, isChecked: Boolean) {
        val currentActive = _activeSensorTopics.value.toMutableSet()

        if (isChecked) {
            currentActive.add(topic)
            // Abrimos el grifo en el servidor backend (Crea un subscritor de ROS 2 on-demand)
            director.sendStartStream(AppConstants.Resource.SENSORS, topic)
        } else {
            currentActive.remove(topic)
            // Cerramos el grifo para ahorrar ancho de banda
            director.sendStopStream(AppConstants.Resource.SENSORS, topic)
        }
        _activeSensorTopics.value = currentActive
    }

    /**
     * @brief Fin de ciclo de vida invocado cuando la pantalla deja de ser visible.
     * @details Evita que el servidor siga enviando arrays gigantescos por el WebSocket
     *          cuando el usuario no los está mirando en su móvil.
     */
    fun onScreenDisposed() {
        // 1. Apagado de streams activos
        _activeSensorTopics.value.forEach { topic ->
            director.sendStopStream(AppConstants.Resource.SENSORS, topic)
        }

        // 2. Reseteo visual de los interruptores
        _activeSensorTopics.value = emptySet()

        // 3. Borrado del búfer en el ProtocolDirector para evitar datos fantasma al volver
        director.clearActiveSensorData()
    }
}