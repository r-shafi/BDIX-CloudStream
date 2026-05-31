package com.redowan

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Element

class BdixDhakaFlix7Provider : BdixDhakaFlix14Provider() {
    override var mainUrl = "http://172.16.50.7"
    override var name = "(BDIX) DhakaFlix 7"
    override val tvSeriesKeyword: List<String> = emptyList()
    override val serverName: String = "DHAKA-FLIX-7"
    override val supportedTypes = setOf(TvType.Movie)
    override val mainPage = mainPageOf(
        "English Movies/($year)/" to "English Movies ($year)",
        "English Movies/(2024)/" to "English Movies (2024)",
        "English Movies/(2023)/" to "English Movies (2023)",
        "English Movies/(2022)/" to "English Movies (2022)",
        "English Movies/(2021)/" to "English Movies (2021)",
        "English Movies/(2020)/" to "English Movies (2020)",
        "English Movies/(2019)/" to "English Movies (2019)",
        "English Movies/(2018)/" to "English Movies (2018)",
        "English Movies/(2017)/" to "English Movies (2017)",
        "English Movies/(2016)/" to "English Movies (2016)",
        "English Movies/(2015)/" to "English Movies (2015)",
        "English Movies/(2014)/" to "English Movies (2014)",
        "English Movies/(2013)/" to "English Movies (2013)",
        "English Movies/(2012)/" to "English Movies (2012)",
        "English Movies/(2011)/" to "English Movies (2011)",
        "English Movies/(2010)/" to "English Movies (2010)",
        "English Movies/(2009)/" to "English Movies (2009)",
        "English Movies/(2008)/" to "English Movies (2008)",
        "English Movies/(2007)/" to "English Movies (2007)",
        "English Movies/(2006)/" to "English Movies (2006)",
        "English Movies/(2005)/" to "English Movies (2005)",
        "English Movies/(2004)/" to "English Movies (2004)",
        "English Movies/(2003)/" to "English Movies (2003)",
        "English Movies/(2002)/" to "English Movies (2002)",
        "English Movies/(2001)/" to "English Movies (2001)",
        "English Movies/(2000)/" to "English Movies (2000)",
        "English Movies/(1999)/" to "English Movies (1999)",
        "English Movies/(1998)/" to "English Movies (1998)",
        "English Movies/(1997)/" to "English Movies (1997)",
        "English Movies/(1996)/" to "English Movies (1996)",
        "English Movies/(1995)/" to "English Movies (1995)",
        "English Movies/(1960-1994)/" to "English Movies (1960-1994)",
        "English Movies/Alfred Hitchcock-Complete Filmography (1925-1934)/" to "Alfred Hitchcock Filmography",
        "English Movies/Charlie Chaplin-Complete Filmography (1914-1967)/" to "Charlie Chaplin Filmography",
        "English Movies/DC Extended Universe-DCEU Collection (2008-2019) 720p & 1080p [Dual Audio]/" to "DCEU Collection (2008-2019)",
        "3D Movies/" to "3D Movies",
        "Foreign Language Movies/Bangla Dubbing Movies/" to "Bangla Dubbing Movies",
        "Foreign Language Movies/Brazilian Movie/" to "Brazilian Movies",
        "Foreign Language Movies/Chinese Language/" to "Chinese Movies",
        "Foreign Language Movies/Danish Language/" to "Danish Movies",
        "Foreign Language Movies/Dutch Language/" to "Dutch Movies",
        "Foreign Language Movies/French Language/" to "French Movies",
        "Foreign Language Movies/German Language/" to "German Movies",
        "Foreign Language Movies/Indonesian Language/" to "Indonesian Movies",
        "Foreign Language Movies/Iranian Movies/" to "Iranian Movies",
        "Foreign Language Movies/Italian Movie/" to "Italian Movies",
        "Foreign Language Movies/Japanese Language/" to "Japanese Movies",
        "Foreign Language Movies/Korean Language/" to "Korean Movies",
        "Foreign Language Movies/Norwegian Language/" to "Norwegian Movies",
        "Foreign Language Movies/Other Language/" to "Other Language Movies",
        "Foreign Language Movies/Pakistani Movie/" to "Pakistani Movies",
        "Foreign Language Movies/Polish Language/" to "Polish Movies",
        "Foreign Language Movies/Russian Language/" to "Russian Movies",
        "Foreign Language Movies/Spanish Language/" to "Spanish Movies",
        "Foreign Language Movies/Swedish Language/" to "Swedish Movies",
        "Foreign Language Movies/Thai Language/" to "Thai Movies",
        "Foreign Language Movies/Turkish Language/" to "Turkish Movies",
        "Kolkata Bangla Movies/($year)/" to "Kolkata Bangla Movies ($year)",
        "Kolkata Bangla Movies/(2024)/" to "Kolkata Bangla Movies (2024)",
        "Kolkata Bangla Movies/(2023)/" to "Kolkata Bangla Movies (2023)",
        "Kolkata Bangla Movies/(2022)/" to "Kolkata Bangla Movies (2022)",
        "Kolkata Bangla Movies/(2021)/" to "Kolkata Bangla Movies (2021)",
        "Kolkata Bangla Movies/(2020)/" to "Kolkata Bangla Movies (2020)",
        "Kolkata Bangla Movies/(2019)/" to "Kolkata Bangla Movies (2019)",
        "Kolkata Bangla Movies/(2018)/" to "Kolkata Bangla Movies (2018)",
        "Kolkata Bangla Movies/(2017)/" to "Kolkata Bangla Movies (2017)",
        "Kolkata Bangla Movies/(2016)/" to "Kolkata Bangla Movies (2016)",
        "Kolkata Bangla Movies/(2015)/" to "Kolkata Bangla Movies (2015)",
        "Kolkata Bangla Movies/(2014)/" to "Kolkata Bangla Movies (2014)",
        "Kolkata Bangla Movies/(2013)/" to "Kolkata Bangla Movies (2013)",
        "Kolkata Bangla Movies/(2012)/" to "Kolkata Bangla Movies (2012)",
        "Kolkata Bangla Movies/(2011)/" to "Kolkata Bangla Movies (2011)",
        "Kolkata Bangla Movies/(2010)/" to "Kolkata Bangla Movies (2010)",
        "Kolkata Bangla Movies/(2009)/" to "Kolkata Bangla Movies (2009)",
        "Kolkata Bangla Movies/(2008)/" to "Kolkata Bangla Movies (2008)",
        "Kolkata Bangla Movies/(2007)/" to "Kolkata Bangla Movies (2007)",
        "Kolkata Bangla Movies/(2006)/" to "Kolkata Bangla Movies (2006)",
        "Kolkata Bangla Movies/(2005)/" to "Kolkata Bangla Movies (2005)",
        "Kolkata Bangla Movies/(2004)/" to "Kolkata Bangla Movies (2004)",
        "Kolkata Bangla Movies/(2003)/" to "Kolkata Bangla Movies (2003)",
        "Kolkata Bangla Movies/(2002)/" to "Kolkata Bangla Movies (2002)",
        "Kolkata Bangla Movies/(2000)/" to "Kolkata Bangla Movies (2000)",
        "Kolkata Bangla Movies/(1999) & Before/" to "Kolkata Bangla Movies (Before 2000)",
        "Kolkata Bangla Movies/Satyajit Ray Films/" to "Satyajit Ray Films",
    )

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val doc = app.get("$mainUrl/$serverName/${request.data}").document
        val home = doc.select("tbody > tr:gt(1)").mapNotNull { post ->
            getPostResultWithPoster(post)
        }
        return newHomePageResponse(request.name, home, false)
    }

    private fun getPostResultWithPoster(post: Element): SearchResponse? {
        val folderHtml = post.select("td.fb-n > a")
        val isFolder = post.select("td.fb-i > img").attr("alt") == "folder"
        if (!isFolder) return null
        val name = folderHtml.text()
        val url = mainUrl + folderHtml.attr("href")
        val poster = "${url}a_AL_.jpg"
        return newAnimeSearchResponse(name, url, TvType.Movie) {
            this.posterUrl = poster
            addDubStatus(
                dubExist = "Dual" in name,
                subExist = "ESub" in name
            )
        }
    }
}
