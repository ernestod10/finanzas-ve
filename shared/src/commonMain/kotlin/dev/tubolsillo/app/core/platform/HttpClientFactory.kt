package dev.tubolsillo.app.core.platform

import io.ktor.client.HttpClient

/** Cliente HTTP común: el engine es por plataforma (OkHttp Android, Darwin iOS). */
expect fun createHttpClient(): HttpClient
