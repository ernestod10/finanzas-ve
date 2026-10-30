package dev.tubolsillo.app.data.source.github

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El actualizador contra un JSON real de la API de GitHub Releases. */
class GitHubReleasesApiTest {

    private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

    private fun api(engine: MockEngine) = GitHubReleasesApi(
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
    )

    private val releaseJson = """
        {
          "tag_name": "v0.2.0",
          "name": "TuBolsillo 0.2.0",
          "body": "Arregla la lectura de las tarjetas de crédito.",
          "html_url": "https://github.com/ernestod10/finanzas-ve/releases/tag/v0.2.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "TuBolsillo-0.2.0.apk",
              "browser_download_url": "https://github.com/ernestod10/finanzas-ve/releases/download/v0.2.0/TuBolsillo-0.2.0.apk",
              "size": 17757096
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `lee la ultima release y elige el apk`() = runTest {
        val engine = MockEngine { respond(releaseJson, HttpStatusCode.OK, jsonHeader) }
        val info = api(engine).latest().getOrNull()

        assertEquals("0.2.0", info?.versionName)
        assertTrue(info!!.apkUrl.endsWith("TuBolsillo-0.2.0.apk"))
        assertEquals("https://github.com/ernestod10/finanzas-ve/releases/tag/v0.2.0", info.pageUrl)
        assertTrue(info.notes!!.contains("tarjetas"))
    }

    @Test
    fun `sin apk entre los assets no hay actualizacion`() = runTest {
        val engine = MockEngine {
            respond(
                """{"tag_name":"v0.2.0","assets":[{"name":"notas.txt","browser_download_url":"https://x/y.txt"}]}""",
                HttpStatusCode.OK,
                jsonHeader,
            )
        }
        assertNull(api(engine).latest().getOrNull())
    }

    @Test
    fun `un 404 del repo significa que aun no hay releases`() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val result = api(engine).latest()
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `una respuesta con basura es un fallo, no un falso al dia`() = runTest {
        val engine = MockEngine { respond("<html>no json</html>", HttpStatusCode.OK, jsonHeader) }
        assertTrue(api(engine).latest().isFailure)
    }
}
