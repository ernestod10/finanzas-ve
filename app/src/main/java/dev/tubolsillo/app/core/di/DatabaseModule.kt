package dev.tubolsillo.app.core.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.tubolsillo.app.core.db.TubolsilloDatabase
import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.BalanceSnapshotDao
import dev.tubolsillo.app.core.db.dao.BudgetDao
import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.db.dao.CategoryRuleDao
import dev.tubolsillo.app.core.db.dao.CurrencyRateDao
import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.dao.TransferPairDao
import org.koin.java.KoinJavaComponent.getKoin
import javax.inject.Singleton

/**
 * Puente temporal Hilt→Koin: la base de datos y los DAOs viven en el grafo
 * Koin de shared; Hilt los expone a los ViewModels hasta M4.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(): TubolsilloDatabase = getKoin().get()

    @Provides fun provideAccountDao(): AccountDao = getKoin().get()
    @Provides fun provideTransactionDao(): TransactionDao = getKoin().get()
    @Provides fun provideBalanceSnapshotDao(): BalanceSnapshotDao = getKoin().get()
    @Provides fun provideTransferPairDao(): TransferPairDao = getKoin().get()
    @Provides fun provideCurrencyRateDao(): CurrencyRateDao = getKoin().get()
    @Provides fun provideCaptureEventDao(): CaptureEventDao = getKoin().get()
    @Provides fun provideInstallmentDao(): InstallmentDao = getKoin().get()
    @Provides fun provideBudgetDao(): BudgetDao = getKoin().get()
    @Provides fun provideTagDao(): TagDao = getKoin().get()
    @Provides fun provideCategoryRuleDao(): CategoryRuleDao = getKoin().get()
}
