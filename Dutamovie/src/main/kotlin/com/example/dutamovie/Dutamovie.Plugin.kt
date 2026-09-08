package com.example.dutamovie

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class DutamoviePlugin : Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan class MainAPI Dutamovie agar terbaca di Cloudstream
        registerMainAPI(Dutamovie())
    }
}
