package com.example.kuramanime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Kuramanime : MainAPI() {

    override var mainUrl = "https://v20.kuramanime.ing"
    override var name = "Kuramanime"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime
    )

    private val fallbackUrls = listOf(
        "https://v20.kuramanime.ing",
        "https://m2.kuramanime.ing"
    )

    private var activeBaseUrl = mainUrl

    /*
     * Authorization yang digunakan oleh player Kuramanime.
     */
    private val authValue =
        "kJuHHkaqcBFXiGMHQf6bJw8YAyDcwGD8Ur"

    override val mainPage = mainPageOf(
        "$mainUrl/quick/ongoing?order_by=updated&page=" to "Sedang Tayang",
        "$mainUrl/quick/finished?order_by=updated&page=" to "Selesai Tayang",
        "$mainUrl/quick/movie?order_by=updated&page=" to "Film Layar Lebar"
    )

    // =========================================================
    // FALLBACK REQUEST
    // =========================================================

    private suspend fun getWithFallback(
        path: String
    ): Pair<String, Document>? {

        val cleanPath =
            if (path.startsWith("http")) {
                path.substringAfter(".ing")
            } else {
                path
            }

        for (base in fallbackUrls) {

            try {

                val url =
                    if (cleanPath.startsWith("/")) {
                        "$base$cleanPath"
                    } else {
                        "$base/$cleanPath"
                    }

                val response = app.get(url)

                if (response.isSuccessful) {

                    activeBaseUrl = base
                    mainUrl = base

                    return Pair(
                        base,
                        response.document
                    )
                }

            } catch (_: Exception) {
                continue
            }
        }

        return null
    }

    private fun normalizeUrl(
        url: String
    ): String {

        if (url.startsWith("//")) {
            return "https:$url"
        }

        if (url.startsWith("http")) {
            return url
        }

        return "$activeBaseUrl/${
            url.trimStart('/')
        }"
    }

    // =========================================================
    // MAIN PAGE
    // =========================================================

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val path = when {

            request.name == "Sedang Tayang" ->
                "/quick/ongoing?order_by=updated&page=$page"

            request.name == "Selesai Tayang" ->
                "/quick/finished?order_by=updated&page=$page"

            request.name == "Film Layar Lebar" ->
                "/quick/movie?order_by=updated&page=$page"

            else ->
                "/quick/ongoing?order_by=updated&page=$page"
        }

        val result =
            getWithFallback(path)

        val document =
            result?.second
                ?: return newHomePageResponse(
                    request.name,
                    emptyList()
                )

        val home = document
            .select("div.product__item")
            .mapNotNull { item ->

                val link = item.selectFirst(
                    "a[href*=/anime/]"
                ) ?: return@mapNotNull null

                val href =
                    normalizeUrl(
                        link.attr("href")
                    )

                if (href.isBlank()) {
                    return@mapNotNull null
                }

                val title =
                    item.selectFirst("h5")
                        ?.text()
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?: item.selectFirst(
                            "a:last-of-type"
                        )
                            ?.text()
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null

                val poster =
                    item.selectFirst(".set-bg")
                        ?.attr("data-setbg")
                        ?.takeIf { it.isNotBlank() }

                val animeUrl =
                    href.replace(
                        Regex(
                            "/episode/\\d+.*$"
                        ),
                        ""
                    )

                val episode =
                    Regex(
                        "Ep\\s*(\\d+)",
                        RegexOption.IGNORE_CASE
                    )
                        .find(item.text())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()

                newAnimeSearchResponse(
                    title,
                    animeUrl,
                    TvType.Anime
                ) {
                    this.posterUrl = poster
                    addSub(episode)
                }
            }
            .distinctBy {
                it.url
            }

        return newHomePageResponse(
            request.name,
            home
        )
    }

    // =========================================================
    // SEARCH
    // =========================================================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val encoded =
            URLEncoder.encode(
                query,
                StandardCharsets.UTF_8.toString()
            )

        val result =
            getWithFallback(
                "/anime?search=$encoded&order_by=text"
            )
                ?: return emptyList()

        val document =
            result.second

        return document
            .select("div.product__item")
            .mapNotNull { item ->

                val link =
                    item.selectFirst(
                        "a[href*=/anime/]"
                    )
                        ?: return@mapNotNull null

                val href =
                    normalizeUrl(
                        link.attr("href")
                    )

                val title =
                    item.selectFirst("h5")
                        ?.text()
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: item.selectFirst(
                            "a:last-of-type"
                        )
                            ?.text()
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }
                        ?: return@mapNotNull null

                val poster =
                    item.selectFirst(".set-bg")
                        ?.attr("data-setbg")
                        ?.takeIf {
                            it.isNotBlank()
                        }

                newAnimeSearchResponse(
                    title,
                    href,
                    TvType.Anime
                ) {
                    this.posterUrl = poster
                }
            }
            .distinctBy {
                it.url
            }
    }

    // =========================================================
    // LOAD ANIME DETAIL
    // =========================================================

    override suspend fun load(
        url: String
    ): LoadResponse {

        val path =
            url.substringAfter(".ing")

        val result =
            getWithFallback(path)

        val document =
            result?.second
                ?: return newAnimeLoadResponse(
                    "Unknown",
                    url,
                    TvType.Anime
                )

        val animeUrl =
            url.replace(
                Regex("/episode/.*$"),
                ""
            )

        val title =
            document.selectFirst(
                ".anime__details__title h3"
            )
                ?.text()
                ?.trim()
                ?: document.selectFirst("h3")
                    ?.text()
                    ?.trim()
                ?: document.selectFirst("h1")
                    ?.text()
                    ?.trim()
                ?: "Unknown"

        val poster =
            document.selectFirst(
                ".anime__details__pic.set-bg"
            )
                ?.attr("data-setbg")
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: document.selectFirst(
                    ".set-bg"
                )
                    ?.attr("data-setbg")
                    ?.takeIf {
                        it.isNotBlank()
                    }

        val description =
            document.selectFirst(
                ".anime__details__text p"
            )
                ?.text()
                ?.trim()
                ?: document.selectFirst(
                    "meta[name=description]"
                )
                    ?.attr("content")
                    ?.trim()

        val genres =
            document.select(
                ".anime__details__widget " +
                    "a[href*=/properties/genre/]"
            )
                .map {
                    it.text()
                        .replace(",", "")
                        .trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        val episodes =
            mutableListOf<Episode>()

        var currentDocument =
            document

        while (true) {

            val episodeHtml =
                currentDocument
                    .selectFirst(
                        "a#episodeLists"
                    )
                    ?.attr("data-content")
                    ?: ""

            if (episodeHtml.isBlank()) {
                break
            }

            val episodeDocument =
                Jsoup.parse(
                    episodeHtml
                )

            episodeDocument
                .select(
                    "a[href*=/episode/]"
                )
                .forEach { episodeLink ->

                    val episodeUrl =
                        episodeLink
                            .attr("href")
                            .trim()

                    if (episodeUrl.isBlank()) {
                        return@forEach
                    }

                    val episodeText =
                        episodeLink
                            .text()
                            .trim()

                    if (
                        episodeText.contains(
                            "Terlama",
                            ignoreCase = true
                        ) ||
                        episodeText.contains(
                            "Terbaru",
                            ignoreCase = true
                        )
                    ) {
                        return@forEach
                    }

                    val episodeNumber =
                        Regex(
                            "/episode/(\\d+)"
                        )
                            .find(episodeUrl)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()

                    episodes.add(
                        newEpisode(
                            normalizeUrl(
                                episodeUrl
                            )
                        ) {

                            name =
                                "Episode ${
                                    episodeNumber
                                        ?: episodeText
                                }"

                            episode =
                                episodeNumber
                        }
                    )
                }

            val nextPage =
                episodeDocument
                    .selectFirst(
                        "a.page__link__episode" +
                            ":has(i.fa-forward)"
                    )
                    ?.attr("href")

            if (
                !nextPage.isNullOrBlank()
            ) {

                val nextResult =
                    getWithFallback(
                        nextPage
                            .substringAfter(".ing")
                    )

                if (
                    nextResult == null
                ) {
                    break
                }

                currentDocument =
                    nextResult.second

            } else {
                break
            }
        }

        val sortedEpisodes =
            episodes
                .distinctBy {
                    it.data
                }
                .sortedBy {
                    it.episode
                }

        return newAnimeLoadResponse(
            title,
            animeUrl,
            TvType.Anime
        ) {

            this.posterUrl =
                poster

            this.plot =
                description

            this.tags =
                genres

            addEpisodes(
                DubStatus.Subbed,
                sortedEpisodes
            )
        }
    }

    // =========================================================
    // LOAD LINKS
    // =========================================================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (
            SubtitleFile
        ) -> Unit,
        callback: (
            ExtractorLink
        ) -> Unit
    ): Boolean {

        var found = false

        val episodePath =
            data.substringAfter(".ing")

        val result =
            getWithFallback(
                episodePath
            )
                ?: return false

        val document =
            result.second

        val html =
            document.outerHtml()

        try {

            // -------------------------------------------------
            // CSRF TOKEN
            // -------------------------------------------------

            val csrf =
                document.selectFirst(
                    "meta[name=csrf-token]"
                )
                    ?.attr("content")

            // -------------------------------------------------
            // data-kk
            // -------------------------------------------------

            val kk =
                Regex(
                    """data-kk="([^"]+)""""
                )
                    .find(html)
                    ?.groupValues
                    ?.getOrNull(1)

            if (
                !csrf.isNullOrBlank() &&
                !kk.isNullOrBlank()
            ) {

                // -------------------------------------------------
                // PLAYER CONFIG
                // -------------------------------------------------

                val configUrl =
                    "$activeBaseUrl/assets/js/$kk.js"

                val config =
                    app.get(
                        configUrl,
                        headers = mapOf(
                            "Referer" to data,
                            "User-Agent" to userAgent
                        )
                    ).text

                fun configValue(
                    key: String
                ): String {

                    return Regex(
                        "$key:\\s*'([^']+)'"
                    )
                        .find(config)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?: ""
                }

                val authParam =
                    configValue(
                        "MIX_AUTH_ROUTE_PARAM"
                    )

                val pageTokenKey =
                    configValue(
                        "MIX_PAGE_TOKEN_KEY"
                    )

                val serverKey =
                    configValue(
                        "MIX_STREAM_SERVER_KEY"
                    )

                val authKey =
                    configValue(
                        "MIX_AUTH_KEY"
                    )

                val authToken =
                    configValue(
                        "MIX_AUTH_TOKEN"
                    )

                val fuckId =
                    "$authKey:$authToken"

                if (
                    authParam.isNotBlank() &&
                    pageTokenKey.isNotBlank() &&
                    serverKey.isNotBlank()
                ) {

                    // -------------------------------------------------
                    // REQUEST ID
                    // -------------------------------------------------

                    val requestId =
                        (1..6)
                            .map {
                                ('a'..'z').random()
                            }
                            .joinToString("")

                    // -------------------------------------------------
                    // GET TOKEN
                    // -------------------------------------------------

                    val token =
                        app.get(
                            "$activeBaseUrl/assets/$authParam",
                            headers = mapOf(
                                "X-Fuck-ID" to fuckId,
                                "X-Request-ID" to requestId,
                                "X-Request-Index" to "0",
                                "Referer" to data,
                                "User-Agent" to userAgent
                            )
                        )
                            .text
                            .trim()

                    if (
                        token.isNotBlank()
                    ) {

                        // -------------------------------------------------
                        // PLAYER REQUEST
                        // -------------------------------------------------

                        val playerUrl =
                            "$data?" +
                                "$pageTokenKey=$token&" +
                                "$serverKey=kuramadrive&" +
                                "page=1"

                        val postDocument =
                            app.post(
                                playerUrl,
                                headers = mapOf(
                                    "Accept" to
                                        "text/html, */*; q=0.01",

                                    "X-Requested-With" to
                                        "XMLHttpRequest",

                                    "X-CSRF-TOKEN" to
                                        csrf,

                                    "Origin" to
                                        activeBaseUrl,

                                    "Referer" to
                                        data,

                                    "User-Agent" to
                                        userAgent
                                ),
                                data = mapOf(
                                    "authorization" to
                                        authValue
                                )
                            )
                                .document

                        // -------------------------------------------------
                        // KURAMADRIVE SOURCES
                        // -------------------------------------------------

                        postDocument
                            .select(
                                "video#player source[src]"
                            )
                            .forEach { source ->

                                val sourceUrl =
                                    source
                                        .attr("src")
                                        .trim()

                                if (
                                    sourceUrl.isBlank()
                                ) {
                                    return@forEach
                                }

                                val quality =
                                    source
                                        .attr("size")
                                        .toIntOrNull()

                                val qualityName =
                                    if (
                                        quality != null
                                    ) {
                                        "KuramaDrive ${quality}p"
                                    } else {
                                        "KuramaDrive"
                                    }

                                callback(
                                    newExtractorLink(
                                        name,
                                        qualityName,
                                        normalizeUrl(
                                            sourceUrl
                                        )
                                    ) {

                                        this.referer =
                                            activeBaseUrl

                                        this.quality =
                                            when (quality) {

                                                1080 ->
                                                    Qualities
                                                        .P1080
                                                        .value

                                                720 ->
                                                    Qualities
                                                        .P720
                                                        .value

                                                480 ->
                                                    Qualities
                                                        .P480
                                                        .value

                                                360 ->
                                                    Qualities
                                                        .P360
                                                        .value

                                                else ->
                                                    Qualities
                                                        .Unknown
                                                        .value
                                            }
                                    }
                                )

                                found = true
                            }

                        // -------------------------------------------------
                        // PIXELDRAIN DOWNLOAD
                        // -------------------------------------------------

                        var currentQuality =
                            Qualities
                                .Unknown
                                .value

                        postDocument
                            .select(
                                "#animeDownloadLink > *"
                            )
                            .forEach { element ->

                                if (
                                    element.tagName()
                                        .equals(
                                            "h6",
                                            ignoreCase = true
                                        )
                                ) {

                                    val text =
                                        element
                                            .text()

                                    currentQuality =
                                        when {

                                            text.contains(
                                                "1080"
                                            ) ->
                                                Qualities
                                                    .P1080
                                                    .value

                                            text.contains(
                                                "720"
                                            ) ->
                                                Qualities
                                                    .P720
                                                    .value

                                            text.contains(
                                                "480"
                                            ) ->
                                                Qualities
                                                    .P480
                                                    .value

                                            text.contains(
                                                "360"
                                            ) ->
                                                Qualities
                                                    .P360
                                                    .value

                                            else ->
                                                Qualities
                                                    .Unknown
                                                    .value
                                        }

                                } else {

                                    element
                                        .select(
                                            "a[href]"
                                        )
                                        .forEach { link ->

                                            val href =
                                                link
                                                    .attr(
                                                        "href"
                                                    )

                                            val pixelDrainId =
                                                Regex(
                                                    "pixeldrain\\.com/[du]/(\\w+)"
                                                )
                                                    .find(
                                                        href
                                                    )
                                                    ?.groupValues
                                                    ?.getOrNull(
                                                        1
                                                    )

                                            if (
                                                pixelDrainId != null
                                            ) {

                                                callback(
                                                    newExtractorLink(
                                                        name,
                                                        "PixelDrain",
                                                        "https://pixeldrain.com/api/file/$pixelDrainId"
                                                    ) {

                                                        this.quality =
                                                            currentQuality

                                                        this.referer =
                                                            activeBaseUrl
                                                    }
                                                )

                                                found = true
                                            }
                                        }
                                }
                            }
                    }
                }
            }

        } catch (_: Exception) {
            // Keep fallback extractor below alive.
        }

        // =========================================================
        // FALLBACK IFRAME / EXTRACTOR
        // =========================================================

        if (!found) {

            document
                .select(
                    "iframe[src], iframe[data-src]"
                )
                .forEach { iframe ->

                    val src =
                        iframe.attr("src")
                            .ifBlank {
                                iframe.attr(
                                    "data-src"
                                )
                            }
                            .trim()

                    if (
                        src.isBlank()
                    ) {
                        return@forEach
                    }

                    val fullSrc =
                        if (
                            src.startsWith("//")
                        ) {
                            "https:$src"
                        } else {
                            src
                        }

                    try {

                        loadExtractor(
                            fullSrc,
                            data,
                            subtitleCallback,
                            callback
                        )

                    } catch (_: Exception) {
                    }
                }
        }

        return found
    }

    private val userAgent =
        "Mozilla/5.0 (Linux; Android 10; K) " +
            "AppleWebKit/537.36 " +
            "(KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
}
