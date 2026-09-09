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

        if (directUrl != null) {
            return
        }

        try {

            val response =
                app.get(mainUrlJson).text

            val json =
                JSONObject(response)

            val array =
                json.optJSONArray("Dutamovie")

            val newUrl =
                array
                    ?.optString(0)
                    ?.removeSuffix("/")

            if (!newUrl.isNullOrBlank()) {

                mainUrl = newUrl
                directUrl = newUrl
            }

        } catch (_: Exception) {
            // Gunakan mainUrl bawaan
        }
    }

    private fun getImageUrl(
        img: Element
    ): String? {

        val url = when {

            img.hasAttr("data-src") ->
                img.attr("abs:data-src")

            img.hasAttr("data-lazy-src") ->
                img.attr("abs:data-lazy-src")

            img.hasAttr("data-original") ->
                img.attr("abs:data-original")

            img.hasAttr("data-lazy-srcset") ->
                img.attr("abs:data-lazy-srcset")
                    .substringBefore(",")

            img.hasAttr("srcset") ->
                img.attr("abs:srcset")
                    .substringBefore(",")

            else ->
                img.attr("abs:src")
        }

        return url
            .trim()
            .takeIf {
                it.isNotBlank() &&
                !it.startsWith("data:")
            }
    }

    private fun getPoster(
        element: Element
    ): String? {

        /*
         * Kalau element adalah article,
         * cari gambar langsung di dalamnya.
         */
        element
            .selectFirst("img")
            ?.let {
                getImageUrl(it)?.let { poster ->
                    return poster
                }
            }

        /*
         * Kalau element adalah link judul,
         * naik ke parent sampai menemukan article.
         */
        var current: Element? =
            element

        repeat(8) {

            current
                ?.selectFirst("img")
                ?.let {
                    getImageUrl(it)?.let { poster ->
                        return poster
                    }
                }

            current =
                current?.parent()
        }

        return null
    }

    private fun Element.toSearchResult():
        SearchResponse? {

        val titleElement =
            selectFirst(
                "h2.entry-title > a"
            )
                ?: return null

        val title =
            titleElement
                .text()
                .trim()

        val href =
            titleElement
                .attr("href")
                .trim()

        if (
            title.isBlank() ||
            href.isBlank()
        ) {
            return null
        }

        val poster =
            getPoster(this)

        return newMovieSearchResponse(
            title,
            fixUrl(href),
            TvType.Movie
        ) {
            posterUrl = poster
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

        val results =
            document
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
            URLEncoder.encode(
                query,
                "UTF-8"
            )

        val url =
            "$mainUrl/?s=$encodedQuery&post_type[]=post&post_type[]=tv"

        val document =
            app.get(url).document

        return document
            .select("article.item")
            .mapNotNull {
                it.toSearchResult()
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
            document
                .selectFirst(
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
                ?.let {
                    getPoster(it)
                }

        val description =
            document
                .selectFirst(
                    "div[itemprop=description] > p"
                )
                ?.text()
                ?.trim()

        val year =
            document
                .select(
                    "div.gmr-moviedata " +
                    "strong:contains(Year:) > a"
                )
                .text()
                .trim()
                .toIntOrNull()

        val tags =
            document
                .select(
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
            document
                .selectFirst(
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

            this.year =
                year

            plot =
                description

            this.tags =
                tags

            score =
                Score.from10(
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

        val candidates =
            mutableListOf<String>()

        /*
         * 1. Ambil iframe yang sedang aktif.
         */
        try {

            val mainDocument =
                app.get(data).document

            mainDocument
                .select("iframe")
                .forEach { iframe ->

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

                    if (src.isNotBlank()) {

                        candidates.add(
                            httpsify(src)
                        )
                    }
                }

            /*
             * 2. Ambil semua Server 1, Server 2,
             *    Server 3, dst.
             */
            mainDocument
                .select("a")
                .forEach { element ->

                    val text =
                        element
                            .text()
                            .trim()
                            .lowercase()

                    val href =
                        element
                            .attr("abs:href")
                            .trim()

                    if (
                        text.startsWith("server") &&
                        href.isNotBlank()
                    ) {

                        candidates.add(
                            href
                        )
                    }
                }

            /*
             * 3. Ambil Link Download yang tersedia
             *    sebagai fallback player.
             */
            mainDocument
                .select("a")
                .forEach { element ->

                    val text =
                        element
                            .text()
                            .trim()
                            .lowercase()

                    val href =
                        element
                            .attr("abs:href")
                            .trim()

                    if (
                        text.startsWith(
                            "link download"
                        ) &&
                        href.isNotBlank()
                    ) {

                        candidates.add(
                            href
                        )
                    }
                }

        } catch (_: Exception) {
        }

        val uniqueCandidates =
            candidates
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (
            uniqueCandidates.isEmpty()
        ) {
            return false
        }

        var found =
            false

        /*
         * Coba setiap kandidat.
         */
        uniqueCandidates
            .forEach { candidate ->

                try {

                    /*
                     * Kalau kandidat langsung merupakan
                     * player/extractor, coba langsung.
                     */
                    loadExtractor(
                        candidate,
                        data,
                        subtitleCallback,
                        callback
                    )

                    found = true

                } catch (_: Exception) {

                    /*
                     * Kalau kandidat adalah halaman
                     * server, cari iframe di dalamnya.
                     */
                    try {

                        val serverDocument =
                            app.get(candidate).document

                        serverDocument
                            .select("iframe")
                            .forEach { iframe ->

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

                                if (
                                    src.isNotBlank()
                                ) {

                                    try {

                                        loadExtractor(
                                            httpsify(src),
                                            candidate,
                                            subtitleCallback,
                                            callback
                                        )

                                        found = true

                                    } catch (_: Exception) {
                                    }
                                }
                            }

                    } catch (_: Exception) {
                    }
                }
            }

        return found
    }
}
