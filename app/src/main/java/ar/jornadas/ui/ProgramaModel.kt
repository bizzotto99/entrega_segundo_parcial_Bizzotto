package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

class ProgramaModel(app: Application, private val state: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.obtener(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda

    val charlas: StateFlow<List<Charla>?> = repo.programa
        .onEach { if (it.isNotEmpty()) repo.marcarEdicionDisponible() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val tieneEdicionPrevia: StateFlow<Boolean> = combine(repo.edicionDescargada, charlas) { descargada, lista ->
        descargada || !lista.isNullOrEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, repo.edicionDescargada.value)

    val dia: StateFlow<Int> = state.getStateFlow(KEY_DIA, 0)
    val tema: StateFlow<String> = state.getStateFlow(KEY_TEMA, TEMA_TODOS)
    val aviso = MutableStateFlow("")
    val cargando = MutableStateFlow(false)

    fun elegirDia(nuevoDia: Int) { state[KEY_DIA] = nuevoDia }

    fun elegirTema(nuevoTema: String) { state[KEY_TEMA] = nuevoTema }

    fun asegurarCargaInicial() {
        if (servicios.consumirCargaInicial()) {
            recargar()
        }
    }

    // Toques durante una petición en curso se agrupan en UNA sola petición posterior,
    // que captura la disponibilidad y edición vigentes al comenzar.
    private var pendiente = false

    fun recargar() {
        if (cargando.value) {
            pendiente = true
            return
        }
        cargando.value = true
        viewModelScope.launch {
            try {
                do {
                    pendiente = false
                    aviso.value = ""
                    try {
                        repo.sincronizar()
                    } catch (e: IOException) {
                        aviso.value = "No se pudo consultar el programa"
                    }
                } while (pendiente)
            } finally {
                pendiente = false
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }

    companion object {
        const val TEMA_TODOS = "Todos"
        private const val KEY_DIA = "filtro_dia"
        private const val KEY_TEMA = "filtro_tema"
    }
}
