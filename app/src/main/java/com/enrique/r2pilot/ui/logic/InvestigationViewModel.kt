/**
 * @file InvestigationViewModel.kt
 * @brief ViewModel para la herramienta de Análisis e Investigación de la red ROS 2.
 * @details Implementa un motor de búsqueda y filtrado reactivo en memoria. Se encarga de solicitar
 *          y formatear el grafo de tópicos, servicios y acciones expuestos por el robot.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// --- IMPORTS DE LA ARQUITECTURA ---
import com.enrique.r2pilot.core.ProtocolDirector
import com.enrique.r2pilot.utils.AppConstants

/**
 * @class RosNodeItem
 * @brief Modelo de Presentación (UI Model) para las listas de investigación.
 * @details Aísla la vista de las estructuras de datos puras del backend (Mapas).
 * @param name Nombre de la interfaz de ROS 2 (ej: "/cmd_vel").
 * @param types Lista de tipos de mensajes asociados (ej: ["geometry_msgs/msg/Twist"]).
 */
data class RosNodeItem(
    val name: String,
    val types: List<String>
)

/**
 * @class InvestigationViewModel
 * @brief Orquesta las peticiones de topología de red a ROS 2 y su visualización filtrada.
 * @param director Inyección de dependencia del núcleo de comunicaciones.
 */
class InvestigationViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. ESTADOS LOCALES DE LA INTERFAZ
    // ========================================================================

    /** @brief Categoría actual seleccionada por el usuario (TOPICS, SERVICES, ACTIONS). */
    private val _selectedResource = MutableStateFlow(AppConstants.Resource.TOPICS)
    val selectedResource: StateFlow<String> = _selectedResource.asStateFlow()

    /** @brief Cadena de texto actual introducida en la barra de búsqueda. */
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    /**
     * @brief Controla de forma aislada el spinner de carga del botón "Listar".
     * @details Evita bloquear toda la pantalla de forma global.
     */
    private val _isLoadingLocal = MutableStateFlow(false)
    val isLoadingLocal: StateFlow<Boolean> = _isLoadingLocal.asStateFlow()

    /**
     * @brief Semáforo global del sistema.
     * @details Informa de transiciones de alto nivel (como ESPERANDO_RECIBIR_INFORMACION_UNICA).
     */
    val globalState: StateFlow<String> = director.stateManager.globalState

    // ========================================================================
    // 2. EL MOTOR DE BÚSQUEDA REACTIVA
    // ========================================================================

    /**
     * @brief Tubería de datos reactiva que combina 5 fuentes de información distintas.
     * @details Si *cualquiera* de las fuentes cambia (ej. el usuario teclea una letra,
     *          o llega un nuevo topic del backend), el bloque se re-ejecuta automáticamente.
     *          La búsqueda se hace localmente (en caché), ahorrando ancho de banda.
     */
    val filteredList: StateFlow<List<RosNodeItem>> = combine(
        _selectedResource,
        _searchText,
        director.rosTopics,
        director.rosServices,
        director.rosActions
    ) { resource, search, topics, services, actions ->

        // 1. SELECCIÓN DE ORIGEN: ¿Qué mapa del Director debemos mirar?
        val sourceMap = when (resource) {
            AppConstants.Resource.TOPICS -> topics
            AppConstants.Resource.SERVICES -> services
            AppConstants.Resource.ACTIONS -> actions
            else -> emptyMap()
        }

        // 2. MAPEO A MODELO DE VISTA: Convertimos Map<String, List<String>> -> List<RosNodeItem>
        val itemList = sourceMap.map { entry ->
            RosNodeItem(name = entry.key, types = entry.value)
        }

        // 3. APLICACIÓN DEL FILTRO LOCAL
        if (search.isBlank()) {
            // Si la barra está vacía, entregamos la lista completa
            itemList
        } else {
            itemList.filter { item ->
                // Buscamos coincidencia parcial ignorando mayúsculas/minúsculas.
                // Se busca tanto en el nombre del nodo como en sus tipos de mensaje.
                item.name.contains(search, ignoreCase = true) ||
                        item.types.any { type -> type.contains(search, ignoreCase = true) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed(5000): Mantiene los datos en memoria 5 segundos tras perder el foco de la UI
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // ========================================================================
    // 3. VIGILANTE DE ESTADOS (Sincronización con el Backend)
    // ========================================================================
    init {
        viewModelScope.launch {
            globalState.collect { state ->
                // 1. Al volver a estado normal (SESION_INICIADA),
                // apagamos la animación de carga del botón de petición.
                if (state == AppConstants.GlobalState.SESION_INICIADA) {
                    _isLoadingLocal.value = false
                }

                // 2. Si el robot se apaga o pierde señal,
                // reseteamos por seguridad la vista para no mostrar datos obsoletos o fantasma.
                if (state == AppConstants.GlobalState.IDLE || state == AppConstants.GlobalState.CONEXION_BACKEND) {
                    clearData()
                }
            }
        }
    }

    // ========================================================================
    // 4. EVENTOS DE INTERACCIÓN
    // ========================================================================

    /**
     * @brief Cambia el recurso a investigar (Topics, Actions o Services).
     * @details Es una operación puramente visual. Al cambiar de pestaña, se borra el término
     *          de búsqueda activo para evitar resultados confusos.
     * @param resource Constante desde AppConstants.Resource.
     */
    fun selectResource(resource: String) {
        _selectedResource.value = resource
        _searchText.value = ""
    }

    /**
     * @brief Lanza la petición al backend para mostrar el grafo de red actual.
     * @details Delega la llamada de red al `ProtocolDirector`.
     */
    fun fetchNetworkInfo() {
        director.requestNetworkInfo(_selectedResource.value)
    }

    /**
     * @brief Actualiza en tiempo real el término del buscador.
     * @param text Nueva cadena de texto introducida por teclado.
     */
    fun updateSearchText(text: String) {
        _searchText.value = text
    }

    /**
     * @brief Eliminación interna manual de la información en pantalla.
     * @details Vacía tanto el texto de búsqueda como la caché temporal en el Director.
     */
    private fun clearData() {
        _searchText.value = ""
        director.clearNetworkInfo()
    }
}