package com.example.Dutamovie

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.extractors.AbyssPlayer

class DutamovieAbyssPlayer : AbyssPlayer() {

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        super.getUrl(
            url,
            referer,
            subtitleCallback,
            callback
        )
    }
}
