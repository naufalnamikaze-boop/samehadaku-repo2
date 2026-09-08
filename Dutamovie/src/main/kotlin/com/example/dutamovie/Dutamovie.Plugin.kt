package com.example.dutamovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class DutamoviePlugin : BasePlugin() {

    override fun load(context: android.content.Context) {
        registerMainAPI(Dutamovie())
    }
}