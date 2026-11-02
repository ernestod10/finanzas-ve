package dev.tubolsillo.app.core.di

import dev.tubolsillo.app.data.source.binance.BinanceSync
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.data.source.portal.PortalImporter
import dev.tubolsillo.app.domain.repo.CategoryRepository
import dev.tubolsillo.app.domain.repo.BalanceRepository
import dev.tubolsillo.app.domain.repo.BudgetRepository
import dev.tubolsillo.app.domain.repo.DashboardRepository
import dev.tubolsillo.app.domain.repo.PlanRepository
import dev.tubolsillo.app.domain.repo.RatesRefresher
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.domain.repo.StatsRepository
import dev.tubolsillo.app.domain.repo.UpdateRepository
import dev.tubolsillo.app.domain.repo.TagRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import dev.tubolsillo.app.domain.usecase.BackupExporter
import dev.tubolsillo.app.domain.usecase.BudgetCalculator
import dev.tubolsillo.app.domain.usecase.CapturePipeline
import dev.tubolsillo.app.domain.usecase.DedupGate
import dev.tubolsillo.app.domain.usecase.BalanceTrendBuilder
import dev.tubolsillo.app.domain.usecase.PeriodMetricsCalculator
import dev.tubolsillo.app.domain.usecase.PlanCalculator
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.RuleEngine
import dev.tubolsillo.app.domain.usecase.TotalsCalculator
import dev.tubolsillo.app.domain.usecase.TransferMatcher
import dev.tubolsillo.app.ui.account.AccountViewModel
import dev.tubolsillo.app.ui.budget.BudgetViewModel
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import dev.tubolsillo.app.ui.events.EventsViewModel
import dev.tubolsillo.app.ui.importcsv.CsvImportViewModel
import dev.tubolsillo.app.ui.categories.CategoriesViewModel
import dev.tubolsillo.app.ui.settings.BinanceSettingsViewModel
import dev.tubolsillo.app.ui.settings.ExportViewModel
import dev.tubolsillo.app.ui.settings.RatesViewModel
import dev.tubolsillo.app.ui.settings.UpdateViewModel
import dev.tubolsillo.app.ui.settings.AiModelViewModel
import dev.tubolsillo.app.ui.transactions.TransactionsViewModel
import dev.tubolsillo.app.ui.portal.PortalViewModel
import dev.tubolsillo.app.ui.plan.PlanViewModel
import dev.tubolsillo.app.ui.period.PeriodState
import dev.tubolsillo.app.ui.review.EntryDraftHolder
import dev.tubolsillo.app.ui.review.ReviewReadingCache
import dev.tubolsillo.app.ui.review.ReviewViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Repositorios y casos de uso (singleton de dominio, inyección por constructor). */
val domainModule = module {
    singleOf(::AccountRepository)
    singleOf(::CategoryRepository)
    singleOf(::BalanceRepository)
    singleOf(::BudgetRepository)
    singleOf(::DashboardRepository)
    singleOf(::SettingsRepository)
    singleOf(::TagRepository)
    singleOf(::TransactionRepository)
    singleOf(::UpdateRepository)
    singleOf(::BackupExporter)
    singleOf(::BudgetCalculator)
    single { PortalImporter(get(), get(), get(), get(), get()) }
    single { dev.tubolsillo.app.data.source.portal.PortalAutoSync(get(), get(), get(), get()) }
    singleOf(::CapturePipeline)
    singleOf(::DedupGate)
    singleOf(::RateProvider)
    singleOf(::RatesRefresher)
    singleOf(::RuleEngine)
    singleOf(::TotalsCalculator)
    singleOf(::TransferMatcher)
    singleOf(::PlanCalculator)
    singleOf(::PlanRepository)
    singleOf(::PeriodMetricsCalculator)
    singleOf(::BalanceTrendBuilder)
    singleOf(::StatsRepository)
    singleOf(::PeriodState)
    singleOf(::EntryDraftHolder)
    singleOf(::ReviewReadingCache)
    singleOf(::BinanceSync)
}

/** ViewModels (koinViewModel() los resuelve por clase). */
val viewModelModule = module {
    viewModelOf(::DashboardViewModel)
    viewModelOf(::TransactionsViewModel)
    viewModelOf(::BudgetViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::EventsViewModel)
    viewModelOf(::RatesViewModel)
    viewModelOf(::BinanceSettingsViewModel)
    viewModelOf(::CsvImportViewModel)
    viewModelOf(::ExportViewModel)
    viewModelOf(::PortalViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::PlanViewModel)
    viewModelOf(::ReviewViewModel)
    viewModelOf(::UpdateViewModel)
    viewModelOf(::AiModelViewModel)
}
