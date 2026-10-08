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

        "$mainUrl/anime/page/%d/" to
            "Anime Terbaru",

        "$mainUrl/anime/page/%d/?order=update" to
            "Diupdate",

        "$mainUrl/anime/page/%d/?order=latest" to
            "Baru Ditambahkan",

        "$mainUrl/anime/page/%d/?order=popular" to
            "Terpopuler",

        "$mainUrl/anime/page/%d/?order=rating" to
            "Rating Tertinggi",

        "$mainUrl/anime/page/%d/?order=az" to
            "A-Z",

        "$mainUrl/anime/page/%d/?order=za" to
            "Z-A"
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
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

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
         * Struktur katalog saat ini:
         *
         * <article>
         *     ...
         *     <a href="/anime/anime-slug/">
         *         ...
         *     </a>
         *     ...
         *     <h2>Judul Anime</h2>
         * </article>
         *
         * Kita tidak bergantung pada class lama.
         */

        val linkElement = selectFirst(
            "a[href*='/anime/']"
        ) ?: selectFirst(
            "h2 a[href]",
            "h3 a[href]",
            "a[href]"
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
         * Hindari link kategori seperti:
         * /anime/
         */
        if (
            fixedUrl == "$mainUrl/anime/" ||
            fixedUrl == "$mainUrl/anime"
        ) {
            return null
        }

        /*
         * Prioritas judul:
         * 1. h2
         * 2. h3
         * 3. entry-title
         * 4. anchor
         */
        val title = selectFirst(
            "h2",
            "h3",
            ".entry-title",
            ".title"
        )
            ?.text()
            ?.trim()
            ?.replace(
                Regex("\\s+"),
                " "
            )
            ?: linkElement
                .text()
                .trim()

        if (title.isBlank()) {
            return null
        }

        /*
         * Poster.
         *
         * Website bisa menggunakan:
         * src
         * data-src
         * data-lazy-src
         * data-original
         */
        val poster = selectFirst(
            "img"
        )?.let { img ->

            img.attr("data-src")
                .ifBlank {
                    img.attr("data-lazy-src")
                }
                .ifBlank {
                    img.attr("data-original")
                }
                .ifBlank {
                    img.attr("src")
                }
                .trim()

        } ?: ""

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

        /*
         * Endpoint utama yang kita coba:
         *
         * /anime/?title=QUERY
         */
        val primaryUrl =
            "$mainUrl/anime/?title=$encodedQuery"

        var document = app.get(
            primaryUrl,
            headers = headers
        ).document

        var results = document
            .select("article")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }

        /*
         * Fallback ke WordPress search lama
         * apabila endpoint title tidak menghasilkan apa-apa.
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
                .mapNotNull { it.toSearchResult() }
                .distinctBy { it.url }
        }

        return results
    }

    // =========================================================
    // DETAIL ANIME
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

        val title = document
            .selectFirst(
                "h1.entry-title",
                "h1"
            )
            ?.text()
            ?.trim()
            ?.replace(
                Regex("\\s+"),
                " "
            )
            ?.replace(
                Regex("\\s+Sub\\s*Indo.*$"),
                "",
                ignoreCase = true
            )
            ?.trim()
            ?: "Unknown"

        // =====================================================
        // POSTER
        // =====================================================

        /*
         * OpenGraph biasanya lebih stabil
         * daripada class poster yang bisa berubah.
         */
        val poster = document
            .selectFirst(
                "meta[property='og:image']"
            )
            ?.attr("content")
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?: document
                .selectFirst(
                    ".infoanime img",
                    ".thumb img",
                    ".animeposter img",
                    "img"
                )
                ?.let { img ->

                    img.attr("data-src")
                        .ifBlank {
                            img.attr("data-lazy-src")
                        }
                        .ifBlank {
                            img.attr("data-original")
                        }
                        .ifBlank {
                            img.attr("src")
                        }
                        .trim()
                }
                ?: ""

        // =====================================================
        // DESCRIPTION
        // =====================================================

        val description = document
            .selectFirst(
                "meta[property='og:description']"
            )
            ?.attr("content")
            ?.trim()
            ?.takeIf {
                it.isNotBlank()
            }
            ?: document
                .selectFirst(
                    ".entry-content",
                    ".desc",
                    ".description",
                    ".sinopsis"
                )
                ?.text()
                ?.trim()
                ?: ""

        // =====================================================
        // EPISODES
        // =====================================================

        /*
         * Struktur aktual detail:
         *
         * List Episode
         *
         * Tensei shitara Ken deshita S2 Episode 1
         *
         * Link episode mengarah langsung ke:
         *
         * /tensei-shitara-ken-deshita-s2-episode-1/
         *
         * Karena class episode bisa berubah,
         * kita cari link berdasarkan pola teks/url.
         */

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
                 * Harus terlihat seperti episode.
                 */
                val episodeMatch = Regex(
                    """(?i)\bepisode[\s\-]*(\d+(?:\.\d+)?)\b"""
                ).find(text)

                if (episodeMatch == null) {
                    return@mapNotNull null
                }

                /*
                 * Jangan menangkap link komentar / related
                 * yang bukan episode utama.
                 */
                val fixedUrl = fixUrlNull(href)
                    ?: return@mapNotNull null

                val episodeNumber =
                    episodeMatch
                        .groupValues
                        .getOrNull(1)
                        ?.toFloatOrNull()

                if (episodeNumber == null) {
                    return@mapNotNull null
                }

                newEpisode(
                    fixedUrl
                ) {

                    name = text

                    episode =
                        episodeNumber
                            .toInt()

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
            .select(
                "a[href*='/genre/']"
            )
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
    // LOAD VIDEO LINKS
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
        // METHOD 1
        // SERVER AJAX
        // =====================================================

        /*
         * Kita tidak lagi mengandalkan:
         *
         * #server > ul > li > div
         *
         * saja.
         *
         * Cari elemen apa pun yang mempunyai:
         *
         * data-post
         * data-nume
         * data-type
         */

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

                val type = server
                    .attr("data-type")
                    .trim()

                if (
                    postId.isBlank() ||
                    nume.isBlank() ||
                    type.isBlank()
                ) {
                    continue
                }

                val serverName =
                    server
                        .selectFirst("span")
                        ?.text()
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: server.text()
                            .trim()
                            .ifBlank {
                                "Samehadaku"
                            }

                val requestBody =
                    FormBody.Builder()
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
                            type
                        )
                        .build()

                val response = app.post(
                    "$mainUrl/wp-admin/admin-ajax.php",
                    requestBody = requestBody,
                    headers = mapOf(
                        "User-Agent" to
                            headers["User-Agent"]
                                .orEmpty(),

                        "Referer" to data,

                        "Origin" to mainUrl,

                        "X-Requested-With" to
                            "XMLHttpRequest"
                    )
                )

                val html = response.text

                /*
                 * Cari iframe dari response AJAX.
                 */
                val iframeUrl =
                    Regex(
                        """(?:src|data-src)\s*=\s*["']([^"']+)["']""",
                        RegexOption.IGNORE_CASE
                    )
                        .find(html)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()

                if (
                    iframeUrl.isNullOrBlank()
                ) {
                    continue
                }

                val fixedUrl =
                    fixUrlNull(iframeUrl)
                        ?: continue

                /*
                 * Serahkan host embed ke extractor
                 * CloudStream.
                 */
                val loaded =
                    loadExtractor(
                        fixedUrl,
                        data,
                        subtitleCallback,
                        callback
                    )

                if (loaded) {
                    found = true
                }

            } catch (_: Exception) {
                /*
                 * Satu server error tidak boleh
                 * menghentikan server lainnya.
                 */
                continue
            }
        }

        // =====================================================
        // METHOD 2
        // DIRECT IFRAME FALLBACK
        // =====================================================

        /*
         * Halaman episode aktual sekarang sudah
         * menyediakan iframe player.
         *
         * Contohnya saat ini iframe mengarah ke Blogger.
         *
         * Jadi walaupun AJAX server berubah,
         * kita masih punya fallback.
         */

        val iframes = document
            .select(
                "iframe[src], iframe[data-src]"
            )
            .mapNotNull { iframe ->

                val src = iframe
                    .attr("src")
                    .ifBlank {
                        iframe.attr("data-src")
                    }
                    .trim()

                if (src.isBlank()) {
                    null
                } else {
                    fixUrlNull(src)
                }
            }
            .distinct()

        for (iframe in iframes) {

            try {

                val loaded =
                    loadExtractor(
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