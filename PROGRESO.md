# TuBolsillo — Progreso del proyecto

> Documento de estado generado el 2-sep-2026. Todo lo committeado en git; cada sección referencia sus commits.

## ¿Qué es TuBolsillo?

App de finanzas personales (Android hoy, iOS en camino) para agregar cuentas bancarias venezolanas,
Mercantil Panamá, Binance y Cashea, con **presupuesto mensual por etiqueta**, **etiquetado en vivo**
de transacciones (auto-aprendizaje por comercio), patrimonio neto en USD y captura automática de
movimientos. 100% local en el teléfono — **cero-conocimiento**: la data financiera nunca sale del
dispositivo.

## Estado actual

- ✅ Compila: `:shared:testDebugUnitTest` + `:app:assembleDebug` — **62 tests verdes**.
- ✅ Fases 0–5 del MVP Android completas.
- ✅ Migración KMP: M0, M1, M2, M3 completas.
- 🔄 M4 (UI a shared) es el siguiente paso.
- ⏸ Pausado aquí por decisión del usuario.

## Historia (commits)

| Commit | Fase | Contenido |
|---|---|---|
| `a785486` | Fase 0 | Esqueleto Android (Kotlin/Compose, Hilt, minSdk 26) |
| `e6c4dbc` | Fase 1 | Núcleo manual: Room v1 (11 entidades), money es-VE, presupuesto por etiqueta, etiquetado en vivo, pantallas |
| `1378298` | Fase 2 | Binance: SecretStore (Keystore), HMAC signer, sync 15min, pantalla de llaves |
| `d94bfe2` | Fase 3 | Captura automática SMS/push: CapturePipeline, DedupGate, TransferMatcher, 6 parsers, Capturas+Replay |
| `a9cb46a` | Fase 4 | Importación CSV con presets y reconciliación |
| `729ea49` | Fase 5 | Exportar respaldo JSON completo (SAF) |
| `35d819a` | Fase 5 | UX Binance: wizard 3 pasos con deep-link a API Management |
| `0376e73` | M0 | Bump Kotlin 2.2.20 + KSP 2.2.20-2.0.4 (aisla riesgo del compilador) |
| `07838c0` | M1 | Módulo `shared` + código puro en commonMain (money/text/time reescritos KMP), tests → kotlin.test |
| `3deedf8` | M2 | Room KMP 2.8.2 + sqlite-bundled, DataStore KMP, Koin data layer, puente Hilt→Koin |
| `93949f2` | M3 | Ktor 3.2.4 reemplaza Retrofit (BinanceApi KMP, MockEngine tests con asserts de headers) |

## Arquitectura actual

```
shared/   (Kotlin Multiplatform)
├── commonMain/  ← corazón multiplataforma
│   ├── core/money|text|time    (puro, sin JVM: MoneyFormatter propio, acentos, kotlinx-datetime)
│   ├── core/db                 (Room KMP: 11 entidades + 9 DAOs, builder expect/actual)
│   ├── core/notify             (CaptureNotifier = interfaz)
│   ├── core/platform           (expect: dataStorePath, createHttpClient)
│   ├── core/di                 (Koin: dbModule, parserModule, networkModule)
│   ├── domain/model|repo|usecase
│   └── data/parse|source/binance (parsers canal-agnósticos; BinanceApi Ktor)
├── androidMain/ ← actuals Android
│   ├── DatabaseBuilder (Room android + BundledSQLiteDriver, archivo tubolsillo.db sin cambio)
│   ├── DataStorePath (filesDir), HttpClientFactory (OkHttp, expectSuccess=true)
│   ├── SecretStore (EncryptedSharedPreferences), AndroidCaptureNotifier
│   └── BinanceSync (friendlyError con excepciones Ktor), CsvImporter (kotlin-csv hasta M7)
└── commonTest/  ← 62 tests (kotlin.test, MockEngine, Turbine)

app/      (androidApp)
├── ui/** (Compose — se mueve a shared en M4)
├── data/source/sms|notification (SmsReceiver, BankNotificationListener)
├── core/di (puente Hilt→Koin temporal: getKoin().get())
├── BinanceWorker (WorkManager), TubolsilloApp (startKoin), MainActivity
└── AndroidManifest (READ_SMS, NLS, POST_NOTIFICATIONS, WorkManagerInitializer removido)

iosApp/  (pendiente — M5, se genera con el wizard oficial KMP en la Mac)
```

## Decisiones clave

1. **Sin backend** (MVP): todo on-device; email IMAP local en iOS (M6). El backend
   cero-conocimiento (Lambdas) llega cuando se masifique.
2. **Captura automática**: SMS (Android), push de apps bancarias (Android), **email IMAP (iOS —
   SMS es imposible en Apple)**, Binance API, CSV, manual. Los parsers son canal-agnósticos:
   el mismo parser Banesco sirve SMS y email.
