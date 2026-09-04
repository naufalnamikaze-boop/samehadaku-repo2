package com.example.samehadaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Samehadaku : MainAPI() {
    override var mainUrl = "https://v2.samehadaku.how"
    override var name = "Samehadaku"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "$mainUrl/page/%d/" to "Anime Terbaru"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data.format(page)).document
        val home = document.select("div.post-show, article.animpost").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleElement = this.selectFirst("h2.entry-title a, h3 a, .title a") ?: return null
        val title = titleElement.text()
        val href = titleElement.attr("href")
        val posterUrl = this.selectFirst("img")?.let { 
            it.attr("data-src").ifEmpty { it.attr("src") } 
        } ?: ""

        return newMovieSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("div.post-show, article.animpost").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title, h1.title")?.text() ?: ""
        val poster = document.selectFirst(".thumb img, .poster img")?.let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        } ?: ""
        val description = document.select(".entry-content p, .desc p").joinToString("\n") { it.text() }
        
        val episodes = document.select(".episodelist ul li, .lsteps ul li, .allepisodes ul li").mapNotNull {
            val link = it.selectFirst("a")
            val epName = link?.text() ?: return@mapNotNull null
            val epUrl = link.attr("href")
            Episode(epUrl, name = epName)
        }.reversed()

        val genres = document.select(".genre-info a, .spe span a").map { it.text() }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
            addEpisodes(DubStatus.Subbed, episodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Mengambil pemutar video iframe utama dari halaman episode
        val iframeUrl = document.selectFirst("iframe")?.attr("src") ?: return false
        loadExtractor(iframeUrl, data, subtitleCallback, callback)

        return true
    }
}
