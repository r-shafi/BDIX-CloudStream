package com.redowan

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addSeasonNames
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
    private val apiBaseUrl get() = "$mainUrl/api/v1"

    // Movie categories from menu.php (parent: movies) - movies first
    private val movieCategories = listOf(
        "Hollywood", "Bollywood", "Animation", "Foreign", "French", "Chinese",
        "Indian Bangla", "Italian", "Japanese", "Korean", "Malayalam", "Russia",
        "Tamil", "Thailand", "Turkey", "Hong-Kong", "Spanish", "Pakistani",
        "Germany", "3D Movie", "Brasil", "Persian", "Hindi Dubbed",
        "IMDB Top 250", "Panjabi", "Bangla"
    )

    // TV categories from menu.php (parent: Tv Series) - then TVs
    private val tvCategories = listOf(
        "English Tv Series", "Korean Tv Series", "Hindi Tv Series",
        "Bengali Tv Series", "Arabic Tv Series", "Sinhalese TV"
    )

    override val mainPage = mainPageOf(
        *movieCategories.map { "movie_$it" to it }.toTypedArray(),
        *tvCategories.map { "tv_$it" to it }.toTypedArray()
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val limit = 99999
        val data = request.data

        val results = when {
            data.startsWith("movie_") -> {
                val category = data.removePrefix("movie_")
                val json = app.get(
                    "$apiBaseUrl/movies.php?category=$category&sort_by=uploadTime+DESC&limit=$limit",
                    verify = false,
                    cacheTime = 60
                ).text
                val movies = AppUtils.parseJson<List<MovieData>>(json)
                movies.mapNotNull { toMovieSearchResult(it) }
            }
            data.startsWith("tv_") -> {
                val category = data.removePrefix("tv_")
                val json = app.get(
                    "$apiBaseUrl/tvshows.php?category=$category&limit=$limit&sort_by=uploadTime+DESC",
                    verify = false,
                    cacheTime = 60
                ).text
                val tvShows = AppUtils.parseJson<List<TvShowData>>(json)
                tvShows.mapNotNull { toTvShowSearchResult(it) }
            }
            else -> emptyList()
        }

        return newHomePageResponse(request.name, results, false)
    }

    private fun toMovieSearchResult(movie: MovieData): SearchResponse? {
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

    private fun toTvShowSearchResult(tvShow: TvShowData): SearchResponse? {
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
            "$apiBaseUrl/movies.php?sort_by=uploadTime+DESC&limit=99999",
            verify = false,
            cacheTime = 60
        ).text
        val movies = AppUtils.parseJson<List<MovieData>>(moviesJson)
        movies.filter {
            it.MovieTitle.contains(query, ignoreCase = true) ||
            it.MovieCategory?.contains(query, ignoreCase = true) == true
        }.forEach { movie ->
            toMovieSearchResult(movie)?.let { results.add(it) }
        }

        val tvShowsJson = app.get(
            "$apiBaseUrl/tvshows.php?limit=99999&sort_by=uploadTime+DESC",
            verify = false,
            cacheTime = 60
        ).text
        val tvShows = AppUtils.parseJson<List<TvShowData>>(tvShowsJson)
        tvShows.filter {
            it.TVtitle.contains(query, ignoreCase = true) ||
            it.TVcategory?.contains(query, ignoreCase = true) == true
        }.forEach { tvShow ->
            toTvShowSearchResult(tvShow)?.let { results.add(it) }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val path = url.removePrefix(mainUrl)

        if (path.startsWith("/movie/")) {
            val movieId = path.removePrefix("/movie/")
            val json = app.get(
                "$apiBaseUrl/movies.php?sort_by=uploadTime+DESC&limit=99999",
                verify = false,
                cacheTime = 60
            ).text
            val movies = AppUtils.parseJson<List<MovieData>>(json)
            val movie = movies.find { it.id == movieId }
                ?: return newMovieLoadResponse("Unknown", url, TvType.Movie, url)

            val posterUrl = if (!movie.poster.isNullOrBlank()) "$posterBaseUrl/${movie.poster}" else null
            val backdropUrl = if (!movie.backdrops_Poster.isNullOrBlank()) "$posterBaseUrl/${movie.backdrops_Poster}" else null

            return newMovieLoadResponse(
                "${movie.MovieTitle.trim()} (${movie.MovieYear})",
                movie.MovieWatchLink,
                TvType.Movie,
                movie.MovieWatchLink
            ) {
                this.posterUrl = posterUrl
                this.backgroundPosterUrl = backdropUrl ?: posterUrl
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
                "$apiBaseUrl/tvshows.php?limit=99999&sort_by=uploadTime+DESC",
                verify = false,
                cacheTime = 60
            ).text
            val tvShows = AppUtils.parseJson<List<TvShowData>>(json)
            val tvShow = tvShows.find { it.id == tvShowId }
                ?: return newTvSeriesLoadResponse("Unknown", url, TvType.TvSeries, emptyList())

            val posterUrl = if (!tvShow.TVposter.isNullOrBlank()) "$posterBaseUrl/${tvShow.TVposter}" else null

            val episodes = mutableListOf<Episode>()
            val seasonNames = mutableListOf<SeasonData>()
            try {
                val episodesJson = app.get(
                    "$apiBaseUrl/tvepisodes.php?TVID=${tvShow.TVID}",
                    verify = false,
                    cacheTime = 60
                ).text
                val episodeData = AppUtils.parseJson<List<TvEpisodeData>>(episodesJson)

                val groupedEpisodes = episodeData.groupBy { it.season_number?.toIntOrNull() ?: 1 }

                groupedEpisodes.forEach { (seasonNum, seasonEpisodes) ->
                    seasonNames.add(SeasonData(season = seasonNum, name = "Season $seasonNum"))

                    seasonEpisodes.sortedBy { it.episode_number?.toIntOrNull() ?: 0 }.forEach { ep ->
                        val watchUrl = ep.watchlink?.let { link ->
                            if (link.startsWith("http")) link
                            else "$mainUrl/${link.trimStart('.', '/')}"
                        } ?: return@forEach

                        episodes.add(newEpisode(watchUrl) {
                            this.name = ep.name ?: "Episode ${ep.episode_number}"
                            this.season = seasonNum
                            this.episode = ep.episode_number?.toIntOrNull() ?: 0
                            this.posterUrl = ep.still_path?.let { "$posterBaseUrl$it" }
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
                if (seasonNames.isNotEmpty()) addSeasonNames(seasonNames)
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

    private fun getQualityFromString(quality: String?): SearchQuality? {
        return when {
            quality.isNullOrBlank() -> null
            quality.contains("CAM", ignoreCase = true) -> SearchQuality.Cam
            quality.contains("HD-RIP", ignoreCase = true) -> SearchQuality.HD
            quality.contains("BRRIP", ignoreCase = true) -> SearchQuality.BlueRay
            quality.contains("WEBRIP", ignoreCase = true) -> SearchQuality.WebRip
            quality.contains("DVD", ignoreCase = true) -> SearchQuality.DVD
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

    data class TvEpisodeData(
        val id: String,
        val TVID: String?,
        val episode_number: String?,
        val season_number: String?,
        val EPIID: String?,
        val name: String?,
        val still_path: String?,
        val overview: String?,
        val quality: String?,
        val watchlink: String?,
        val air_date: String?,
        val up_time: String?,
        val hit: String?,
        val upby: String?,
        val published: String?
    )
}
