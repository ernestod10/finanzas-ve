package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.repo.UpdateRepository
import dev.tubolsillo.app.domain.repo.UpdateState
import dev.tubolsillo.app.domain.update.UpdateInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Versión instalada y actualización disponible desde GitHub Releases. */
class UpdateViewModel(
    private val repository: UpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = repository.state
    val currentVersion: String = repository.currentVersion

    fun check() {
        viewModelScope.launch { repository.check() }
    }

    fun download(info: UpdateInfo) {
        viewModelScope.launch { repository.download(info) }
    }
}
