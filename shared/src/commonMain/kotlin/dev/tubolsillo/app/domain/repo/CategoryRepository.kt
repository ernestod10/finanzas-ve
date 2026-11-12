package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.CategoryDao
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** CRUD de categorías de gasto (presupuesto mensual). */
class CategoryRepository(
    private val categoryDao: CategoryDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val categoriesState: StateFlow<List<CategoryEntity>> =
        categoryDao.observeAll()
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun observeAll(): Flow<List<CategoryEntity>> = categoriesState

    suspend fun getAll(): List<CategoryEntity> = categoryDao.getAll()

    suspend fun add(name: String, colorHex: String?, iconKey: String? = null): Long {
        val all = categoryDao.getAll()
        val nextOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return categoryDao.insert(
            CategoryEntity(
                name = name.trim(),
                colorHex = colorHex,
                sortOrder = nextOrder,
                iconKey = iconKey,
            )
        )
    }

    suspend fun rename(id: Long, name: String, colorHex: String?, iconKey: String? = null) =
        categoryDao.update(id, name.trim(), colorHex, iconKey)

    suspend fun delete(id: Long) = categoryDao.delete(id)
}