3. **iOS vía Kotlin Multiplatform + Compose Multiplatform 1.8.2** (una sola UI para ambos).
   El usuario tiene Mac para Xcode.
4. **Modo API bancario (futuro)**: KYC obligatorio (cédula + selfie), **pull solo cuando el
   usuario refresca** (cero polling, cero webhooks), read-only. La empresa del usuario es
   vendor de Banesco/Bancaribe (portales), BNC (onboarding), TodoTicket/CestaTicket (core) —
   esa relación es la puerta de negociación, con tracción medida primero.
5. **Monetización**: FREE SIN ADS (la confianza es el producto) → premium $2-4/mes (rails:
   Apple IAP, pago móvil/Zinli/USDT) → B2B white-label para bancos (la joya, dado los
   contratos existentes) → data agregada anónima opt-in (largo plazo).
6. **Instituciones**: todos los bancos VE + Mercantil Panamá + Banesco + Zinli/Ubii/Wally +
   TodoTicket/CestaTicket + Cashea + Binance. Packages de apps mapeados de forma observable
   (nada bloquea la captura por package desconocido).

## Investigación de bancos (sep-2026)

- Ningún banco VE/PA tiene API pública de consumidor (Belvo solo MX/BR/CO). Binance sí
  (llaves read-only, sin geo-bloqueo en VE). Cashea solo API de comercios.
- 2FA hostil: Banesco Clave Dinámica, Bancaribe coordenadas, BNC Token, BDV Ami Ven.
- BDV SMS banking 2661/2662 (CSC=saldo) — posible feature de consulta manual por SMS.
- Binance OAuth existe pero solo para partners empresariales (KYB + auditoría).

## Roadmap pendiente

| Fase | Alcance | Gate |
|---|---|---|
| **M4** | UI a shared (CMP, koinViewModel, navigation JetBrains 2.9.2, icons 1.7.3, FilePicker/Permissions expect, BackgroundScheduler interface, Hilt eliminado) | Smoke manual completo Android |
| **M5** | iOS bootstrap en la Mac (targets iOS, actuals iosMain, wizard KMP, signing personal team) | Dashboard en iPhone |
| **M6** | Conector IMAP (TlsSocket NSStream por KTOR-2749, ImapClient common, SourceType.EMAIL, BGAppRefresh) | Compra real → email → transacción |
| **M7** | Parser CSV común (RFC-4180) + UIDocumentPicker + pulido | Regresión ambas plataformas |
| Tuning | Corpus de SMS/push/email reales del usuario → afinar parsers v1 | — |
| Modo API | KYC + conectores pull por banco, empezando por los de la empresa | — |

## Toolchain (Linux)

- JDK: Temurin 21 en `~/Android/jdk21` (el 26 del sistema NO sirve para AGP).
- `gradle.properties` fija `org.gradle.java.home`. Gradle 8.13 vía wrapper.
- SDK Android en `~/Android/Sdk` (build-tools 34–36, platforms 35–37).
- Build: `./gradlew :shared:testDebugUnitTest :app:assembleDebug`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Instalar: `adb install -r app-debug.apk` (USB funciona; la depuración inalámbrica de Samsung
  queda zombi con la pantalla bloqueada — el puerto cambia en cada re-activación).

## Gotchas técnicos aprendidos

- Ktor: `expectSuccess = true` es obligatorio (default false → no lanza en 4xx).
- `ConnectTimeoutException` de Ktor vive en `io.ktor.client.network.sockets`.
- Koin 4.1.1: para obtener el Context usar `GlobalContext.get().get<Context>()` (el getter
  `org.koin.android.ext.koin.androidContext` no resuelve como accessor).
- `kotlin.system.getTimeMillis()` no es common → helper `nowMillis()` con kotlin.time.Clock
  (@OptIn ExperimentalTime).
- kotlinx-datetime 0.7: `Clock`/`TimeZone.currentSystemDefault()` requieren opt-in.
- okio: `String.encodeToByteArray().toByteString()` (el companion `encodeUtf8` está deprecado).
- KSP en KMP: `add("kspCommonMainMetadata", libs.room.compiler)` con plugin `androidx.room`.
- Smart casts fallan entre módulos → usar `?.let` y variables locales.
- `git mv` de directorios exige que el padre destino exista.
- KTOR-2749: TLS de ktor-network roto en iOS → el IMAP de M6 usará NSStream+CFStream actual.
- material-icons-extended no publica iOS en CMP 1.8 → pinear `compose.materialIconsExtended`
  (resuelve al build multiplataforma 1.7.3).

## Referencias

- Plan completo: `~/.claude/plans/quiero-hacer-un-app-purring-diffie.md`
- Esquema Room: `shared/schemas/dev.tubolsillo.app.core.db.TubolsilloDatabase/1.json`
- Catálogo de versiones: `gradle/libs.versions.toml`
