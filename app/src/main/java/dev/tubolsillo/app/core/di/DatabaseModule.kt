package dev.tubolsillo.app.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TubolsilloDatabase =
        Room.databaseBuilder(context, TubolsilloDatabase::class.java, "tubolsillo.db")
            .build()

    @Provides fun provideAccountDao(db: TubolsilloDatabase): AccountDao = db.accountDao()
    @Provides fun provideTransactionDao(db: TubolsilloDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideBalanceSnapshotDao(db: TubolsilloDatabase): BalanceSnapshotDao = db.balanceSnapshotDao()
    @Provides fun provideTransferPairDao(db: TubolsilloDatabase): TransferPairDao = db.transferPairDao()
    @Provides fun provideCurrencyRateDao(db: TubolsilloDatabase): CurrencyRateDao = db.currencyRateDao()
    @Provides fun provideCaptureEventDao(db: TubolsilloDatabase): CaptureEventDao = db.captureEventDao()
    @Provides fun provideInstallmentDao(db: TubolsilloDatabase): InstallmentDao = db.installmentDao()
    @Provides fun provideBudgetDao(db: TubolsilloDatabase): BudgetDao = db.budgetDao()
    @Provides fun provideTagDao(db: TubolsilloDatabase): TagDao = db.tagDao()
    @Provides fun provideCategoryRuleDao(db: TubolsilloDatabase): CategoryRuleDao = db.categoryRuleDao()
}
