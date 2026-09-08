package com.example.Dutamovie

import android.content.Context
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class DutamoviePlugin : BasePlugin() {

    override fun load(context: Context) {
        registerMainAPI(Dutamovie())
    }
}