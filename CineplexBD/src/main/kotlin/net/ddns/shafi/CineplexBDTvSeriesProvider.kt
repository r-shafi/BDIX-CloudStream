package net.ddns.shafi

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class CineplexBDTvSeriesProvider : MainAPI() {
    override var mainUrl = "http://cineplexbd.net"
    override var name = "(BDIX) CinePlex TV Series"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.AsianDrama,
        TvType.Cartoon,
        TvType.Documentary
    )

    override val mainPage = mainPageOf(
        "Bangla+Series" to "Bangla Series",
        "Bangla+Tv+Series" to "Bangla TV Series",
        "Bangla+Drama" to "Bangla Drama",
        "Web+Series" to "Web Series",
        "Hindi+Series" to "Hindi Series",
        "English+Series" to "English Series",
        "Korean+Series" to "Korean Series",
        "Japanese+Series" to "Japanese Series",
        "Chinese+Series" to "Chinese Series",
        "Pakistani+Series" to "Pakistani Series",
        "Animation+Series" to "Animation Series",
        "Documentary" to "Documentary",
        "Islamic+Series" to "Islamic Series"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/tcategory.php?category=${request.data}&page=$page").document
        val items = doc.select("a[href*=watch.php]")
        val home = items.mapNotNull { item ->
            val link = item.attr("href")
            val img = item.selectFirst("img") ?: return@mapNotNull null
            val title = img.attr("alt")
            val poster = img.attr("src")
            val url = if (link.startsWith("http")) link else "$mainUrl/$link"

            newMovieSearchResponse(title, url, TvType.TvSeries) {
                this.posterUrl = if (poster.startsWith("http")) poster else "$mainUrl/$poster"
            }
        }
        return newHomePageResponse(request.name, home, home.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.get("$mainUrl/search_ajax.php?q=$query&limit=20").text
        val results = AppUtils.parseJson<CineplexBDMoviesProvider.SearchResults>(json)
        return results.results.mapNotNull { item ->
            if (item.type != "series") return@mapNotNull null
            newMovieSearchResponse(item.title, "$mainUrl${item.url}", TvType.TvSeries) {
                this.posterUrl = if (item.poster.startsWith("http")) item.poster
                    else "$mainUrl${item.poster}"
                item.year?.toIntOrNull()?.let { this.year = it }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.title().replace(" — Watch", "").replace(" — Player", "").trim()

        val poster = doc.selectFirst("img[src*=uploads/posters]")?.attr("src") ?: ""

        val episodes = mutableListOf<Episode>()
        val seriesId = Regex("""[?&](?:id|series_id)=(\d+)""").find(url)?.groupValues?.get(1) ?: ""
        val seasonLinks = doc.select("form select[name=season] option")

        val seasons = if (seasonLinks.isEmpty()) listOf("1")
            else seasonLinks.map { it.attr("value") }

        seasons.forEach { season ->
            val seasonUrl = "$mainUrl/watch.php?id=$seriesId&season=$season"
            val seasonDoc = if (season != seasons.first()) app.get(seasonUrl).document else doc
            val epItems = seasonDoc.select("li[id^=ep-]")

            epItems.forEach { ep ->
                val epLink = ep.selectFirst("a.ep-card")?.attr("href") ?: return@forEach
                val epNum = ep.selectFirst("a.ep-card")?.attr("data-ep")?.toIntOrNull()
                val epImg = ep.selectFirst("img")?.attr("src") ?: ""
                val epUrl = if (epLink.startsWith("http")) epLink else "$mainUrl$epLink"

                episodes.add(
                    newEpisode(epUrl) {
                        this.name = "Episode $epNum"
                        this.season = season.toIntOrNull()
                        this.episode = epNum
                        this.posterUrl = if (epImg.startsWith("http")) epImg else "$mainUrl/$epImg"
                    }
                )
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = if (poster.startsWith("http")) poster else "$mainUrl/$poster"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val html = app.get(data).text
        val src = Regex("""<source[^>]*src="([^"]+)"[^>]*>""").find(html)
        val srcUrl = src?.groupValues?.get(1) ?: return false
        val videoUrl = if (srcUrl.startsWith("http")) srcUrl else "$mainUrl$srcUrl"

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
}
