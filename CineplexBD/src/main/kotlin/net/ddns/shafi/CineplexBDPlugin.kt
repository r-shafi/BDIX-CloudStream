package net.ddns.shafi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CineplexBDPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CineplexBDMoviesProvider())
        registerMainAPI(CineplexBDTvSeriesProvider())
    }
}
