package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.CategoryDao
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** CRUD de categorías de gasto (presupuesto mensual). */
class CategoryRepository(
    private val categoryDao: CategoryDao,
) {

    fun observeAll(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun getAll(): List<CategoryEntity> = categoryDao.getAll()

    suspend fun add(name: String, colorHex: String?): Long {
        val all = categoryDao.getAll()
        val nextOrder = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
        return categoryDao.insert(
            CategoryEntity(name = name.trim(), colorHex = colorHex, sortOrder = nextOrder)
        )
    }

    suspend fun rename(id: Long, name: String, colorHex: String?) =
        categoryDao.update(id, name.trim(), colorHex)

    suspend fun delete(id: Long) = categoryDao.delete(id)
}
