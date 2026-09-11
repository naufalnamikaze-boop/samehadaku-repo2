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

    private val websiteConfigUrl =
        "https://raw.githubusercontent.com/naufalnamikaze-boop/samehadaku-repo2/builds/Website.json"

    private suspend fun updateWebsiteUrl() {

        try {

            val json =
                app.get(websiteConfigUrl)
                    .text
                    .let {
                        JSONObject(it)
                    }

            val urls =
                json
                    .getJSONArray("dutamovie")

            if (urls.length() > 0) {

                val newUrl =
                    urls
                        .getString(0)
                        .trim()
                        .removeSuffix("/")

                if (newUrl.isNotBlank()) {
                    mainUrl = newUrl
                }
            }

        } catch (_: Exception) {
            // Gunakan mainUrl bawaan jika config gagal.
        }
    }

    override val hasMainPage = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

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

    /*
     * ============================================================
     * IMAGE / POSTER
     * ============================================================
     */

    private fun cleanImageUrl(
        url: String?
    ): String? {

        if (url.isNullOrBlank()) {
            return null
        }

        var result =
            url.trim()

        if (
            result.startsWith("//")
        ) {
            result =
                "https:$result"
        }

        if (
            result.startsWith("data:")
        ) {
            return null
        }

        /*
         * srcset biasanya:
         *
         * image-300.jpg 300w,
         * image-600.jpg 600w
         *
         * Kita ambil URL pertama.
         */
        result =
            result
                .substringBefore(",")
                .substringBefore(" ")
                .trim()

        return result
            .takeIf {
                it.startsWith("http://") ||
                it.startsWith("https://")
            }
    }

    private fun getImageFromElement(
        img: Element
    ): String? {

        /*
         * Prioritas atribut lazy-load.
         */

        val attributes =
            listOf(
                "data-src",
                "data-lazy-src",
                "data-original",
                "data-url",
                "data-image",
                "data-lazy-srcset",
                "srcset",
                "src"
            )

        for (
            attribute in attributes
        ) {

            if (
                img.hasAttr(attribute)
            ) {

                val value =
                    img.attr(attribute)

                val image =
                    cleanImageUrl(value)

                if (
                    image != null
                ) {
                    return image
                }
            }
        }

        return null
    }

    private fun getPoster(
        element: Element
    ): String? {

        /*
         * 1. Cari <img> di dalam element.
         */
        element
            .selectFirst("img")
            ?.let {

                getImageFromElement(it)
                    ?.let { poster ->
                        return poster
                    }
            }

        /*
         * 2. Naik ke parent.
         *
         * Berguna kalau element yang kita terima
         * adalah <a> judul film.
         */
        var current:
            Element? = element

        repeat(10) {

            current
                ?.selectFirst("img")
                ?.let {

                    getImageFromElement(it)
                        ?.let { poster ->
                            return poster
                        }
                }

            current =
                current?.parent()
        }

        /*
         * 3. Coba background-image.
         */
        current =
            element

        repeat(10) {

            current
                ?.select("[style]")
                ?.forEach { styled ->

                    val style =
                        styled.attr("style")

                    val match =
                        Regex(
                            """url\(['"]?([^'")]+)"""
                        )
                            .find(style)

                    val image =
                        match
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.let {
                                cleanImageUrl(it)
                            }

                    if (
                        image != null
                    ) {
                        return image
                    }
                }

            current =
                current?.parent()
        }

        /*
         * 4. Fallback ke OpenGraph image.
         */
        element
            .ownerDocument()
            ?.selectFirst(
                "meta[property=og:image]"
            )
            ?.attr("content")
            ?.let {
                cleanImageUrl(it)
                    ?.let { poster ->
                        return poster
                    }
            }

        return null
    }

    /*
     * ============================================================
     * SEARCH / HOME MAPPER
     * ============================================================
     */

    private fun Element.toSearchResult():
        SearchResponse? {

        val titleElement =
            selectFirst(
                "h2.entry-title a"
            )
                ?: selectFirst(
                    "h2 a"
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

        /*
         * Ambil poster dari seluruh card,
         * bukan hanya dari <a> judul.
         */
        val poster =
            getPoster(this)

        return newMovieSearchResponse(
            title,
            fixUrl(href),
            TvType.Movie
        ) {

            posterUrl =
                poster
        }
    }

    /*
     * ============================================================
     * HOME
     * ============================================================
     */

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        updateWebsiteUrl()

        val url =
            "$mainUrl/${request.data.format(page)}"

        val document =
            app.get(url).document

        /*
         * Situs menggunakan article.item
         * untuk kartu film.
         */
        var results =
            document
                .select("article.item")
                .mapNotNull {
                    it.toSearchResult()
                }

        /*
         * Fallback kalau struktur HTML berubah.
         */
        if (
            results.isEmpty()
        ) {

            results =
                document
                    .select(
                        "h2.entry-title"
                    )
                    .mapNotNull { heading ->

                        heading
                            .parent()
                            ?.toSearchResult()
                    }
            }

        return newHomePageResponse(
            request.name,
            results
        )
    }

    /*
     * ============================================================
     * SEARCH
     * ============================================================
     */

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        updateWebsiteUrl()

        val encodedQuery =
            URLEncoder.encode(
                query,
                "UTF-8"
            )

        val url =
            "$mainUrl/?s=$encodedQuery" +
            "&post_type[]=post" +
            "&post_type[]=tv"

        val document =
            app.get(url).document

        var results =
            document
                .select("article.item")
                .mapNotNull {
                    it.toSearchResult()
                }

        /*
         * Fallback selector.
         */
        if (
            results.isEmpty()
        ) {

            results =
                document
                    .select(
                        "h2.entry-title a"
                    )
                    .mapNotNull { element ->

                        val title =
                            element
                                .text()
                                .trim()

                        val href =
                            element
                                .attr("href")
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
                                    getPoster(element)
                            }
                        }
                    }
        }

        return results
            .distinctBy {
                it.url
            }
    }

    /*
     * ============================================================
     * DETAIL
     * ============================================================
     */

    override suspend fun load(
        url: String
    ): LoadResponse {

        updateWebsiteUrl()

        val document =
            app.get(url).document

        val title =
    document
        .selectFirst(
            "h1.entry-title"
        )
        ?.text()
        ?.replace(
            Regex(
                "\\s+Season.*$",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        ?.replace(
            Regex(
                "\\s+Episode.*$",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        ?.trim()
        ?: "Unknown"

        /*
         * Cari poster dari area artikel/detail.
         */
        val poster =
            document
                .selectFirst(
                    "h1.entry-title"
                )
                ?.let {
                    getPoster(it)
                }
                ?: document
                    .selectFirst(
                        "meta[property=og:image]"
                    )
                    ?.attr("content")
                    ?.let {
                        cleanImageUrl(it)
                    }

        val description =
            document
                .selectFirst(
                    "div[itemprop=description]"
                )
                ?.text()
                ?.trim()

        val year =
            document
                .select(
                    "div.gmr-moviedata " +
                    "strong:contains(Year:) a"
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

            posterUrl =
                poster

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

    /*
     * ============================================================
     * VIDEO LINKS
     * ============================================================
     */

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {

    var found = false

    for (player in 1..7) {

        val playerUrl =
            if (player == 1) {
                data
            } else {
                if (data.contains("?")) {
                    "$data&player=$player"
                } else {
                    "$data?player=$player"
                }
            }

        try {

            val document =
                app.get(playerUrl).document

            val iframeUrls =
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
                            .trim()
                            .takeIf {
                                it.isNotBlank()
                            }
                            ?.let {
                                httpsify(it)
                            }
                    }
                    .distinct()

            println(
                "DUTAMOVIE PLAYER $player -> $iframeUrls"
            )

            if (iframeUrls.isEmpty()) {
                continue
            }

            for (iframeUrl in iframeUrls) {

                try {

                    val extractorFound =
                        loadExtractor(
                            iframeUrl,
                            playerUrl,
                            subtitleCallback
                        ) { link ->

                            found = true
                            callback(link)
                        }

                    println(
                        "DUTAMOVIE PLAYER $player -> " +
                        "$iframeUrl -> extractor=$extractorFound"
                    )

                } catch (e: Exception) {

                    println(
                        "DUTAMOVIE ERROR PLAYER $player -> " +
                        "$iframeUrl -> ${e.message}"
                    )
                }
            }

        } catch (e: Exception) {

            println(
                "DUTAMOVIE ERROR PLAYER $player -> " +
                "${e.message}"
            )
        }
    }

    return found
}
}
}
