package net.ddns.shafi

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class CineplexBDMoviesProvider : MainAPI() {
    override var mainUrl = "http://cineplexbd.net"
    override var name = "(BDIX) CinePlex Movies"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.AnimeMovie,
        TvType.Cartoon,
        TvType.Documentary
    )

    override val mainPage = mainPageOf(
        "English" to "English",
        "Hindi" to "Hindi",
        "Bangla+Movies" to "Bangla",
        "Bangla+Dubbed" to "Bangla Dubbed",
        "Animation" to "Animation",
        "Anime" to "Anime",
        "3D+Movies" to "3D Movies",
        "4K+Movies" to "4K Movies",
        "Dual+Audio" to "Dual Audio",
        "Exclusive+Full+HD" to "Exclusive Full HD",
        "Hindi+Dubbed/English+Movies" to "Hindi Dubbed English",
        "Hindi+Dubbed/Korean+Movies" to "Hindi Dubbed Korean",
        "Chinese" to "Chinese",
        "Korean" to "Korean",
        "Japanese" to "Japanese",
        "Foreign" to "Foreign",
        "Documentaries" to "Documentaries",
        "Kids+Cartoon" to "Kids Cartoon"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/category.php?category=${request.data}&page=$page").document
        val items = doc.select("a[href*=view.php]")
        val home = items.mapNotNull { item ->
            val link = item.attr("href")
            val img = item.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt")
            val poster = img.attr("src")
            val url = if (link.startsWith("http")) link else "$mainUrl/$link"

            val qualityBadge = item.selectFirst("div.absolute.top-2.right-2")
            val quality = qualityBadge?.text()

            newMovieSearchResponse(title, url, TvType.Movie) {
                this.posterUrl = if (poster.startsWith("http")) poster else "$mainUrl/$poster"
                quality?.let { this.quality = getSearchQuality(it) }
            }
        }
        return newHomePageResponse(request.name, home, home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.get("$mainUrl/search_ajax.php?q=$query&limit=20").text
        val results = AppUtils.parseJson<SearchResults>(json)
        return results.results.mapNotNull { item ->
            if (item.type != "movie") return@mapNotNull null
            newMovieSearchResponse(item.title, "$mainUrl${item.url}", TvType.Movie) {
                this.posterUrl = if (item.poster.startsWith("http")) item.poster
                    else "$mainUrl/uploads/${item.poster}"
                item.year?.toIntOrNull()?.let { this.year = it }
                qualityFromResolution(item.resolution)?.let { this.quality = it }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: "Unknown"

        val chips = doc.select("span.chip")
        val year = chips.firstOrNull()?.text()?.toIntOrNull()
        val genre = chips.mapNotNull { chip ->
            val text = chip.text()
            if (text.all { it.isDigit() || it == '-' }) null else text
        }.filter { it.isNotEmpty() }

        val poster = doc.selectFirst("img.poster")?.attr("src") ?: ""
        val playerUrl = doc.selectFirst("a[href*=player.php]")?.attr("href") ?: ""
        val dataUrl = if (playerUrl.startsWith("http")) playerUrl else "$mainUrl/$playerUrl"

        return newMovieLoadResponse(title, url, TvType.Movie, dataUrl) {
            this.posterUrl = if (poster.startsWith("http")) poster else "$mainUrl/$poster"
            this.year = year
            this.tags = genre
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text
        val videoSrc = Regex("""const\s+videoSrc\s*=\s*["']([^"']+)["']""").find(html)
        val src = videoSrc?.groupValues?.get(1) ?: return false
        val videoUrl = if (src.startsWith("http")) src else "$mainUrl$src"

        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = "HLS",
                url = videoUrl,
                type = ExtractorLinkType.M3U8
            )
        )

        Regex("""<track[^>]*src="([^"]+)"[^>]*>""").findAll(html).forEach { match ->
            val subUrl = match.groupValues[1]
            if (subUrl.contains(".vtt")) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = "English",
                        url = subUrl
                    )
                )
            }
        }

        return true
    }

    private fun getSearchQuality(text: String): SearchQuality? {
        return when {
            text.contains("4K", ignoreCase = true) -> SearchQuality.FourK
            text.contains("1080", ignoreCase = true) -> SearchQuality.HD
            text.contains("720", ignoreCase = true) -> SearchQuality.HD
            text.contains("HD", ignoreCase = true) -> SearchQuality.HD
            text.contains("CAM", ignoreCase = true) -> SearchQuality.Cam
            else -> null
        }
    }

    private fun qualityFromResolution(resolution: String?): SearchQuality? {
        if (resolution.isNullOrEmpty()) return null
        return when {
            resolution.contains("4K", ignoreCase = true) -> SearchQuality.FourK
            resolution.contains("1080", ignoreCase = true) -> SearchQuality.HD
            resolution.contains("720", ignoreCase = true) -> SearchQuality.HD
            else -> null
        }
    }

    data class SearchResults(
        val results: List<SearchItem>
    )

    data class SearchItem(
        val id: Int,
        val title: String,
        val category: String?,
        val poster: String,
        val year: String?,
        val resolution: String?,
        val type: String?,
        val url: String
    )
}
