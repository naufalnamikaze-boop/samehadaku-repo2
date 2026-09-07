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
    override val hasMainPage = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Anime
    )

    override val mainPage = mainPageOf(
        "$mainUrl/daftar-anime-2/page/%d/?order=update" to "Update Terbaru",
        "$mainUrl/daftar-anime-2/page/%d/?type=movie" to "Movie",
        "$mainUrl/daftar-anime-2/page/%d/?type=special" to "Special",
        "$mainUrl/daftar-anime-2/page/%d/?type=ova" to "OVA",
        "$mainUrl/daftar-anime-2/page/%d/?type=ona" to "ONA",
        "$mainUrl/daftar-anime-2/page/%d/?status=Finished+Airing" to "Finished Airing"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data.format(page)).document

        val home = document
            .select("div.relat > article, main.site-main.relat > article")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val linkElement = selectFirst(
            "div > a, .content-thumb a, .title a, h2 a"
        ) ?: return null

        val href = linkElement.attr("href").trim()

        if (href.isBlank()) return null

        val title = selectFirst(
            "div.title > h2, h2.entry-title, .entry-title a, .title"
        )?.text()?.trim() ?: return null

        if (title.isBlank()) return null

        val posterUrl = selectFirst(
            "div.content-thumb img, .thumb img, " +
            ".content-thumb > img, img"
        )?.let { img ->
            img.attr("data-src")
                .ifEmpty { img.attr("data-lazy-src") }
                .ifEmpty { img.attr("data-original") }
                .ifEmpty { img.attr("src") }
        }?.trim() ?: ""

        return newMovieSearchResponse(
            title,
            href,
            TvType.Anime
        ) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(
        query: String
    ): List<SearchResponse> {

        val encodedQuery = URLEncoder.encode(
            query,
            StandardCharsets.UTF_8.toString()
        )

        val document = app.get(
            "$mainUrl/daftar-anime-2/?title=$encodedQuery"
        ).document

        return document
            .select(
                "main.site-main.relat > article, div.relat > article"
            )
            .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(
        url: String
    ): LoadResponse {

        val document = app.get(url).document

        val title = document
            .selectFirst(
                "h1.entry-title, h1.title, " +
                ".entry-title h1, h3.anim-detail"
            )
            ?.text()
            ?.trim()
            ?.removeSuffix("Sub Indo")
            ?.trim()
            ?: "Unknown"

        val poster = document
            .selectFirst(
                "div.infoanime.widget_senction > div.thumb > img, " +
                "div.episodeinf > div.infoanime > " +
                "div.areainfo > div.thumb > img, " +
                ".thumb img, div.thumb img, .ts-post-image"
            )
            ?.let { img ->
                img.attr("data-src")
                    .ifEmpty { img.attr("data-lazy-src") }
                    .ifEmpty { img.attr("data-original") }
                    .ifEmpty { img.attr("src") }
            }
            ?.trim()
            ?: ""

        val description = document
            .selectFirst(
                "div.entry-content.entry-content-single > p, " +
                "div.desc > div.entry-content.entry-content-single, " +
                ".entry-content, .description"
            )
            ?.text()
            ?.trim()
            ?: ""

        val episodes = document
            .select(
                "div.lstepsiode > ul > li, " +
                ".eplister li, .episodelist ul li"
            )
            .mapNotNull { item ->

                val episodeLink = item.selectFirst(
                    "span.eps > a, a"
                ) ?: return@mapNotNull null

                val epUrl = episodeLink.attr("href").trim()

                if (epUrl.isBlank()) {
                    return@mapNotNull null
                }

                val epNumber = episodeLink.text().trim()

                val epTitle = item.selectFirst(
                    "span.lchx > a, .epl-title, a"
                )?.text()?.trim() ?: epNumber

                newEpisode(epUrl) {

                    name = if (
                        epTitle.contains(
                            "episode",
                            ignoreCase = true
                        )
                    ) {
                        epTitle
                    } else {
                        "Episode $epNumber"
                    }

                    episode = epNumber
                        .filter { it.isDigit() }
                        .toIntOrNull()
                        ?: 0
                }
            }
            .reversed()

        val genres = document
            .select(
                "div.genre-info a, " +
                "div.spe a[rel='tag'], .genres a"
            )
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return newAnimeLoadResponse(
            title,
            url,
            TvType.Anime
        ) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres

            addEpisodes(
                DubStatus.Subbed,
                episodes
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document

        var found = false

        /*
         * ============================================================
         * 1. SERVER PLAYER SAMEHADAKU
         * ============================================================
         */

        val servers = document.select(
            "#server > ul > li > div"
        )

        if (servers.isEmpty()) {

            val iframe = document
                .selectFirst(
                    "iframe[src], iframe[data-src]"
                )
                ?.let {
                    it.attr("src")
                        .ifEmpty {
                            it.attr("data-src")
                        }
                        .trim()
                }

            if (!iframe.isNullOrBlank()) {

                val fixedUrl = fixUrlNull(iframe)

                if (fixedUrl != null) {

                    val loaded = loadExtractor(
                        fixedUrl,
                        data,
                        subtitleCallback,
                        callback
                    )

                    if (loaded) {
                        found = true
                    }
                }
            }

        } else {

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

                    val serverName = server
                        .selectFirst("span")
                        ?.text()
                        ?.trim()
                        ?: "Samehadaku"

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

                    val ajaxResponse = app.post(
                        "$mainUrl/wp-admin/admin-ajax.php",
                        requestBody = requestBody,
                        headers = mapOf(
                            "User-Agent" to
                                "Mozilla/5.0 (Linux; Android 10; K) " +
                                "AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) " +
                                "Chrome/131.0.0.0 Mobile Safari/537.36",

                            "Referer" to data,

                            "X-Requested-With" to
                                "XMLHttpRequest"
                        )
                    )

                    val ajaxHtml =
                        ajaxResponse.text

                    val embedUrl = Regex(
                        """src\s*=\s*["']([^"']+)["']"""
                    )
                        .find(ajaxHtml)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.trim()

                    if (embedUrl.isNullOrBlank()) {
                        continue
                    }

                    val fixedEmbedUrl =
                        fixUrlNull(embedUrl)
                            ?: continue

                    if (
                        fixedEmbedUrl.contains(
                            ".m3u8",
                            ignoreCase = true
                        ) ||
                        fixedEmbedUrl.contains(
                            ".mp4",
                            ignoreCase = true
                        ) ||
                        fixedEmbedUrl.contains(
                            ".webm",
                            ignoreCase = true
                        )
                    ) {

                        val quality =
                            getQualityFromName(
                                serverName
                            )

                        val linkType =
                            if (
                                fixedEmbedUrl.contains(
                                    ".m3u8",
                                    ignoreCase = true
                                )
                            ) {
                                ExtractorLinkType.M3U8
                            } else {
                                ExtractorLinkType.VIDEO
                            }

                        callback(
                            newExtractorLink(
                                name,
                                serverName,
                                fixedEmbedUrl,
                                linkType
                            ) {
                                this.referer = data
                                this.quality = quality
                            }
                        )

                        found = true

                    } else {

                        val beforeCount =
                            0

                        val loaded =
                            loadExtractor(
                                fixedEmbedUrl,
                                data,
                                subtitleCallback,
                                callback
                            )

                        if (loaded) {
                            found = true
                        }
                    }

                } catch (_: Exception) {
                    continue
                }
            }
        }

        /*
         * ============================================================
         /*
         * ============================================================
         * 2. DOWNLOAD SOURCE - ACEFILE
         * ============================================================
         */

        val acefileLinks = document
            .select("a[href]")
            .mapNotNull { element ->

                val href = element
                    .attr("href")
                    .trim()

                if (
                    href.contains(
                        "acefile.co/f/",
                        ignoreCase = true
                    )
                ) {
                    href
                } else {
                    null
                }
            }
            .distinct()

        for (acefileUrl in acefileLinks) {

            var acefileFound = false

            val acefileCallback:
                (ExtractorLink) -> Unit = { link ->

                acefileFound = true

                // Teruskan langsung link hasil extractor Acefile.
                // Tidak perlu membuat ExtractorLink baru.
                callback(link)
            }

            var success = false

            try {
                success = loadExtractor(
                    acefileUrl,
                    data,
                    subtitleCallback,
                    acefileCallback
                )
            } catch (_: Exception) {
                success = false
            }

            if (success && acefileFound) {
                found = true
            }
        }

        return found
    }
}
