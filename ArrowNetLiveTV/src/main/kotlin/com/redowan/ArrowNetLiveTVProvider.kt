package com.redowan

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink

class ArrowNetLiveTVProvider : MainAPI() {
    override var mainUrl = "http://10.10.230.182:8080"
    override var name = "(BDIX) ArrowNet Live TV"
    override var lang = "bn"
    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Live)

    private val channels = listOf(
        ChannelData("ch1", "Channel 1", "http://10.10.230.182:8080/ch1.m3u8", "http://tv.arrownetsylhet.com/media/logos/44ad517591a69018404e84b8abc57c88.jpg"),
        ChannelData("ch2", "Channel 2", "http://10.10.230.182:8080/ch2.m3u8", "http://tv.arrownetsylhet.com/media/logos/01b15489a8b7c62846a9c14c30022e83.jpg"),
        ChannelData("ch3", "Channel 3", "http://10.10.230.182:8080/ch3.m3u8", "http://tv.arrownetsylhet.com/media/logos/26c0812a6bb26fcf3291ffad6f5d8f5d.jpg"),
        ChannelData("ch4", "Channel 4", "http://10.10.230.182:8080/ch4.m3u8", null),
        ChannelData("ch5", "Channel 5", "http://10.10.230.182:8080/ch5.m3u8", "http://tv.arrownetsylhet.com/media/logos/f98be120a02a83d146a129f4638a1d65.jpg"),
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val results = channels.map { ch ->
            newLiveSearchResponse(ch.name, ch.m3u8Url) {
                this.posterUrl = ch.logo
            }
        }
        return newHomePageResponse(
            listOf(HomePageList("ArrowNet Live TV", results, isHorizontalImages = false)),
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        return channels.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.id.contains(query, ignoreCase = true)
        }.map { ch ->
            newLiveSearchResponse(ch.name, ch.m3u8Url) {
                this.posterUrl = ch.logo
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val channel = channels.find { it.m3u8Url == url } ?: channels.first()
        return newLiveStreamLoadResponse(name = channel.name, url = url, dataUrl = url) {
            this.posterUrl = channel.logo
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
                source = this.name,
                name = this.name,
                url = data,
                type = ExtractorLinkType.M3U8
            )
        )
        return true
    }

    private data class ChannelData(
        val id: String,
        val name: String,
        val m3u8Url: String,
        val logo: String? = null
    )
}
