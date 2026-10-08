package com.example.samehadaku

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import okhttp3.FormBody
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class Samehadaku : MainAPI() {

    override var mainUrl = "https://v2.samehadaku.how"
    override var name = "Samehadaku"
    override var lang = "id"

    override val hasMainPage = true

    override val supportedTypes = setOf(
        TvType.Anime
    )

    // =========================================================
    // HEADER
    // =========================================================

    private val headers = mapOf(
        "User-Agent" to
            "Mozilla/5.0 (Linux; Android 10; K) " +
            "AppleWebKit/537.36 " +
            "(KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Mobile Safari/537.36"
    )

    // =========================================================
    // MAIN PAGE
    // =========================================================

    override val mainPage = mainPageOf(
        "$mainUrl/anime/page/%d/" to "Anime Terbaru",
        "$mainUrl/anime/page/%d/?order=update" to "Diupdate",
        "$mainUrl/anime/page/%d/?order=latest" to "Baru Ditambahkan",
        "$mainUrl/anime/page/%d/?order=popular" to "Terpopuler",
        "$mainUrl/anime/page/%d/?order=rating" to "Rating Tertinggi",
        "$mainUrl/anime/page/%d/?order=az" to "A-Z",
        "$mainUrl/anime/page/%d/?order=za" to "Z-A"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = request.data.format(page)

        val document = app.get(
            url,
            headers = headers
        ).document

        val home = document
            .select("article")
            .mapNotNull {
                it.toSearchResult()
            }
            .distinctBy {
                it.url
            }

        return newHomePageResponse(
            request.name,
            home,
            hasNext = home.isNotEmpty()
        )
    }

    // =========================================================
    // PARSE ANIME CARD
    // =========================================================

    private fun Element.toSearchResult(): SearchResponse? {

        /*
         * Cari link anime.
         */
        val linkElement = selectFirst(
            "a[href*='/anime/']"
        ) ?: return null

        val href = linkElement
            .attr("href")
            .trim()

        if (href.isBlank()) {
            return null
        }

        val fixedUrl = fixUrlNull(href)
            ?: return null

        /*
         * Jangan mengambil halaman katalog
         * sebagai anime.
         */
        if (
            fixedUrl == "$mainUrl/anime" ||
            fixedUrl == "$mainUrl/anime/"
        ) {
            return null
        }

        /*
         * Cari judul.
         */
        var title = ""

        val h2 = selectFirst("h2")

        if (h2 != null) {
            title = h2.text().trim()
        }

        if (title.isBlank()) {
            val h3 = selectFirst("h3")

            if (h3 != null) {
                title = h3.text().trim()
            }
        }

        if (title.isBlank()) {
            val entryTitle = selectFirst(".entry-title")

            if (entryTitle != null) {
                title = entryTitle.text().trim()
            }
        }

        if (title.isBlank()) {
            title = linkElement.text().trim()
        }

        if (title.isBlank()) {
            return null
        }

        /*
         * Poster.
         */
        val image = selectFirst("img")

        var poster = ""

        if (image != null) {

            poster = image
                .attr("data-src")
                .trim()

            if (poster.isBlank()) {
                poster = image
                    .attr("data-lazy-src")
                    .trim()
            }

            if (poster.isBlank()) {
                poster = image
                    .attr("data-original")
                    .trim()
            }

            if (poster.isBlank()) {
                poster = image
                    .attr("src")
                    .trim()
            }
        }

        return newAnimeSearchResponse(
            title,
            fixedUrl,
            TvType.Anime
        ) {
            posterUrl = poster
        }
    }

    // =========================================================
    // SEARCH
    // =========================================================

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val encodedQuery = URLEncoder.encode(
            query,
            StandardCharsets.UTF_8.toString()
        )

        val searchUrl =
            "$mainUrl/anime/?title=$encodedQuery"

        var document = app.get(
            searchUrl,
            headers = headers
        ).document

        var results = document
            .select("article")
            .mapNotNull {
                it.toSearchResult()
            }
            .distinctBy {
                it.url
            }

        /*
         * Fallback WordPress search.
         */
        if (results.isEmpty()) {

            val fallbackUrl =
                "$mainUrl/?s=$encodedQuery"

            document = app.get(
                fallbackUrl,
                headers = headers
            ).document

            results = document
                .select("article")
                .mapNotNull {
                    it.toSearchResult()
                }
                .distinctBy {
                    it.url
                }
        }

        return results
    }

    // =========================================================
    // DETAIL
    // =========================================================

    override suspend fun load(
        url: String
    ): LoadResponse {

        val document = app.get(
            url,
            headers = headers
        ).document

        // =====================================================
        // TITLE
        // =====================================================

        var title = ""

        val h1 = document.selectFirst(
            "h1.entry-title"
        )

        if (h1 != null) {
            title = h1.text().trim()
        }

        if (title.isBlank()) {

            val genericH1 = document.selectFirst(
                "h1"
            )

            if (genericH1 != null) {
                title = genericH1.text().trim()
            }
        }

        if (title.isBlank()) {
            title = "Unknown"
        }

        /*
         * Bersihkan suffix Sub Indo.
         */
        title = title
            .replace(
                "Sub Indo",
                "",
                ignoreCase = true
            )
            .trim()

        // =====================================================
        // POSTER
        // =====================================================

        var poster = ""

        val ogImage = document.selectFirst(
            "meta[property='og:image']"
        )

        if (ogImage != null) {
            poster = ogImage
                .attr("content")
                .trim()
        }

        if (poster.isBlank()) {

            val posterImage = document.selectFirst(
                ".infoanime img"
            )

            if (posterImage != null) {

                poster = posterImage
                    .attr("data-src")
                    .trim()

                if (poster.isBlank()) {
                    poster = posterImage
                        .attr("data-lazy-src")
                        .trim()
                }

                if (poster.isBlank()) {
                    poster = posterImage
                        .attr("src")
                        .trim()
                }
            }
        }

        if (poster.isBlank()) {

            val genericImage = document.selectFirst(
                "img"
            )

            if (genericImage != null) {

                poster = genericImage
                    .attr("src")
                    .trim()
            }
        }

        // =====================================================
        // DESCRIPTION
        // =====================================================

        var description = ""

        val ogDescription = document.selectFirst(
            "meta[property='og:description']"
        )

        if (ogDescription != null) {
            description = ogDescription
                .attr("content")
                .trim()
        }

        if (description.isBlank()) {

            val entryContent = document.selectFirst(
                ".entry-content"
            )

            if (entryContent != null) {
                description = entryContent
                    .text()
                    .trim()
            }
        }

        // =====================================================
        // EPISODES
        // =====================================================

        val episodes = document
            .select("a[href]")
            .mapNotNull { link ->

                val href = link
                    .attr("href")
                    .trim()

                val text = link
                    .text()
                    .trim()
                    .replace(
                        Regex("\\s+"),
                        " "
                    )

                if (
                    href.isBlank() ||
                    text.isBlank()
                ) {
                    return@mapNotNull null
                }

                /*
                 * Cari "Episode 1", "Episode 2",
                 * "Episode 12", dll.
                 */
                val match = Regex(
                    """(?i)\bEpisode[\s\-]*(\d+(?:\.\d+)?)\b"""
                ).find(text)

                if (match == null) {
                    return@mapNotNull null
                }

                val episodeNumber =
                    match
                        .groupValues
                        .getOrNull(1)
                        ?.toFloatOrNull()
                        ?: return@mapNotNull null

                val episodeUrl =
                    fixUrlNull(href)
                        ?: return@mapNotNull null

                newEpisode(
                    episodeUrl
                ) {

                    name = text

                    episode =
                        episodeNumber.toInt()
                }
            }
            .distinctBy {
                it.data
            }
            .sortedBy {
                it.episode ?: 0
            }

        // =====================================================
        // GENRES
        // =====================================================

        val genres = document
            .select("a[href*='/genre/']")
            .map {
                it.text().trim()
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()

        // =====================================================
        // RESPONSE
        // =====================================================

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {

            posterUrl = poster

            plot = description

            tags = genres

            addEpisodes(
                DubStatus.Subbed,
                episodes
            )
        }
    }

    // =========================================================
    // VIDEO LINKS
    // =========================================================

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(
            data,
            headers = headers
        ).document

        var found = false

        // =====================================================
        // AJAX SERVERS
        // =====================================================

        val servers = document.select(
            "[data-post][data-nume][data-type]"
        )

        for (server in servers) {

            try {

                val postId = server
                    .attr("data-post")
                    .trim()

                val nume = server
                    .attr("data-nume")
                    .trim()

                val serverType = server
                    .attr("data-type")
                    .trim()

                if (
                    postId.isBlank() ||
                    nume.isBlank() ||
                    serverType.isBlank()
                ) {
                    continue
                }

                var serverName = server.text().trim()

                if (serverName.isBlank()) {
                    serverName = "Samehadaku"
                }

                val body = FormBody.Builder()
                    .add(
                        "action",
                        "player_ajax"
                    )
                    .add(
                        "post",
                        postId
                    )
                    .add(
                        "nume",
                        nume
                    )
                    .add(
                        "type",
                        serverType
                    )
                    .build()

                val response = app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    requestBody = body,
                    headers = mapOf(
                        "User-Agent" to
                            headers["User-Agent"].orEmpty(),

                        "Referer" to data,

                        "Origin" to mainUrl,

                        "X-Requested-With" to
                            "XMLHttpRequest"
                    )
                )

                val html = response.text

                val iframeMatch = Regex(
                    """(?:src|data-src)\s*=\s*["']([^"']+)["']""",
                    RegexOption.IGNORE_CASE
                ).find(html)

                if (iframeMatch == null) {
                    continue
                }

                val iframeUrl =
                    iframeMatch
                        .groupValues
                        .getOrNull(1)
                        ?.trim()
                        ?: continue

                val fixedUrl =
                    fixUrlNull(iframeUrl)
                        ?: continue

                val loaded = loadExtractor(
                    fixedUrl,
                    data,
                    subtitleCallback,
                    callback
                )

                if (loaded) {
                    found = true
                }

            } catch (_: Exception) {
                continue
            }
        }

        // =====================================================
        // IFRAME FALLBACK
        // =====================================================

        val iframes = document
            .select("iframe")
            .mapNotNull { iframe ->

                var src = iframe
                    .attr("src")
                    .trim()

                if (src.isBlank()) {
                    src = iframe
                        .attr("data-src")
                        .trim()
                }

                if (src.isBlank()) {
                    return@mapNotNull null
                }

                fixUrlNull(src)
            }
            .distinct()

        for (iframe in iframes) {

            try {

                val loaded = loadExtractor(
                    iframe,
                    data,
                    subtitleCallback,
                    callback
                )

                if (loaded) {
                    found = true
                }

            } catch (_: Exception) {
                continue
            }
        }

        return found
    }
}