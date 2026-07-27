package net.ddns.shafi

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class CtgFunMediaPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CtgFunMediaProvider())
    }
}
