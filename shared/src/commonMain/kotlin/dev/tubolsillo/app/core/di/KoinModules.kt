package dev.tubolsillo.app.core.di

import dev.tubolsillo.app.core.db.TubolsilloDatabase
import dev.tubolsillo.app.core.db.getDatabaseBuilder
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.ParserRegistry
import dev.tubolsillo.app.data.parse.rules.BancaribeParser
import dev.tubolsillo.app.data.parse.rules.BanescoParser
import dev.tubolsillo.app.data.parse.rules.BdvParser
import dev.tubolsillo.app.data.parse.rules.BncParser
import dev.tubolsillo.app.data.parse.rules.CasheaParser
import dev.tubolsillo.app.data.parse.rules.MercantilPanamaParser
import org.koin.core.module.Module
import org.koin.dsl.module

/** Base de datos Room KMP + DAOs. */
val dbModule = module {
    single { getDatabaseBuilder().build() }
    single { get<TubolsilloDatabase>().accountDao() }
    single { get<TubolsilloDatabase>().transactionDao() }
    single { get<TubolsilloDatabase>().balanceSnapshotDao() }
    single { get<TubolsilloDatabase>().transferPairDao() }
    single { get<TubolsilloDatabase>().currencyRateDao() }
    single { get<TubolsilloDatabase>().captureEventDao() }
    single { get<TubolsilloDatabase>().installmentDao() }
    single { get<TubolsilloDatabase>().budgetDao() }
    single { get<TubolsilloDatabase>().tagDao() }
    single { get<TubolsilloDatabase>().categoryRuleDao() }
    single { get<TubolsilloDatabase>().categoryDao() }
}

/** Parsers por institución (el equivalente Koin del @IntoSet de Hilt). */
val parserModule = module {
    single {
        listOf<InstitutionParser>(
            BanescoParser(),
            MercantilPanamaParser(),
            BancaribeParser(),
            BncParser(),
            BdvParser(),
            CasheaParser(),
        )
    }
    single { ParserRegistry(get()) }
}

/** Red (Ktor KMP; el engine lo provee createHttpClient() por plataforma). */
val networkModule = module {
    single { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    single { dev.tubolsillo.app.core.platform.createHttpClient() }
    single { dev.tubolsillo.app.data.source.binance.BinanceApi(get()) }
    single { dev.tubolsillo.app.data.source.rates.RatesApi(get()) }
}

fun commonKoinModules(): List<Module> = listOf(dbModule, parserModule, networkModule)
