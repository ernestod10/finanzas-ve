package dev.tubolsillo.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** CRUD de categorías de gasto. */
class CategoriesViewModel(
    private val repository: CategoryRepository,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repository.categoriesState

    fun add(name: String, colorHex: String?, iconKey: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.add(name, colorHex, iconKey) }
    }

    fun update(id: Long, name: String, colorHex: String?, iconKey: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(id, name, colorHex, iconKey) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
