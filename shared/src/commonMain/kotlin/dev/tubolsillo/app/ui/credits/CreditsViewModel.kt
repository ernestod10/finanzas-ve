package dev.tubolsillo.app.ui.credits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.model.CreditsOverview
import dev.tubolsillo.app.domain.repo.CreditsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.db.dao.InstallmentDao
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.repo.AccountRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Deudas vivas (tarjetas, Cashea y cuotas) con su próxima fecha de pago. */
class CreditsViewModel(
    repository: CreditsRepository,
    private val accountRepository: AccountRepository,
    private val installmentDao: InstallmentDao,
) : ViewModel() {
    val overview: StateFlow<CreditsOverview> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreditsOverview())

    /**
     * Guarda el límite de una tarjeta. Es lo que falta para que la deuda salga
     * sola cuando el banco (BNC) solo publica el disponible: deuda = límite −
     * disponible.
     */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Compra de Cashea: se paga una inicial y el resto en 3, 6, 9 o 12 cuotas
     * cada 15 días. Se guardan como cuotas pendientes (lo que la pantalla de
     * Créditos suma como deuda) y se registra el saldo de la cuenta BNPL.
     */
    fun addCasheaPurchase(
        description: String,
        totalText: String,
        initialText: String,
        installments: Int,
        firstDueAt: Long,
    ) {
        val total = AmountNormalizer.parseMinor(totalText, "VES") ?: return
        val initial = AmountNormalizer.parseMinor(initialText.ifBlank { "0" }, "VES") ?: 0L
        if (total <= 0 || initial < 0 || initial >= total || installments !in listOf(3, 6, 9, 12)) {
            _message.value = "Revisa los montos y el número de cuotas."
            return
        }
        viewModelScope.launch {
            val financed = total - initial
            val per = financed / installments
            val remainder = financed - per * installments
            val account = accountRepository.observeActive().first()
                .firstOrNull { it.institution == Institution.CASHEA.key && it.kind == AccountKind.BNPL.key }
                ?: accountRepository.create(
                    institution = Institution.CASHEA,
                    name = "Cashea",
                    kind = AccountKind.BNPL,
                    currencyCode = "VES",
                    initialBalanceMinor = null,
                    creditLimitMinor = null,
                ).let { accountRepository.getById(it) }

            val dayMs = 86_400_000L
            for (i in 0 until installments) {
                // La última cuota absorbe el redondeo para que sumen exacto.
                val amount = per + if (i == installments - 1) remainder else 0L
                installmentDao.insert(
                    InstallmentEntity(
                        accountId = account?.id ?: return@launch,
                        dueAt = firstDueAt + i * 15L * dayMs,
                        amountMinor = amount,
                        currency = "VES",
                        status = "PENDING",
                    )
                )
            }
            account?.let {
                val current = it.lastBalanceMinor ?: 0L
                accountRepository.recordBalance(
                    accountId = it.id,
                    amountMinor = current - financed,
                    currencyCode = "VES",
                    source = SourceType.MANUAL,
                    rawText = "cashea:" + description.ifBlank { "compra" },
                )
            }
            _message.value = "Compra de Cashea agregada: " + installments + " cuotas de " +
                MoneyFormatter.formatMinor(per, "VES") + " cada 15 días."
        }
    }

    fun setLimit(accountId: Long, currency: String, text: String) {
        val minor = AmountNormalizer.parseMinor(text, currency) ?: return
        if (minor <= 0) return
        viewModelScope.launch { accountRepository.updateCreditLimit(accountId, minor) }
    }
}
