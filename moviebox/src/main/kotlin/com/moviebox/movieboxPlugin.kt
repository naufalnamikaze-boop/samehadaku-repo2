package com.moviebox

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MovieboxPlugin : Plugin() {

    override fun load(context: android.content.Context) {
        registerMainAPI(Moviebox())
    }
}
