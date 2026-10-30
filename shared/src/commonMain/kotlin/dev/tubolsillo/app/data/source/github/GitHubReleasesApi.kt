package dev.tubolsillo.app.data.source.github

import dev.tubolsillo.app.domain.update.UpdateInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Consulta la última release publicada en GitHub. Es la vía de distribución
 * de la app (sin Play Store): cada tag `vX.Y.Z` publica el APK, y la app
 * compara ese número con el suyo.
 */
class GitHubReleasesApi(
    private val client: HttpClient,
    private val repo: String = DEFAULT_REPO,
) {

    /**
     * Última release publicada. `success(null)` significa "el repo todavía no
     * tiene releases" (o la última no trae APK); el fracaso es un problema de
     * red/API, que la UI muestra como error recuperable.
     */
    suspend fun latest(): Result<UpdateInfo?> = runCatching {
        val response = client.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }
        if (response.status == HttpStatusCode.NotFound) return@runCatching null
        if (!response.status.isSuccess()) error("GitHub respondió " + response.status.value)
        val release: GhRelease = response.body()
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return@runCatching null
        val version = release.tagName.removePrefix("v").trim()
        if (version.isBlank() || apk.downloadUrl.isBlank()) return@runCatching null
        UpdateInfo(
            versionName = version,
            apkUrl = apk.downloadUrl,
            notes = release.body.takeIf { it.isNotBlank() },
            pageUrl = release.htmlUrl,
        )
    }

    companion object {
        const val DEFAULT_REPO = "ernestod10/finanzas-ve"
    }
}

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String = "",
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)
