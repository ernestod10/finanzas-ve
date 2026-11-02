# TuBolsillo

Tu dinero, claro y en orden. App **Kotlin Multiplatform + Compose** para llevar
las finanzas personales en Venezuela: bancos (Banesco, Bancaribe, BNC, BDV,
Mercantil…), Binance y Cashea, todo **en el teléfono**: no hay backend y ningún
dato financiero sale del dispositivo.

## Qué hace

- **Captura automática**: lee SMS bancarios y notificaciones, y consulta los
  portales de los bancos con las credenciales guardadas y cifradas.
- **Binance**: saldos, depósitos, retiros, P2P y Binance Pay con llaves de solo
  lectura.
- **Tasas**: BCV (dólar y euro) y USDT P2P, con snapshot para no perder el
  histórico cuando el banco central no publica.
- **Plan del mes**: ingresos y gastos estimados por fecha, enfrentados a lo real.
- **Estadísticas**: tendencia del saldo y estructura del gasto.
- **IA on-device**: Gemini Nano (AICore) para interpretar capturas, descartar
  ruido, sugerir categorías y redactar el resumen del mes. Si el teléfono no
  tiene Nano, se importa un modelo `.task` (LiteRT/MediaPipe) — la app
  recomienda cuál según la RAM.
- **Búsqueda en lenguaje natural**: "¿cuánto gasté en farmacia en septiembre?"
  El modelo solo produce el filtro; la consulta la hace la app.
- **Mapeo de CSV con IA**: un estado de cuenta de un banco desconocido se mapea
  solo (el usuario revisa antes de importar).
- **Actualizaciones fuera de la Play Store**: la app lee la última release de
  este repo y se descarga el APK.

## Estructura

```
app/     UI Android delgada (MainActivity, servicios, WebView de portales, AICore)
shared/  Todo lo demás: dominio, Room, repos, parsers, UI Compose común y tests
iosApp/  Envoltorio de iOS
```

## Compilar

Requiere JDK 21 y el Android SDK (compileSdk 35).

```bash
./gradlew :shared:testDebugUnitTest     # unit tests + migraciones (JVM)
./gradlew :app:assembleRelease          # APK arm64-v8a firmado
```

En local el release se firma con la clave de debug. En CI se pasa el keystore
por variables de entorno (`TUBOLSILLO_KEYSTORE`, `TUBOLSILLO_KEYSTORE_PASSWORD`,
`TUBOLSILLO_KEY_ALIAS`, `TUBOLSILLO_KEY_PASSWORD`) — **tiene que ser el mismo
keystore**, o Android rechaza la actualización por firma distinta.

## Publicar una versión

```bash
git tag v0.2.0
git push origin v0.2.0
```

El workflow `.github/workflows/release.yml` corre los tests, compila el APK con
`versionName` del tag y `versionCode` del número de ejecución, y lo publica en
GitHub Releases. Las instalaciones existentes lo ven en **Menú → Aplicación**.

## Tests

- Unitarios de dominio y parsers (Kotlin common, sin emulador).
- Migraciones de Room 4→5 y 5→6 sobre SQLite en memoria.
- `MockEngine` de Ktor para las APIs de Binance y de GitHub Releases.

```bash
./gradlew :shared:testDebugUnitTest
```

## Privacidad

Las llaves de API, las credenciales de los portales y la base de datos viven en
almacenamiento cifrado/bajo el sandbox de la app. No hay servidores propios ni
telemetría.
