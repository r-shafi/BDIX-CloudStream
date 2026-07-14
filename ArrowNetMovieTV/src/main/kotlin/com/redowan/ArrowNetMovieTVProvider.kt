package com.redowan

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
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class ArrowNetMovieTVProvider : MainAPI() {
    override var mainUrl = "http://103.142.80.21"
    override var name = "(BDIX) ArrowNet Movies & TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val posterBaseUrl = "https://image.tmdb.org/t/p/w500"

    override val mainPage = mainPageOf(
        "movies" to "Movies",
        "tvshows" to "TV Shows"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val limit = 30
        val results = when (request.data) {
            "movies" -> {
                val json = app.get(
                    "$mainUrl/api/v1/movies.php?sort_by=uploadTime+DESC&limit=$limit",
                    verify = false,
                    cacheTime = 60
                ).text
                val movies = AppUtils.parseJson<List<MovieData>>(json)
                movies.mapNotNull { movie ->
                    toMovieSearchResult(movie)
                }
            }
            "tvshows" -> {
                val json = app.get(
                    "$mainUrl/api/v1/tvshows.php?limit=$limit&sort_by=uploadTime+DESC",
                    verify = false,
                    cacheTime = 60
                ).text
                val tvShows = AppUtils.parseJson<List<TvShowData>>(json)
                tvShows.mapNotNull { tvShow ->
                    toTvShowSearchResult(tvShow)
                }
            }
            else -> emptyList()
        }
        return newHomePageResponse(request.name, results, true)
    }

    private fun toMovieSearchResult(movie: MovieData): SearchResponse {
        val posterUrl = if (!movie.poster.isNullOrBlank()) "$posterBaseUrl/${movie.poster}" else null
        return newAnimeSearchResponse(
            "${movie.MovieTitle.trim()} (${movie.MovieYear})",
            "$mainUrl/movie/${movie.id}",
            TvType.Movie
        ) {
            this.posterUrl = posterUrl
            this.year = movie.MovieYear?.toIntOrNull()
            this.quality = getQualityFromString(movie.MovieQuality)
        }
    }

    private fun toTvShowSearchResult(tvShow: TvShowData): SearchResponse {
        val posterUrl = if (!tvShow.TVposter.isNullOrBlank()) "$posterBaseUrl/${tvShow.TVposter}" else null
        return newAnimeSearchResponse(
            "${tvShow.TVtitle.trim()} (${tvShow.TVrelease})",
            "$mainUrl/tvshow/${tvShow.id}",
            TvType.TvSeries
        ) {
            this.posterUrl = posterUrl
            this.year = tvShow.TVrelease?.toIntOrNull()
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val results = mutableListOf<SearchResponse>()

        val moviesJson = app.get(
            "$mainUrl/api/v1/movies.php?sort_by=uploadTime+DESC&limit=50",
            verify = false,
            cacheTime = 60
        ).text
        val movies = AppUtils.parseJson<List<MovieData>>(moviesJson)
        movies.filter {
            it.MovieTitle.contains(query, ignoreCase = true) ||
            it.MovieCategory?.contains(query, ignoreCase = true) == true
        }.forEach { movie ->
            toMovieSearchResult(movie).let { results.add(it) }
        }

        val tvShowsJson = app.get(
            "$mainUrl/api/v1/tvshows.php?limit=50&sort_by=uploadTime+DESC",
            verify = false,
            cacheTime = 60
        ).text
        val tvShows = AppUtils.parseJson<List<TvShowData>>(tvShowsJson)
        tvShows.filter {
            it.TVtitle.contains(query, ignoreCase = true) ||
            it.TVcategory?.contains(query, ignoreCase = true) == true
        }.forEach { tvShow ->
            toTvShowSearchResult(tvShow).let { results.add(it) }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val path = url.removePrefix(mainUrl)

        if (path.startsWith("/movie/")) {
            val movieId = path.removePrefix("/movie/")
            val json = app.get(
                "$mainUrl/api/v1/movies.php?sort_by=uploadTime+DESC&limit=100",
                verify = false,
                cacheTime = 60
            ).text
            val movies = AppUtils.parseJson<List<MovieData>>(json)
            val movie = movies.find { it.id == movieId }
                ?: return newMovieLoadResponse("Unknown", url, TvType.Movie, url)

            val posterUrl = if (!movie.poster.isNullOrBlank()) "$posterBaseUrl/${movie.poster}" else null
            val backdropUrl = if (!movie.backdrops_Poster.isNullOrBlank()) "$posterBaseUrl/${movie.backdrops_Poster}" else null
            val backgroundPosterUrl = backdropUrl ?: posterUrl

            return newMovieLoadResponse(
                "${movie.MovieTitle.trim()} (${movie.MovieYear})",
                movie.MovieWatchLink,
                TvType.Movie,
                movie.MovieWatchLink
            ) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backgroundPosterUrl
                this.year = movie.MovieYear?.toIntOrNull()
                this.plot = movie.MovieStory
                this.score = movie.MovieRatings?.let { Score.from(it, 10) }
                this.duration = movie.MovieRuntime?.toIntOrNull()?.times(60000)
                this.tags = listOfNotNull(movie.MovieCategory, movie.Movielang).filter { it.isNotBlank() }
            }
        }

        if (path.startsWith("/tvshow/")) {
            val tvShowId = path.removePrefix("/tvshow/")
            val json = app.get(
                "$mainUrl/api/v1/tvshows.php?limit=100&sort_by=uploadTime+DESC",
                verify = false,
                cacheTime = 60
            ).text
            val tvShows = AppUtils.parseJson<List<TvShowData>>(json)
            val tvShow = tvShows.find { it.id == tvShowId }
                ?: return newTvSeriesLoadResponse("Unknown", url, TvType.TvSeries, emptyList())

            val posterUrl = if (!tvShow.TVposter.isNullOrBlank()) "$posterBaseUrl/${tvShow.TVposter}" else null

            val episodes = mutableListOf<Episode>()
            try {
                val dirUrl = "$mainUrl/${tvShow.FileLocation.trimStart('.', '/')}/"
                val doc = app.get(dirUrl, verify = false).document
                val links = doc.select("a[href\$=.mp4], a[href\$=.mkv]")

                var episodeNum = 0
                links.forEach { link ->
                    val href = link.attr("href")
                    val name = link.text().ifBlank { href.substringAfterLast("/").substringBeforeLast(".") }
                    if (href.isNotBlank()) {
                        episodeNum++
                        val fullUrl = if (href.startsWith("http")) href else "$mainUrl/${href.trimStart('/')}"
                        episodes.add(newEpisode(fullUrl) {
                            this.name = name
                            this.episode = episodeNum
                        })
                    }
                }
            } catch (_: Exception) {
            }

            return newTvSeriesLoadResponse(
                "${tvShow.TVtitle.trim()} (${tvShow.TVrelease})",
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = posterUrl
                this.year = tvShow.TVrelease?.toIntOrNull()
                this.plot = tvShow.TVstory
                this.score = tvShow.TVRatings?.let { Score.from(it, 10) }
                this.tags = listOfNotNull(tvShow.TVcategory, tvShow.TVgenre).filter { it.isNotBlank() }
            }
        }

        return newMovieLoadResponse("Unknown", url, TvType.Movie, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        callback.invoke(
            newExtractorLink(
                source = this.name,
                name = this.name,
                url = data,
                type = ExtractorLinkType.VIDEO
            )
        )
        return true
    }

    private fun getQualityFromString(quality: String?): com.lagradost.cloudstream3.SearchQuality? {
        return when {
            quality.isNullOrBlank() -> null
            quality.contains("CAM", ignoreCase = true) -> com.lagradost.cloudstream3.SearchQuality.Cam
            quality.contains("HD-RIP", ignoreCase = true) -> com.lagradost.cloudstream3.SearchQuality.HD
            quality.contains("BRRIP", ignoreCase = true) -> com.lagradost.cloudstream3.SearchQuality.BlueRay
            quality.contains("WEBRIP", ignoreCase = true) -> com.lagradost.cloudstream3.SearchQuality.WebRip
            quality.contains("DVD", ignoreCase = true) -> com.lagradost.cloudstream3.SearchQuality.DVD
            else -> null
        }
    }

    data class MovieData(
        val id: String,
        val MovieTitle: String,
        val MovieYear: String?,
        val MovieID: String?,
        val MovieQuality: String?,
        val MovieCategory: String?,
        val MovieTrailer: String?,
        val MovieRatings: String?,
        val MovieGenre: String?,
        val MovieDate: String?,
        val Movielang: String?,
        val Moviehomepage: String?,
        val MovieRuntime: String?,
        val MovieKeywords: String?,
        val MovieStory: String?,
        val MovieWatchLink: String,
        val MovieSubtitle: String?,
        val MovieActors: String?,
        val MovieSize: String?,
        val poster: String?,
        val backdrops_Poster: String?,
        val uploadedUser: String?,
        val uploadTime: String?,
        val views: String?,
        val published: String?,
        val DownHit: String?
    )

    data class TvShowData(
        val id: String,
        val TVtitle: String,
        val TVID: String?,
        val TVcategory: String?,
        val TVtrailer: String?,
        val TVRatings: String?,
        val TVgenre: String?,
        val TVrelease: String?,
        val TVlang: String?,
        val TVhomepage: String?,
        val TVruntime: String?,
        val TVkeywords: String?,
        val TVstory: String?,
        val TVactors: String?,
        val TVposter: String?,
        val TVbackdrops: String?,
        val FileLocation: String,
        val uploadedUser: String?,
        val uploadTime: String?,
        val views: String?,
        val published: String?,
        val completed: String?,
        val missing: String?,
        val last_air_date: String?
    )
}
