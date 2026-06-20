package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Importamos el Director y las Constantes
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants

/**
 * Modelo de datos simple para la vista de Investigación.
 * Representa una fila en la lista (ej: nombre del topic y sus tipos).
 */
data class RosNodeItem(
    val name: String,
    val types: List<String>
)

/**
 * InvestigationViewModel
 * Gestiona la lógica de la pantalla de debug/investigación de ROS 2.
 */
class InvestigationViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ==========================================
    // 1. ESTADOS LOCALES DE LA INTERFAZ
    // ==========================================

    // Qué pestaña o filtro está seleccionado actualmente (Topics por defecto)
    private val _selectedResource = MutableStateFlow(AppConstants.Resource.TOPICS)
    val selectedResource: StateFlow<String> = _selectedResource.asStateFlow()

    // El texto que el usuario está escribiendo en la barra de búsqueda
    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    // ¡NUEVO! Variable exclusiva para controlar el "spinner" del botón de esta pantalla
    private val _isLoadingLocal = MutableStateFlow(false)
    val isLoadingLocal: StateFlow<Boolean> = _isLoadingLocal.asStateFlow()

    // Observamos el estado global para saber si estamos cargando (ESPERANDO_RECIBIR_INFORMACION_UNICA)
    val globalState: StateFlow<String> = director.stateManager.globalState

    // ==========================================
    // 2. EL MOTOR DE BÚSQUEDA REACTIVA (¡La Magia!)
    // ==========================================

    // Combine junta los flujos y se recalcula automáticamente si CUALQUIERA de ellos cambia.
    val filteredList: StateFlow<List<RosNodeItem>> = combine(
        _selectedResource,
        _searchText,
        director.rosTopics,
        director.rosServices,
        director.rosActions
    ) { resource, search, topics, services, actions ->

        // 1. Elegimos qué mapa mirar según la pestaña seleccionada
        val sourceMap = when (resource) {
            AppConstants.Resource.TOPICS -> topics
            AppConstants.Resource.SERVICES -> services
            AppConstants.Resource.ACTIONS -> actions
            else -> emptyMap()
        }

        // 2. Transformamos el mapa del backend (Map<String, List<String>>) a nuestra clase de UI
        val itemList = sourceMap.map { entry ->
            RosNodeItem(name = entry.key, types = entry.value)
        }

        // 3. Aplicamos el filtro de búsqueda
        if (search.isBlank()) {
            itemList // Si no hay texto, devolvemos todo
        } else {
            itemList.filter { item ->
                // Buscamos tanto en el nombre del topic/servicio como en sus tipos (ignorando mayúsculas)
                item.name.contains(search, ignoreCase = true) ||
                        item.types.any { type -> type.contains(search, ignoreCase = true) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // ==========================================
        // 3. VIGILANTE DE ESTADOS (Limpieza y Carga)
        // ==========================================
        viewModelScope.launch {
            globalState.collect { state ->
                // ¡NUEVO! Cuando el robot vuelve a su estado normal tras procesar la petición,
                // apagamos el circulito de carga de nuestro botón.
                if (state == AppConstants.GlobalState.SESION_INICIADA) {
                    _isLoadingLocal.value = false
                }

                // Si el robot se desconecta, limpiamos la barra de búsqueda y la memoria.
                if (state == AppConstants.GlobalState.IDLE || state == AppConstants.GlobalState.CONEXION_BACKEND) {
                    clearData()
                }
            }
        }
    }
    // ==========================================
    // 4. ACCIONES DEL USUARIO
    // ==========================================

    /**
     * Se llama al pulsar las pestañas (Topics, Services, Actions).
     * No hace la petición automáticamente, solo cambia el estado visual.
     */
    fun selectResource(resource: String) {
        _selectedResource.value = resource
        _searchText.value = "" // Limpiamos la búsqueda al cambiar de pestaña por comodidad
    }

    /**
     * Se llama al pulsar el botón "Listar ...".
     * Lanza la petición al backend a través del Director.
     */
    fun fetchNetworkInfo() {
        director.requestNetworkInfo(_selectedResource.value)
    }

    /**
     * Se llama cada vez que el usuario teclea una letra en el buscador.
     */
    fun updateSearchText(text: String) {
        _searchText.value = text
    }

    /**
     * Limpieza manual (se ejecuta también automáticamente al desconectar).
     */
    private fun clearData() {
        _searchText.value = ""
        director.clearNetworkInfo()
    }
}