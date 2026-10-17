package net.ddns.shafi

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

//suspend fun main() {
//    val providerTester = com.lagradost.cloudstreamtest.ProviderTester(SrzoneLiveTVProvider())
////    providerTester.testMainPage(verbose = true)
////    providerTester.testLoad("http://srzone.live/live-tv/btv.html")
////    providerTester.testSearch("btv")
//}

class SrzoneLiveTVProvider : MainAPI() {
    override var mainUrl = "http://srzone.live"
    override var name = "(BDIX) SRZone Live TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/live-tv.html").document
        val home = doc.select("section").mapNotNull { section ->
            getCategory(section)
        }
        return newHomePageResponse(home, hasNext = false)
    }

    private fun getCategory(section: Element): HomePageList? {
        val channels = section.select("div.product").mapNotNull { getResult(it) }
        if (channels.isEmpty()) return null
        val title = section.selectFirst("header.title-dash h5")?.text()?.trim()
        return HomePageList(title ?: "Live TV", channels, isHorizontalImages = false)
    }

    private fun getResult(product: Element): LiveSearchResponse? {
        val link = product.selectFirst("a[href]")?.attr("href")?.takeIf { it.isNotBlank() }
            ?: return null
        val image = product.selectFirst("img")?.attr("src")
        val name = product.selectFirst(".product-title")?.text()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: link.substringAfterLast("/").substringBeforeLast(".html").replace("-", " ")
        return newLiveSearchResponse(name, link) {
            this.posterUrl = image
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        if (query.isBlank()) return emptyList()
        val doc = app.get("$mainUrl/live-tv.html").document
        return doc.select("div.product")
            .mapNotNull { getResult(it) }
            .distinctBy { it.url }
            .filter {
                partialRatioLevenshtein(it.name.lowercase(), query.lowercase()) >= 70
            }
    }

    private val sourceRegex = Regex("\"source\"\\s*:\\s*\"([^\"]+)\"")

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        // The player config embeds a tokenized HLS url, e.g.
        // window.OvooVideojs.mount({"source":"http:\/\/cdn.srzone.live:8083\/btv\/index.m3u8?token=..."})
        // cdn.srzone.live resolves to an ISP-internal BDIX mirror that does not serve the
        // HLS port (:8083) on every network, so always use the public CDN host instead.
        // The token is not host-bound and stays valid for ~3h, so it works there too.
        val source = sourceRegex.find(doc.html())
            ?.groupValues?.get(1)
            ?.replace("\\/", "/")
            ?.replace("cdn.srzone.live", "cdn1.srzone.live")
            ?.takeIf { it.startsWith("http") }
            .orEmpty()
        val name = doc.selectFirst("meta[property=\"og:title\"]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.title().substringBeforeLast("-").trim()
        val poster = doc.selectFirst("meta[property=\"og:image\"]")?.attr("content")?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: doc.selectFirst("video[poster]")?.attr("poster")
        return newLiveStreamLoadResponse(name = name, url = url, dataUrl = source) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = data,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
            }
        )
        return true
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) {
            dp[i][0] = i
        }
        for (j in 0..n) {
            dp[0][j] = j
        }

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost)
            }
        }
        return dp[m][n]
    }

    private fun partialRatioLevenshtein(s1: String, s2: String): Int {
        val shorter: String
        val longer: String

        if (s1.length <= s2.length) {
            shorter = s1
            longer = s2
        } else {
            shorter = s2
            longer = s1
        }

        val n = shorter.length
        if (n == 0) return if (longer.isEmpty()) 100 else 0

        var minDistance = longer.length
        for (i in 0..longer.length - n) {
            val sub = longer.substring(i, i + n)
            val distance = levenshteinDistance(shorter, sub)
            minDistance = minOf(minDistance, distance)
        }

        // Normalize the distance to a 0-100 scale
        // A distance of 0 is a perfect match (score 100)
        val similarity = ((n - minDistance).toDouble() / n) * 100
        return similarity.toInt()
    }
}
