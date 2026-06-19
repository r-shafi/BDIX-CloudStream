package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class BdixDhakaFlixCombinedProvider : MainAPI() {

    private data class ServerConfig(
        val url: String,
        val serverName: String,
        val useAltPoster: Boolean = false
    )

    // `levels` = how many intermediate category folders sit between this path and the
    // actual movie/TV folders (the ones containing media files). The home page descends
    // exactly this many levels so each row shows playable movie/TV folders, never the
    // in-between category folders (years, languages, alphabetical groups, sections).
    private data class ServerPath(val server: ServerConfig, val path: String, val levels: Int = 0)

    private val servers = listOf(
        ServerConfig("http://172.16.50.7", "DHAKA-FLIX-7"),
        ServerConfig("http://172.16.50.9", "DHAKA-FLIX-9", useAltPoster = true),
        ServerConfig("http://172.16.50.12", "DHAKA-FLIX-12", useAltPoster = true),
        ServerConfig("http://172.16.50.14", "DHAKA-FLIX-14"),
    )

    private val tvSeriesPatterns = listOf(
        "KOREAN TV & WEB Series",
        "Anime & Cartoon TV Series",
        "TV-WEB-Series",
        "Documentary",
        "WWE & AEW Wrestling",
        "Awards & TV Shows",
        "Tutorial",
    )

    private val categoryMap = listOf(
        "English Movies" to listOf(
            // -> year folders -> movie folders
            ServerPath(servers[0], "English Movies/", levels = 1),
            ServerPath(servers[3], "English Movies (1080p)/", levels = 1),
        ),
        "Indian Movies" to listOf(
            ServerPath(servers[0], "Kolkata Bangla Movies/", levels = 1),        // -> year -> movie
            ServerPath(servers[0], "Foreign Language Movies/Bangla Dubbing Movies/"), // -> movie folders directly
            ServerPath(servers[3], "Hindi Movies/", levels = 1),                 // -> year -> movie
            ServerPath(servers[3], "SOUTH INDIAN MOVIES/Hindi Dubbed/", levels = 1), // -> year -> movie
            ServerPath(servers[3], "SOUTH INDIAN MOVIES/South Movies/", levels = 1), // -> year -> movie
        ),
        "Animation Movies" to listOf(
            ServerPath(servers[3], "Animation Movies (1080p)/"),                 // -> movie folders directly
        ),
        "Foreign Language Movies" to listOf(
            ServerPath(servers[0], "Foreign Language Movies/", levels = 1),      // -> language -> movie
        ),
        "IMDb Top-250 Movies" to listOf(
            ServerPath(servers[3], "IMDb Top-250 Movies/"),                      // -> movie folders directly
        ),
        "Korean TV & WEB Series" to listOf(
            ServerPath(servers[3], "KOREAN TV %26 WEB Series/"),                 // -> series folders directly
        ),
        "Anime & Cartoon TV Series" to listOf(
            ServerPath(servers[1], "Anime %26 Cartoon TV Series/", levels = 1),  // -> alphabetical group -> series
        ),
        "Documentary" to listOf(
            ServerPath(servers[1], "Documentary/"),                              // -> series folders directly
        ),
        "WWE & AEW Wrestling" to listOf(
            ServerPath(servers[1], "WWE %26 AEW Wrestling/", levels = 1),        // -> WWE/AEW section -> shows
        ),
        "TV Series" to listOf(
            ServerPath(servers[2], "TV-WEB-Series/", levels = 1),               // -> alphabetical group -> series
        ),
        "3D Movies" to listOf(
            ServerPath(servers[0], "3D Movies/"),                                // -> movie folders directly
        ),
        "Awards & TV Shows" to listOf(
            ServerPath(servers[1], "Awards %26 TV Shows/", levels = 1),          // -> section -> shows
        ),
        "Tutorials" to listOf(
            ServerPath(servers[1], "Tutorial/"),                                 // -> tutorial folders directly
        ),
    )

    override var mainUrl = "http://172.16.50.14"
    override var name = "(BDIX) DhakaFlix"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.AnimeMovie, TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        *categoryMap.map { (n, _) -> n to n }.toTypedArray()
    )

    // Limit how many requests run at once and how many category folders are descended
    // per level, so a deep/wide directory can't fire hundreds of requests and stall.
    private val maxConcurrentRequests = 12
    private val maxBranchesPerLevel = 60

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val paths = categoryMap.find { it.first == request.name }?.second
            ?: return newHomePageResponse(request.name, emptyList(), false)

        val semaphore = Semaphore(maxConcurrentRequests)

        val allResults = coroutineScope {
            paths.map { sp ->
                async {
                    try {
                        flattenToDepth(buildServerUrl(sp.server, sp.path), sp.server, sp.levels, semaphore)
                    } catch (_: Exception) {
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten()
        }

        return newHomePageResponse(request.name, allResults, false)
    }

    // Descend exactly `levels` category folders, then return the folders at that level as
    // items. At `levels == 0` the current directory's folders are the movie/TV folders we
    // want to show, so they are returned directly without descending into them.
    private suspend fun flattenToDepth(
        url: String, server: ServerConfig, levels: Int, semaphore: Semaphore
    ): List<SearchResponse> {
        val doc = semaphore.withPermit { app.get(url).document }
        val childFolders = doc.select("tbody > tr:gt(1)").mapNotNull { post ->
            parsePostResult(post, server)
        }
        if (levels <= 0) return childFolders

        return coroutineScope {
            childFolders.take(maxBranchesPerLevel).map { folder ->
                async {
                    try {
                        flattenToDepth(folder.url, server, levels - 1, semaphore)
                    } catch (_: Exception) {
                        emptyList<SearchResponse>()
                    }
                }
            }.awaitAll().flatten()
        }
    }

    private fun buildServerUrl(server: ServerConfig, path: String): String {
        return if (path.startsWith("/")) {
            "${server.url}$path"
        } else {
            "${server.url}/${server.serverName}/$path"
        }
    }

    private fun parsePostResult(post: Element, server: ServerConfig): SearchResponse? {
        val folderHtml = post.select("td.fb-n > a")
        val isFolder = post.select("td.fb-i > img").attr("alt") == "folder"
        if (!isFolder) return null
        val title = folderHtml.text()
        val url = server.url + folderHtml.attr("href")
        return newAnimeSearchResponse(title, url, TvType.Movie) {
            addDubStatus(
                dubExist = "Dual" in title || "Multi" in title,
                subExist = "ESub" in title || "MSubs" in title
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val allResults = servers.flatMap { server ->
            try {
                searchOnServer(query, server)
            } catch (_: Exception) {
                emptyList<SearchResponse>()
            }
        }
        return allResults.distinctBy { it.name }.take(40)
    }

    private suspend fun searchOnServer(query: String, server: ServerConfig): List<SearchResponse> {
        val body = "{\"action\":\"get\",\"search\":{\"href\":\"/${server.serverName}/\",\"pattern\":\"$query\",\"ignorecase\":true}}"
            .toRequestBody("application/json".toMediaType())
        val response = app.post("${server.url}/${server.serverName}/", requestBody = body).text
        val searchJson = AppUtils.parseJson<SearchResult>(response)
        return searchJson.search
            .filter { it.size == null }
            .take(20)
            .map { post ->
                val fullUrl = server.url + post.href
                val title = nameFromUrl(post.href)
                newAnimeSearchResponse(title, fullUrl) {
                    addDubStatus(
                        dubExist = "Dual" in title || "Multi" in title,
                        subExist = "ESub" in title || "MSubs" in title
                    )
                }
            }
    }

    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")

    private fun nameFromUrl(href: String): String {
        val decoded = URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        return nameRegex.find(decoded)?.groups?.get(1)?.value.toString()
    }

    private fun getServerForUrl(url: String): ServerConfig {
        return servers.find { url.contains(it.url) } ?: servers[0]
    }

    private fun isTvSeriesUrl(url: String): Boolean {
        val decoded = try {
            URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            url
        }
        return tvSeriesPatterns.any { decoded.contains(it, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse {
        val server = getServerForUrl(url)
        val doc = app.get(url).document

        var imageLink = ""
        val posterEl = doc.select("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]")
        if (posterEl.isNotEmpty()) {
            imageLink = server.url + posterEl.attr("href")
        } else {
            imageLink = if (server.useAltPoster) "${url}a11.jpg" else "${url}a_AL_.jpg"
        }

        val tableHtml = doc.select("tbody > tr:gt(1)")
        val title = nameFromUrl(url)

        if (isTvSeriesUrl(url)) {
            val episodesData = mutableListOf<Episode>()
            var seasonNum = 0
            tableHtml.forEach {
                seasonNum++
                val aHtml = it.selectFirst("td.fb-n > a")
                val link = server.url + aHtml?.attr("href")
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonExtractor(link, episodesData, seasonNum, server)
                } else if (aHtml?.selectFirst("a[href~=(?i)\\.(mkv|mp4)]") != null) {
                    val epName = aHtml.text()
                    episodesData.add(
                        newEpisode(link) {
                            this.name = epName
                            this.season = 1
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodesData) {
                this.posterUrl = imageLink
            }
        }

        val folderHtml = tableHtml.select("td.fb-n > a[href~=(?i)\\.(mkv|mp4)]")
        if (folderHtml.isNotEmpty()) {
            val movieTitle = folderHtml.text().toString()
            val link = server.url + folderHtml.attr("href")
            return newMovieLoadResponse(movieTitle, url, TvType.Movie, link) {
                this.posterUrl = imageLink
            }
        }

        val subItems = tableHtml.mapNotNull { post ->
            val a = post.select("td.fb-n > a")
            val isFolder = post.select("td.fb-i > img").attr("alt") == "folder"
            if (isFolder && a.isNotEmpty()) {
                val name = a.text()
                val subUrl = server.url + a.attr("href")
                newAnimeSearchResponse(name, subUrl, TvType.Movie) {
                    addDubStatus(
                        dubExist = "Dual" in name || "Multi" in name,
                        subExist = "ESub" in name || "MSubs" in name
                    )
                }
            } else null
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = imageLink
            this.recommendations = subItems
        }
    }

    private suspend fun seasonExtractor(
        url: String, episodesData: MutableList<Episode>, seasonNum: Int, server: ServerConfig
    ) {
        val doc = app.get(url).document
        var episodeNum = 0
        doc.select("tbody > tr:gt(1) > td.fb-n > a[href~=(?i)\\.(mkv|mp4)]").forEach {
            episodeNum++
            val folderHtml = it.select("a")
            val name = folderHtml.text()
            val link = server.url + folderHtml.attr("href")
            episodesData.add(
                newEpisode(link) {
                    this.name = name
                    this.season = seasonNum
                    this.episode = episodeNum
                }
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                data, this.name, url = data, type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    data class SearchResult(val search: List<Search>)

    data class Search(
        val fetched: Boolean,
        val href: String,
        val managed: Boolean,
        val size: Long?,
        val time: Long
    )
}
