package dev.tubolsillo.app.core.di

import org.koin.core.context.GlobalContext

/** Acceso global al grafo Koin desde clases sin inyección (receiver, service, worker). */
inline fun <reified T> sharedKoin(): T = GlobalContext.get().get()
