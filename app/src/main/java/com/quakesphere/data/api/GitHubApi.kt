package com.quakesphere.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Minimal GitHub Releases v3 wrapper, used only for the in-app self-updater.
 *
 * We pin to the `releases/latest` endpoint specifically — it skips drafts and
 * pre-releases, so users only get pinged when there's a real published build.
 */
interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo")  repo: String
    ): GitHubRelease
}

data class GitHubRelease(
    /** e.g. "v0.1.2" */
    @SerializedName("tag_name") val tagName: String,
    /** e.g. "QuakeStation v0.1.2" */
    @SerializedName("name") val name: String?,
    /** Release notes markdown. */
    @SerializedName("body") val body: String?,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("assets") val assets: List<GitHubAsset> = emptyList()
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("size") val sizeBytes: Long
)
