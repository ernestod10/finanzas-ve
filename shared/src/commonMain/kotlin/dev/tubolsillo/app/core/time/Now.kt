package dev.tubolsillo.app.core.time

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Epoch ms actual, común a todas las plataformas (kotlin.time.Clock). */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
