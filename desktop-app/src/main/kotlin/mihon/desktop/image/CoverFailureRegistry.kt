package mihon.desktop.image

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object CoverFailureRegistry {
    private val failures = ConcurrentHashMap<Long, String>()
    private val _failuresFlow = MutableStateFlow<Map<Long, String>>(emptyMap())
    val failuresFlow: StateFlow<Map<Long, String>> = _failuresFlow.asStateFlow()

    fun record(mangaId: Long, url: String, code: Int) {
        if (code == 404 || code == 410) {
            failures[mangaId] = url
            _failuresFlow.value = HashMap(failures)
        }
    }

    fun snapshot(): Map<Long, String> = HashMap(failures)

    fun clear(mangaId: Long) {
        if (failures.remove(mangaId) != null) {
            _failuresFlow.value = HashMap(failures)
        }
    }

    fun clearAll() {
        failures.clear()
        _failuresFlow.value = emptyMap()
    }
}
