package net.ddns.shafi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SrzoneLiveTVPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SrzoneLiveTVProvider())
    }
}
