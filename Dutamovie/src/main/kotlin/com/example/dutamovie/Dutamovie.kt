package com.example.Dutamovie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element
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
            // Gunakan mainUrl fallback
        }
    }

    private fun getPoster(
    element: Element,
    title: String? = null
): String? {

    fun imageUrl(img: Element): String? {

        val url = when {
            img.hasAttr("data-src") ->
                img.attr("abs:data-src")

            img.hasAttr("data-lazy-src") ->
                img.attr("abs:data-lazy-src")

            img.hasAttr("data-original") ->
                img.attr("abs:data-original")

            img.hasAttr("srcset") ->
                img.attr("abs:srcset")
                    .substringBefore(",")

            else ->
                img.attr("abs:src")
        }

        return url
            .trim()
            .takeIf { it.isNotBlank() }
    }

    var current: Element? = element

    repeat(8) {

        current
            ?.selectFirst("img")
            ?.let { img ->
                imageUrl(img)?.let { return it }
            }

        current = current?.parent()
    }

    if (!title.isNullOrBlank()) {

        val titleLower =
            title.lowercase()

        element
            .ownerDocument()
            ?.select("img")
            ?.firstOrNull {

                it.attr("alt")
                    .lowercase()
                    .contains(titleLower)
            }
            ?.let { img ->
                imageUrl(img)?.let { return it }
            }
    }

    return null
}

    private fun Element.toSearchResult(): SearchResponse? {

        val titleElement = selectFirst(
            "h2.entry-title > a"
        ) ?: return null

        val title = titleElement
            .text()
            .trim()

        val href = titleElement
            .attr("href")
            .trim()

        if (title.isBlank() || href.isBlank()) {
            return null
        }

        val poster = getPoster(
    titleElement,
    title
)

        return newMovieSearchResponse(
            title,
            fixUrl(href),
            TvType.Movie
        ) {
            posterUrl = getPoster(
    element,
    title
)
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
            .select("h2.entry-title > a")
            .mapNotNull { element ->

                val title =
                    element.text().trim()

                val href =
                    element.attr("href").trim()

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
                        posterUrl = getPoster(
    element,
    title
)
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
            document
                .selectFirst(
                    "h1.entry-title"
                )
                ?.closest("article")
                ?.selectFirst("img")
                ?.let {

                    when {
                        it.hasAttr("data-src") ->
                            it.attr("abs:data-src")

                        it.hasAttr("data-lazy-src") ->
                            it.attr("abs:data-lazy-src")

                        it.hasAttr("data-original") ->
                            it.attr("abs:data-original")

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
                "div.gmr-moviedata strong:contains(Year:) a"
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
                "div.gmr-meta-rating span[itemprop=ratingValue]"
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

    val serverUrls =
        mutableListOf<String>()

    val mainDocument =
        app.get(data).document

    /*
     * Ambil URL semua tombol Server.
     */
    mainDocument
        .select("a")
        .forEach { element ->

            val text =
                element.text()
                    .trim()
                    .lowercase()

            if (text.startsWith("server")) {

                val href =
                    element
                        .attr("abs:href")
                        .trim()

                if (href.isNotBlank()) {
                    serverUrls.add(href)
                }
            }
        }

    /*
     * Kalau server tidak ditemukan,
     * gunakan halaman utama sebagai fallback.
     */
    if (serverUrls.isEmpty()) {
        serverUrls.add(data)
    }

    var found = false

    serverUrls
        .distinct()
        .forEach { serverUrl ->

            try {

                val serverDocument =
                    app.get(serverUrl).document

                val iframeUrls =
                    serverDocument
                        .select("iframe")
                        .mapNotNull { iframe ->

                            val src =
                                when {

                                    iframe.hasAttr(
                                        "data-litespeed-src"
                                    ) ->
                                        iframe.attr(
                                            "data-litespeed-src"
                                        )

                                    iframe.hasAttr(
                                        "data-src"
                                    ) ->
                                        iframe.attr(
                                            "data-src"
                                        )

                                    else ->
                                        iframe.attr("src")
                                }

                            src
                                .trim()
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?.let {
                                    httpsify(it)
                                }
                        }
                        .distinct()

                iframeUrls.forEach { iframeUrl ->

                    try {

                        loadExtractor(
                            iframeUrl,
                            serverUrl,
                            subtitleCallback,
                            callback
                        )

                        found = true

                    } catch (_: Exception) {
                    }
                }

            } catch (_: Exception) {
            }
        }

    return found
}

        val uniqueServers =
            serverUrls.distinct()

        if (uniqueServers.isEmpty()) {
            return false
        }

        var found = false

        /*
         * Coba setiap Server.
         */
        uniqueServers.forEach { serverUrl ->

            try {

                val serverDocument =
                    app.get(serverUrl).document

                val iframes =
                    serverDocument
                        .select("iframe")
                        .mapNotNull { iframe ->

                            val src =
                                when {

                                    iframe.hasAttr(
                                        "data-litespeed-src"
                                    ) ->
                                        iframe.attr(
                                            "data-litespeed-src"
                                        )

                                    iframe.hasAttr(
                                        "data-src"
                                    ) ->
                                        iframe.attr(
                                            "data-src"
                                        )

                                    else ->
                                        iframe.attr(
                                            "src"
                                        )
                                }

                            src
                                .takeIf {
                                    it.isNotBlank()
                                }
                                ?.let {
                                    httpsify(it)
                                }
                        }
                        .distinct()

                /*
                 * Kalau halaman server langsung berupa
                 * iframe, coba extractor CloudStream.
                 */
                iframes.forEach { iframeUrl ->

                    try {

                        loadExtractor(
                            iframeUrl,
                            serverUrl,
                            subtitleCallback,
                            callback
                        )

                        found = true

                    } catch (_: Exception) {
                    }
                }

                /*
                 * Beberapa server bisa langsung berupa
                 * halaman video tanpa iframe.
                 */
                if (
                    iframes.isEmpty() &&
                    serverUrl.isNotBlank()
                ) {

                    try {

                        loadExtractor(
                            serverUrl,
                            data,
                            subtitleCallback,
                            callback
                        )

                        found = true

                    } catch (_: Exception) {
                    }
                }

            } catch (_: Exception) {
            }
        }

        return found
    }
}
