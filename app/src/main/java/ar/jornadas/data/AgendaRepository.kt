package ar.jornadas.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgendaRepository(
    private val api: ProgramaApi,
    private val dao: CarteleraDao,
    context: Context
) {
    private val estado = context.getSharedPreferences(PREFS_NOMBRE, Context.MODE_PRIVATE)
    private val _edicionDescargada = MutableStateFlow(estado.getBoolean(KEY_EDICION_DESCARGADA, false))
    val edicionDescargada: StateFlow<Boolean> = _edicionDescargada.asStateFlow()

    val programa = dao.observar()

    suspend fun sincronizar() {
        val fichas = api.consultar().map {
            Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
        }
        dao.reemplazarSnapshot(fichas)
        marcarEdicionDisponible()
    }

    fun marcarEdicionDisponible() {
        if (!_edicionDescargada.value) {
            estado.edit().putBoolean(KEY_EDICION_DESCARGADA, true).apply()
            _edicionDescargada.value = true
        }
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)

    private companion object {
        const val PREFS_NOMBRE = "agenda_estado"
        const val KEY_EDICION_DESCARGADA = "edicion_descargada"
    }
}
