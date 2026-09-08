package com.example.lk21

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LK21 : MainAPI() {

    override var mainUrl = "https://tv12.lk21official.cc"
    override var name = "LK21"
    override var lang = "id"

    override val hasMainPage = true
    override val hasDownloadSupport = false

    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/latest" to "Film Terbaru",
        "$mainUrl/popular" to "Film Populer"
    )

    private fun Element.toSearchResult(): SearchResponse? {

        val link = selectFirst(
            "a[href]"
        ) ?: return null

        val href = link.attr("href").trim()

        if (href.isBlank()) {
            return null
        }

        val title = selectFirst(
            "h2, h3, h4, h5, .title, .film-title"
        )
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: link.attr("title")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: link.text()
                .trim()
                .takeIf { it.isNotBlank() }
            ?: return null

        val poster = selectFirst(
            "img"
        )?.let { img ->
            img.attr("data-src")
                .ifEmpty {
                    img.attr("data-lazy-src")
                }
                .ifEmpty {
                    img.attr("src")
                }
                .trim()
        }

        return newMovieSearchResponse(
            title,
            fixUrl(href),
            TvType.Movie
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url = if (
            request.data.contains("%d")
        ) {
            request.data.format(page)
        } else {
            request.data
        }

        val document = app.get(url).document

        val results = document
            .select(
                "article, " +
                ".movie-item, " +
                ".film-item, " +
                ".item, " +
                ".card"
            )
            .mapNotNull {
                it.toSearchResult()
            }
            .distinctBy {
                it.url
            }

        return newHomePageResponse(
            request.name,
            results
        )
    }

    override suspend fun search(
    query: String
): List<SearchResponse> {

    val encoded = URLEncoder.encode(
        query,
        StandardCharsets.UTF_8.toString()
    )

    // Endpoint search lama yang masih digunakan
    // oleh extension LK21 lainnya.
    val url = "$mainUrl/search.php?s=$encoded"

    val document = app.get(url).document

    return document
        .select(
            "div.search-item, " +
            ".search-item"
        )
        .mapNotNull { item ->

            val link = item.selectFirst(
                "h3 > a, h3 a, a[href]"
            ) ?: return@mapNotNull null

            val href = link.attr("href")
                .trim()

            if (href.isBlank()) {
                return@mapNotNull null
            }

            val title = item.selectFirst(
                "h3 > a, h3 a"
            )
                ?.text()
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: link.text()
                    .trim()
                    .takeIf {
                        it.isNotBlank()
                    }
                ?: return@mapNotNull null

            val poster = item.selectFirst(
                "img"
            )?.let { img ->

                img.attr("src")
                    .ifEmpty {
                        img.attr("data-src")
                    }
                    .ifEmpty {
                        img.attr("data-lazy-src")
                    }
                    .trim()
            }

            newMovieSearchResponse(
                title,
                fixUrl(href),
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }
        .distinctBy {
            it.url
        }
}

    override suspend fun load(
        url: String
    ): LoadResponse {

        val document =
            app.get(url).document

        val title =
            document.selectFirst(
                "h1, h2, .title, .film-title"
            )
                ?.text()
                ?.trim()
                ?: "Unknown"

        val poster =
            document.selectFirst(
                "img"
            )?.let { img ->
                img.attr("data-src")
                    .ifEmpty {
                        img.attr("data-lazy-src")
                    }
                    .ifEmpty {
                        img.attr("src")
                    }
                    .trim()
            }

        val description =
            document.selectFirst(
                ".description, " +
                ".desc, " +
                ".sinopsis, " +
                ".synopsis, " +
                ".plot"
            )
                ?.text()
                ?.trim()
                ?: ""

        val genres =
            document.select(
                "a[href*='genre'], " +
                ".genre a, " +
                ".genres a"
            )
                .map {
                    it.text().trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = description
            this.tags = genres
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return false
    }
}