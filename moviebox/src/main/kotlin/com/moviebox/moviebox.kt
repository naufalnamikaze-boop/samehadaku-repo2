package com.moviebox

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class Moviebox : MainAPI() {

    override var mainUrl = "https://moviebox.ph"

    private val apiUrl = "https://fmoviesunblocked.net"

    override val instantLinkLoading = true
    override var name = "Moviebox"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override var lang = "id"

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AsianDrama
    )

    override val mainPage = mainPageOf(
        "872031290915189720" to "Trending Now",
        "997144265920760504" to "Popular Movie",
        "5283462032510044280" to "Drama Indonesia Terkini",
        "6528093688173053896" to "Trending Indonesian Movies",
        "4380734070238626200" to "K-Drama",
        "7736026911486755336" to "Western TV",
        "8624142774394406504" to "Most Popular C-Drama",
        "5404290953194750296" to "Trending Anime",
        "5848753831881965888" to "Indonesian Horror Stories",
        "1164329479448281992" to "Thai-Drama",
        "7132534597631837112" to "Animated Film"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {

        val url =
            "$mainUrl/wefeed-h5-bff/web/ranking-list/content" +
            "?id=${request.data}" +
            "&page=$page" +
            "&perPage=12"

        val home = app.get(url)
            .parsedSafe<Media>()
            ?.data
            ?.subjectList
            ?.map { it.toSearchResponse(this) }
            ?: throw ErrorLoadingException("No Data Found")

        return newHomePageResponse(
            request.name,
            home
        )
    }

    override suspend fun quickSearch(
    query: String
): List<SearchResponse> = search(query)

override suspend fun search(
    query: String
): List<SearchResponse> {

    return try {

        val timestamp = System.currentTimeMillis().toString()

        val md5 = java.security.MessageDigest
            .getInstance("MD5")
            .digest(timestamp.reversed().toByteArray())
            .joinToString("") {
                "%02x".format(it)
            }

        val clientToken = "$timestamp,$md5"

        // =====================================================
        // 1. Guest session
        // =====================================================

        val bootstrap = app.get(
            "$apiUrl/wefeed-mobile-bff/tab-operating" +
                    "?host=api.inmoviebox.com" +
                    "&page=1" +
                    "&pageSize=24" +
                    "&tabId=1",
            headers = mapOf(
                "Accept" to "application/json",
                "User-Agent" to
                        "MovieBoxPro/16.2.1 (Android 12; Pixel 6)",
                "X-M-Version" to "4.0.02",
                "X-Client-Token" to clientToken,
                "X-Client-Info" to
                        """{"os":"android","os_version":"12","system_language":"en","net":"NETWORK_WIFI"}"""
            )
        )

        val xUser = bootstrap.headers["x-user"]

        if (xUser.isNullOrBlank()) {
            throw ErrorLoadingException(
                "Guest session gagal: x-user tidak ditemukan"
            )
        }

        // =====================================================
        // 2. Search
        // =====================================================

        val body = mapOf(
            "keyword" to query,
            "type" to 0,
            "page" to 1,
            "pageSize" to 20
        ).toJson().toRequestBody(
            RequestBodyTypes.JSON.toMediaTypeOrNull()
        )

        val response = app.post(
            "$apiUrl/wefeed-mobile-bff/subject-api/search",
            headers = mapOf(
                "Accept" to "application/json",
                "Content-Type" to "application/json",
                "User-Agent" to
                        "MovieBoxPro/16.2.1 (Android 12; Pixel 6)",
                "X-M-Version" to "4.0.02",
                "X-Client-Token" to clientToken,
                "X-Client-Info" to
                        """{"os":"android","os_version":"12","system_language":"en","net":"NETWORK_WIFI"}""",
                "Authorization" to "Bearer $xUser"
            ),
            requestBody = body
        )

        response.parsedSafe<Media>()
            ?.data
            ?.items
            ?.map {
                it.toSearchResponse(this)
            }
            ?: emptyList()

    } catch (e: Exception) {

        val error =
            "${e.javaClass.simpleName}: ${e.message}"

        listOf(
            newMovieSearchResponse(
                "TYPE: ${e.javaClass.simpleName}",
                "$mainUrl/debug-error-type",
                TvType.Movie,
                false
            ),
            newMovieSearchResponse(
                "MSG: $error",
                "$mainUrl/debug-error-msg",
                TvType.Movie,
                false
            )
        )
    }
}
    
    override suspend fun load(
    url: String
): LoadResponse {

    val id = url.substringAfterLast("/")

    val doc = app.get(
        "$mainUrl/wefeed-h5-bff/web/subject/detail?subjectId=$id"
    )
        .parsedSafe<MediaDetail>()
        ?.data

    val subject = doc?.subject

    val title = subject?.title ?: ""
    val poster = subject?.cover?.url

    val tags = subject?.genre
        ?.split(",")
        ?.map { it.trim() }

    val year = subject?.releaseDate
        ?.substringBefore("-")
        ?.toIntOrNull()

    val tvType =
        if (subject?.subjectType == 2) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

    val description = subject?.description

    val trailer = subject
        ?.trailer
        ?.videoAddress
        ?.url

    val actors = doc
        ?.stars
        ?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: return@mapNotNull null,
                    cast.avatarUrl
                ),
                roleString = cast.character
            )
        }
        ?.distinctBy { it.actor }

    val recommendations = app.get(
        "$mainUrl/wefeed-h5-bff/web/subject/detail-rec" +
        "?subjectId=$id&page=1&perPage=12"
    )
        .parsedSafe<Media>()
        ?.data
        ?.items
        ?.map { it.toSearchResponse(this) }

    return if (tvType == TvType.TvSeries) {

        val episodes = doc
            ?.resource
            ?.seasons
            ?.flatMap { season ->

                val episodeNumbers =
                    if (season.allEp.isNullOrEmpty()) {
                        1..(season.maxEp ?: 0)
                    } else {
                        season.allEp
                            .split(",")
                            .mapNotNull { it.toIntOrNull() }
                    }

                episodeNumbers.map { episode ->

                    newEpisode(
                        LoadData(
                            id = id,
                            season = season.se,
                            episode = episode,
                            detailPath = subject?.detailPath
                        ).toJson()
                    ) {
                        this.season = season.se
                        this.episode = episode
                    }
                }
            }
            ?: emptyList()

        newTvSeriesLoadResponse(
            title,
            url,
            TvType.TvSeries,
            episodes
        ) {
            posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations

            addTrailer(
                trailer,
                addRaw = true
            )
        }

    } else {

        newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            LoadData(
                id = id,
                detailPath = subject?.detailPath
            ).toJson()
        ) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations

            addTrailer(
                trailer,
                addRaw = true
            )
        }
    }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val media = parseJson<LoadData>(data)

        val referer =
            "$apiUrl/spa/videoPlayPage/movies/" +
            "${media.detailPath}" +
            "?id=${media.id}" +
            "&type=/movie/detail" +
            "&lang=en"

        val streams = app.get(
            "$apiUrl/wefeed-h5-bff/web/subject/play" +
            "?subjectId=${media.id}" +
            "&se=${media.season ?: 0}" +
            "&ep=${media.episode ?: 0}",
            referer = referer
        )
            .parsedSafe<Media>()
            ?.data
            ?.streams

        streams
            ?.reversed()
            ?.distinctBy { it.url }
            ?.forEach { source ->

                val streamUrl = source.url
                    ?: return@forEach

                callback(
                    newExtractorLink(
                        this.name,
                        this.name,
                        streamUrl,
                        INFER_TYPE
                    ) {
                        this.referer = "$apiUrl/"
                        this.quality =
                            getQualityFromName(
                                source.resolutions
                            )
                    }
                )
            }

        val firstStream = streams?.firstOrNull()

        if (firstStream != null) {

            val id = firstStream.id
            val format = firstStream.format

            if (!id.isNullOrBlank() &&
                !format.isNullOrBlank()
            ) {

                app.get(
                    "$apiUrl/wefeed-h5-bff/web/subject/caption" +
                    "?format=$format" +
                    "&id=$id" +
                    "&subjectId=${media.id}",
                    referer = referer
                )
                    .parsedSafe<Media>()
                    ?.data
                    ?.captions
                    ?.forEach { subtitle ->

                        val subtitleUrl =
                            subtitle.url
                                ?: return@forEach

                        subtitleCallback(
                            SubtitleFile(
                                subtitle.lanName ?: "",
                                subtitleUrl
                            )
                        )
                    }
            }
        }

        return true
    }

    data class LoadData(
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val detailPath: String? = null
    )

    data class Media(
        @JsonProperty("data")
        val data: Data? = null
    ) {

        data class Data(

            @JsonProperty("subjectList")
            val subjectList: ArrayList<Items>? = arrayListOf(),

            @JsonProperty("items")
            val items: ArrayList<Items>? = arrayListOf(),

            @JsonProperty("streams")
            val streams: ArrayList<Streams>? = arrayListOf(),

            @JsonProperty("captions")
            val captions: ArrayList<Captions>? = arrayListOf()
        ) {

            data class Streams(
                @JsonProperty("id")
                val id: String? = null,

                @JsonProperty("format")
                val format: String? = null,

                @JsonProperty("url")
                val url: String? = null,

                @JsonProperty("resolutions")
                val resolutions: String? = null
            )

            data class Captions(
                @JsonProperty("lan")
                val lan: String? = null,

                @JsonProperty("lanName")
                val lanName: String? = null,

                @JsonProperty("url")
                val url: String? = null
            )
        }
    }

    data class MediaDetail(
        @JsonProperty("data")
        val data: Data? = null
    ) {

        data class Data(

            @JsonProperty("subject")
            val subject: Items? = null,

            @JsonProperty("stars")
            val stars: ArrayList<Stars>? = arrayListOf(),

            @JsonProperty("resource")
            val resource: Resource? = null
        ) {

            data class Stars(
                @JsonProperty("name")
                val name: String? = null,

                @JsonProperty("character")
                val character: String? = null,

                @JsonProperty("avatarUrl")
                val avatarUrl: String? = null
            )

            data class Resource(
                @JsonProperty("seasons")
                val seasons: ArrayList<Seasons>? = arrayListOf()
            ) {

                data class Seasons(
                    @JsonProperty("se")
                    val se: Int? = null,

                    @JsonProperty("maxEp")
                    val maxEp: Int? = null,

                    @JsonProperty("allEp")
                    val allEp: String? = null
                )
            }
        }
    }

    data class Items(

        @JsonProperty("subjectId")
        val subjectId: String? = null,

        @JsonProperty("subjectType")
        val subjectType: Int? = null,

        @JsonProperty("title")
        val title: String? = null,

        @JsonProperty("description")
        val description: String? = null,

        @JsonProperty("releaseDate")
        val releaseDate: String? = null,

        @JsonProperty("duration")
        val duration: Long? = null,

        @JsonProperty("genre")
        val genre: String? = null,

        @JsonProperty("cover")
        val cover: Cover? = null,

        @JsonProperty("imdbRatingValue")
        val imdbRatingValue: String? = null,

        @JsonProperty("countryName")
        val countryName: String? = null,

        @JsonProperty("trailer")
        val trailer: Trailer? = null,

        @JsonProperty("detailPath")
        val detailPath: String? = null
    ) {

        fun toSearchResponse(
            provider: Moviebox
        ): SearchResponse {

            return provider.newMovieSearchResponse(
                title ?: "",
                subjectId ?: "",
                if (subjectType == 1) {
                    TvType.Movie
                } else {
                    TvType.TvSeries
                },
                false
            ) {
                posterUrl = cover?.url
            }
        }

        data class Cover(
            @JsonProperty("url")
            val url: String? = null
        )

        data class Trailer(
            @JsonProperty("videoAddress")
            val videoAddress: VideoAddress? = null
        ) {

            data class VideoAddress(
                @JsonProperty("url")
                val url: String? = null
            )
        }
    }
}
