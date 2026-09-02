package dev.tubolsillo.app.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.rules.BancaribeParser
import dev.tubolsillo.app.data.parse.rules.BanescoParser
import dev.tubolsillo.app.data.parse.rules.BdvParser
import dev.tubolsillo.app.data.parse.rules.BncParser
import dev.tubolsillo.app.data.parse.rules.CasheaParser
import dev.tubolsillo.app.data.parse.rules.MercantilPanamaParser

@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds @IntoSet abstract fun bindBanesco(parser: BanescoParser): InstitutionParser
    @Binds @IntoSet abstract fun bindMercantilPa(parser: MercantilPanamaParser): InstitutionParser
    @Binds @IntoSet abstract fun bindBancaribe(parser: BancaribeParser): InstitutionParser
    @Binds @IntoSet abstract fun bindBnc(parser: BncParser): InstitutionParser
    @Binds @IntoSet abstract fun bindBdv(parser: BdvParser): InstitutionParser
    @Binds @IntoSet abstract fun bindCashea(parser: CasheaParser): InstitutionParser
}
