package net.ddns.shafi

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
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.addSeasonNames
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class CtgFunMediaProvider : MainAPI() {

    private val baseUrl = "https://media.ctgfun.com"

    private data class CategoryPath(val path: String, val levels: Int = 0)

    private val tvSeriesCategoryNames = setOf(
        "TV SERIES", "TV SERIES [PART-2]", "TV SERIES [PART-3]", "TV SERIES [PART-4]"
    )

    private val categoryMap = listOf(
        "Recent English Movies" to listOf(
            CategoryPath("/disk1/Recent English Movies/", levels = 1)
        ),
        "English Movies (2014 & Old)" to listOf(
            CategoryPath("/disk2/English Movies (2014 & Old)/")
        ),
        "English Movies (2015)" to listOf(
            CategoryPath("/disk2/English Movies (2015)/")
        ),
        "English Movies (2016)" to listOf(
            CategoryPath("/disk2/English Movies (2016)/")
        ),
        "Hindi Movies" to listOf(
            CategoryPath("/disk3/Hindi Movies/", levels = 1)
        ),
        "IMDb Top 250 Movies" to listOf(
            CategoryPath("/disk4/IMDb Top 250 Movies/")
        ),
        "Pakistani Movies" to listOf(
            CategoryPath("/disk4/Pakistani Movies/")
        ),
        "South Indian Movies" to listOf(
            CategoryPath("/disk4/South Indian Movies/")
        ),
        "Asian Movies" to listOf(
            CategoryPath("/disk6/Asian Movies/")
        ),
        "Bengali Movies" to listOf(
            CategoryPath("/disk6/Bengali Movies/")
        ),
        "European Movies" to listOf(
            CategoryPath("/disk6/European Movies/")
        ),
        "4K Movies Collection" to listOf(
            CategoryPath("/disk11/4K Movies Collection/")
        ),
        "Movie Franchise Collections" to listOf(
            CategoryPath("/disk5/MOVIE SERIES/")
        ),
        "TV Series [Part 2]" to listOf(
            CategoryPath("/disk8/TV SERIES [PART-2]/")
        ),
        "TV Series [Part 3]" to listOf(
            CategoryPath("/disk5/TV SERIES [PART-3]/")
        ),
        "TV Series [Part 4]" to listOf(
            CategoryPath("/disk7/TV SERIES [PART-4]/")
        ),
    )

    override var mainUrl = baseUrl
    override var name = "(BDIX) CtgFun Media"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val instantLinkLoading = true
    override var lang = "bn"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        *categoryMap.map { (n, _) -> n to n }.toTypedArray()
    )

    private val maxBranchesPerLevel = 60

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val paths = categoryMap.find { it.first == request.name }?.second
            ?: return newHomePageResponse(request.name, emptyList(), false)

        val allResults = paths.flatMap { cp ->
            try {
                flattenToDepth("$baseUrl${cp.path}", cp.levels)
            } catch (_: Exception) {
                emptyList<SearchResponse>()
            }
        }

        return newHomePageResponse(request.name, allResults, false)
    }

    private suspend fun flattenToDepth(
        url: String, levels: Int
    ): List<SearchResponse> {
        val doc = app.get(url).document
        val childFolders = doc.select("tbody > tr:gt(1)").mapNotNull { post ->
            parsePostResult(post)
        }
        if (levels <= 0) return childFolders

        return childFolders.take(maxBranchesPerLevel).amap { folder ->
            try {
                flattenToDepth(folder.url, levels - 1)
            } catch (_: Exception) {
                emptyList<SearchResponse>()
            }
        }.flatten()
    }

    private fun parsePostResult(post: Element): SearchResponse? {
        val folderHtml = post.select("td.fb-n > a")
        val isFolder = post.select("td.fb-i > img").attr("alt") == "folder"
        if (!isFolder) return null
        val title = folderHtml.text()
        val url = baseUrl + folderHtml.attr("href")
        val tvType = if (isTvSeriesUrl(url)) TvType.TvSeries else TvType.Movie
        return newAnimeSearchResponse(title, url, tvType) {
            this.posterUrl = "${url}a_AL_.jpg"
            this.year = extractYear(title)
            this.quality = extractQuality(title)
            addDubStatus(
                dubExist = "Dual" in title || "Multi" in title,
                subExist = "ESub" in title || "MSubs" in title
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val body = "{\"action\":\"get\",\"search\":{\"href\":\"/\",\"pattern\":\"$query\",\"ignorecase\":true}}"
                .toRequestBody("application/json".toMediaType())
            val response = app.post("$baseUrl/", requestBody = body).text
            val searchJson = AppUtils.parseJson<SearchResult>(response)
            searchJson.search
                .filter { it.size == null }
                .take(20)
                .map { post ->
                    val fullUrl = baseUrl + post.href
                    val title = nameFromUrl(post.href)
                    val tvType = if (isTvSeriesUrl(fullUrl)) TvType.TvSeries else TvType.Movie
                    newAnimeSearchResponse(title, fullUrl, tvType) {
                        this.year = extractYear(title)
                        this.quality = extractQuality(title)
                        addDubStatus(
                            dubExist = "Dual" in title || "Multi" in title,
                            subExist = "ESub" in title || "MSubs" in title
                        )
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val yearRegex = Regex("""\((\d{4})""")
    private fun extractYear(name: String): Int? =
        yearRegex.find(name)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractQuality(name: String): SearchQuality? = when {
        "4K" in name || "2160p" in name -> SearchQuality.FourK
        "1080p" in name -> SearchQuality.HD
        "720p" in name -> SearchQuality.HD
        "480p" in name -> SearchQuality.SD
        else -> null
    }

    private val tagRegex = Regex("""\[([^\]]+)\]""")
    private fun extractTags(name: String): List<String>? =
        tagRegex.findAll(name).map { it.groupValues[1] }.toList().ifEmpty { null }

    private val nameRegex = Regex(""".*/([^/]+)(?:/[^/]*)*$""")

    private fun nameFromUrl(href: String): String {
        val decoded = URLDecoder.decode(href, StandardCharsets.UTF_8.toString())
        return nameRegex.find(decoded)?.groups?.get(1)?.value.toString()
    }

    private fun isTvSeriesUrl(url: String): Boolean {
        val decoded = try {
            URLDecoder.decode(url, StandardCharsets.UTF_8.toString())
        } catch (_: Exception) {
            url
        }
        return tvSeriesCategoryNames.any { decoded.contains(it, ignoreCase = true) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        var imageLink = ""
        val posterEl = doc.select("td.fb-n > a[href~=(?i)\\.(png|jpe?g)]")
        if (posterEl.isNotEmpty()) {
            imageLink = baseUrl + posterEl.attr("href")
        } else {
            imageLink = "${url}a_AL_.jpg"
        }

        val tableHtml = doc.select("tbody > tr:gt(1)")
        val title = nameFromUrl(url)

        if (isTvSeriesUrl(url)) {
            val episodesData = mutableListOf<Episode>()
            val seasonNames = mutableListOf<SeasonData>()
            var seasonNum = 0
            tableHtml.forEach {
                seasonNum++
                val aHtml = it.selectFirst("td.fb-n > a")
                val link = baseUrl + aHtml?.attr("href")
                if (it.selectFirst("td.fb-i > img")?.attr("alt") == "folder") {
                    seasonNames.add(SeasonData(season = seasonNum, name = aHtml?.text()))
                    seasonExtractor(link, episodesData, seasonNum)
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
                this.year = extractYear(title)
                this.tags = extractTags(title)
                if (seasonNames.isNotEmpty()) addSeasonNames(seasonNames)
            }
        }

        val folderHtml = tableHtml.select("td.fb-n > a[href~=(?i)\\.(mkv|mp4)]")
        if (folderHtml.isNotEmpty()) {
            val movieTitle = folderHtml.text().toString()
            val link = baseUrl + folderHtml.attr("href")
            return newMovieLoadResponse(movieTitle, url, TvType.Movie, link) {
                this.posterUrl = imageLink
                this.year = extractYear(movieTitle)
                this.tags = extractTags(movieTitle)
            }
        }

        val subItems = tableHtml.mapNotNull { post ->
            val a = post.select("td.fb-n > a")
            val isFolder = post.select("td.fb-i > img").attr("alt") == "folder"
            if (isFolder && a.isNotEmpty()) {
                val name = a.text()
                val subUrl = baseUrl + a.attr("href")
                newAnimeSearchResponse(name, subUrl, TvType.Movie) {
                    this.year = extractYear(name)
                    this.quality = extractQuality(name)
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
        url: String, episodesData: MutableList<Episode>, seasonNum: Int
    ) {
        val doc = app.get(url).document
        var episodeNum = 0
        doc.select("tbody > tr:gt(1) > td.fb-n > a[href~=(?i)\\.(mkv|mp4)]").forEach {
            episodeNum++
            val folderHtml = it.select("a")
            val name = folderHtml.text()
            val link = baseUrl + folderHtml.attr("href")
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
