package com.example.Dutamovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.net.URLEncoder

class Dutamovie : MainAPI() {

    override var mainUrl = "https://actors-pictures.com"
    override var name = "Dutamovie"
    override var lang = "id"

    override val hasMainPage = true
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    private val mainUrlJson =
        "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/Website.json"

    private var directUrl: String? = null

    override val mainPage = mainPageOf(
        "category/box-office/page/%d/" to "Box Office",
        "category/serial-tv/page/%d/" to "TV Series",
        "action/page/%d/" to "Action",
        "adventure/page/%d/" to "Adventure",
        "animation/page/%d/" to "Animation",
        "comedy/page/%d/" to "Comedy",
        "crime/page/%d/" to "Crime",
        "drama/page/%d/" to "Drama",
        "fantasy/page/%d/" to "Fantasy",
        "horror/page/%d/" to "Horror",
        "mystery/page/%d/" to "Mystery",
        "romance/page/%d/" to "Romance",
        "science-fiction/page/%d/" to "Sci-Fi",
        "thriller/page/%d/" to "Thriller",
        "country/indonesia/page/%d/" to "Indonesia",
        "country/korea/page/%d/" to "Korea",
        "country/china/page/%d/" to "China"
    )

    private suspend fun loadMainUrl() {
        if (directUrl != null) return

        try {
            val response = app.get(mainUrlJson).text
            val json = JSONObject(response)

            val array = json.optJSONArray("Dutamovie")
            val newUrl = array
                ?.optString(0)
                ?.removeSuffix("/")

            if (!newUrl.isNullOrBlank()) {
                mainUrl = newUrl
                directUrl = newUrl
            }
        } catch (_: Exception) {
            // Pakai mainUrl fallback
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {

        val title = selectFirst(
            "h2.entry-title > a"
        )
            ?.text()
            ?.trim()
            ?: return null

        val href = selectFirst(
            "h2.entry-title > a"
        )
            ?.attr("href")
            ?.trim()
            ?: return null

        val poster = selectFirst(
            "a > img"
        )?.let {
            when {
                it.hasAttr("data-src") ->
                    it.attr("abs:data-src")

                it.hasAttr("data-lazy-src") ->
                    it.attr("abs:data-lazy-src")

                it.hasAttr("srcset") ->
                    it.attr("abs:srcset")
                        .substringBefore(" ")

                else ->
                    it.attr("abs:src")
            }
        }

        val quality = select(
            "div.gmr-qual, " +
            "div.gmr-quality-item > a"
        )
            .text()
            .trim()
            .replace("-", "")

        return if (quality.isBlank()) {

            newTvSeriesSearchResponse(
                title,
                href,
                TvType.TvSeries
            ) {
                posterUrl = poster
            }

        } else {

            newMovieSearchResponse(
                title,
                href,
                TvType.Movie
            ) {
                posterUrl = poster
                addQuality(quality)
            }
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        loadMainUrl()

        val url =
            "$mainUrl/${request.data.format(page)}"

        val document =
            app.get(url).document

        val results = document
            .select("article.item")
            .mapNotNull {
                it.toSearchResult()
            }

        return newHomePageResponse(
            request.name,
            results
        )
    }

    override suspend fun search(
    query: String
): List<SearchResponse> {

    loadMainUrl()

    val encodedQuery =
        URLEncoder.encode(query, "UTF-8")

    val url =
        "$mainUrl/?s=$encodedQuery&post_type[]=post&post_type[]=tv"

    val document =
        app.get(url).document

    return document
        .select("h2.entry-title a")
        .mapNotNull { element ->

            val title =
                element.text()
                    .trim()

            val href =
                element.attr("href")
                    .trim()

            if (
                title.isBlank() ||
                href.isBlank()
            ) {
                null
            } else {

                newMovieSearchResponse(
                    title,
                    fixUrl(href),
                    TvType.Movie
                ) {
                    posterUrl =
                        element
                            .parent()
                            ?.parent()
                            ?.selectFirst("img")
                            ?.let { img ->

                                when {
                                    img.hasAttr("data-src") ->
                                        img.attr("abs:data-src")

                                    img.hasAttr("data-lazy-src") ->
                                        img.attr("abs:data-lazy-src")

                                    else ->
                                        img.attr("abs:src")
                                }
                            }
                }
            }
        }
        .distinctBy {
            it.url
        }
    }
    
    override suspend fun load(
        url: String
    ): LoadResponse {

        loadMainUrl()

        val response =
            app.get(url)

        val document =
            response.document

        val title =
            document.selectFirst(
                "h1.entry-title"
            )
                ?.text()
                ?.substringBefore("Season")
                ?.substringBefore("Episode")
                ?.trim()
                ?: "Unknown"

        val poster =
            document.selectFirst(
                "figure.pull-left > img"
            )?.let {
                when {
                    it.hasAttr("data-src") ->
                        it.attr("abs:data-src")

                    it.hasAttr("data-lazy-src") ->
                        it.attr("abs:data-lazy-src")

                    else ->
                        it.attr("abs:src")
                }
            }

        val description =
            document.selectFirst(
                "div[itemprop=description] > p"
            )
                ?.text()
                ?.trim()

        val year =
            document.select(
                "div.gmr-moviedata " +
                "strong:contains(Year:) > a"
            )
                .text()
                .trim()
                .toIntOrNull()

        val tags =
            document.select(
                "div.gmr-moviedata a"
            )
                .map {
                    it.text().trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val rating =
            document.selectFirst(
                "div.gmr-meta-rating " +
                "span[itemprop=ratingValue]"
            )
                ?.text()
                ?.trim()

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            posterUrl = poster
            this.year = year
            plot = description
            this.tags = tags
            score = Score.from10(
                rating?.toDoubleOrNull()
            )
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    val document =
        app.get(data).document

    val iframes =
        document
            .select("iframe")
            .mapNotNull { iframe ->

                val src =
                    when {
                        iframe.hasAttr("data-litespeed-src") ->
                            iframe.attr("data-litespeed-src")

                        iframe.hasAttr("data-src") ->
                            iframe.attr("data-src")

                        else ->
                            iframe.attr("src")
                    }

                src
                    .takeIf { it.isNotBlank() }
                    ?.let { httpsify(it) }
            }
            .distinct()

    if (iframes.isEmpty()) {
        return false
    }

    iframes.forEach { iframe ->

        try {

            loadExtractor(
                iframe,
                data,
                subtitleCallback,
                callback
            )

        } catch (_: Exception) {
        }
    }

    return true
    }
}
