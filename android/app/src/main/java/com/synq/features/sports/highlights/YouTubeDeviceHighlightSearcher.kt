package com.synq.features.sports.highlights

import com.synq.features.sports.domain.model.HighlightPlaybackType
import com.synq.features.sports.domain.model.HighlightSource
import com.synq.features.sports.domain.model.MatchFormat
import com.synq.features.sports.domain.model.MatchHighlight
import com.synq.features.sports.domain.model.MatchSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device fallback searcher for YouTube cricket highlights.
 *
 * Cloud servers (e.g. Render Singapore) cannot see India-geo-restricted official
 * broadcasters like Sony LIV or FanCode in YouTube search results because YouTube
 * geo-fences search responses by caller IP. Running Innertube search from the
 * user's own device IP surfaces highlights licensed in the user's region.
 */
@Singleton
class YouTubeDeviceHighlightSearcher @Inject constructor(
    private val json: Json
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(summary: MatchSummary): List<MatchHighlight> = withContext(Dispatchers.IO) {
        val query = buildSearchQuery(summary)
        val candidates = fetchInnertubeResults(query)
        if (candidates.isEmpty()) return@withContext emptyList()

        val scored = mutableListOf<Pair<Int, MatchHighlight>>()
        val seenIds = mutableSetOf<String>()

        for (candidate in candidates) {
            if (!seenIds.add(candidate.videoId)) continue
            if (!isOfficialChannel(candidate.channelId, candidate.channelName)) continue
            if (candidate.durationSeconds in 1 until MIN_HIGHLIGHT_DURATION_SECONDS) continue

            val score = scoreHighlightMatch(summary, candidate) ?: continue
            val publisher = OFFICIAL_CHANNEL_IDS[candidate.channelId] ?: candidate.channelName

            scored += score to MatchHighlight(
                id = "youtube:${candidate.videoId}",
                title = candidate.title,
                thumbnailUrl = candidate.thumbnailUrl,
                duration = candidate.duration.ifBlank { "0:00" },
                durationSeconds = candidate.durationSeconds,
                publishedAt = candidate.publishedText,
                source = HighlightSource.YOUTUBE,
                playbackType = HighlightPlaybackType.YOUTUBE,
                streamTarget = candidate.videoId,
                externalUrl = "https://www.youtube.com/watch?v=${candidate.videoId}",
                publisher = publisher
            )
        }

        scored
            .sortedByDescending { it.first }
            .take(10)
            .map { it.second }
    }

    private fun buildSearchQuery(summary: MatchSummary): String {
        val year = if (summary.startTime > 0L) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = summary.startTime
            }.get(Calendar.YEAR).toString()
        } else ""
        return listOf(summary.team1.name, "vs", summary.team2.name, summary.matchDesc, year, "highlights")
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun fetchInnertubeResults(query: String): List<YouTubeCandidate> {
        return try {
            val payload = """
                {
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "2.20260101.00.00",
                      "hl": "en"
                    }
                  },
                  "query": ${jsonEscape(query)}
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                parseInnertubePayload(body)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseInnertubePayload(rawJson: String): List<YouTubeCandidate> {
        return try {
            val root = json.parseToJsonElement(rawJson).jsonObject
            val sections = root["contents"]
                ?.jsonObject?.get("twoColumnSearchResultsRenderer")
                ?.jsonObject?.get("primaryContents")
                ?.jsonObject?.get("sectionListRenderer")
                ?.jsonObject?.get("contents")
                ?.jsonArray ?: return emptyList()

            val results = mutableListOf<YouTubeCandidate>()
            for (section in sections) {
                val items = (section as? JsonObject)
                    ?.get("itemSectionRenderer")
                    ?.jsonObject?.get("contents")
                    ?.jsonArray ?: continue
                for (item in items) {
                    val renderer = (item as? JsonObject)?.get("videoRenderer")?.jsonObject ?: continue
                    val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull ?: continue
                    val title = renderer["title"]
                        ?.jsonObject?.get("runs")
                        ?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("text")
                        ?.jsonPrimitive?.contentOrNull.orEmpty()
                    val ownerRun = renderer["ownerText"]
                        ?.jsonObject?.get("runs")
                        ?.jsonArray?.firstOrNull()
                        ?.jsonObject
                    val channelName = ownerRun?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val channelId = ownerRun?.get("navigationEndpoint")
                        ?.jsonObject?.get("browseEndpoint")
                        ?.jsonObject?.get("browseId")
                        ?.jsonPrimitive?.contentOrNull.orEmpty()
                    val duration = renderer["lengthText"]
                        ?.jsonObject?.get("simpleText")
                        ?.jsonPrimitive?.contentOrNull.orEmpty()
                    val publishedText = renderer["publishedTimeText"]
                        ?.jsonObject?.get("simpleText")
                        ?.jsonPrimitive?.contentOrNull
                    val thumbnails = renderer["thumbnail"]?.jsonObject?.get("thumbnails") as? JsonArray
                    val thumbnailUrl = thumbnails?.lastOrNull()
                        ?.jsonObject?.get("url")
                        ?.jsonPrimitive?.contentOrNull

                    results += YouTubeCandidate(
                        videoId = videoId,
                        title = title,
                        channelName = channelName,
                        channelId = channelId,
                        duration = duration,
                        durationSeconds = parseDurationToSeconds(duration),
                        publishedText = publishedText,
                        thumbnailUrl = thumbnailUrl
                    )
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isOfficialChannel(channelId: String, channelName: String): Boolean {
        if (channelId.isNotBlank() && OFFICIAL_CHANNEL_IDS.containsKey(channelId)) return true
        return channelName.trim().lowercase() in OFFICIAL_CHANNEL_NAMES
    }

    internal data class YouTubeCandidate(
        val videoId: String,
        val title: String,
        val channelName: String,
        val channelId: String,
        val duration: String,
        val durationSeconds: Int,
        val publishedText: String?,
        val thumbnailUrl: String?
    )

    companion object {
        private const val MIN_HIGHLIGHT_DURATION_SECONDS = 90

        private val OFFICIAL_CHANNEL_IDS = mapOf(
            "UCkBY0aHJP9BwjZLDYxAQrKg" to "cricket.com.au",
            "UCz1D0n02LR3t-47fM4x7Auw" to "England & Wales Cricket Board",
            "UCi-5OZ2tYjsW2-8d-U_7Xog" to "England & Wales Cricket Board",
            "UCt2JXN_xL_G0nJ-o13K65Wg" to "ICC",
            "UC-560Q9082t83zU0h_k1gCg" to "ICC",
            "UC7ITT3ooYWDYY_ehIUbt6eg" to "Asian Cricket Council",
            "UC3s6K2kZ2w73a7_h08c4-hA" to "Pakistan Cricket",
            "UCO6gN-vI9k02L1Q2E5x8k2g" to "Sri Lanka Cricket",
            "UCr9aK3E9n7jW1q9F_6k_t8Q" to "Sri Lanka Cricket",
            "UC2t5bjwFrE2h_Q-2l9R6E2w" to "Windies Cricket",
            "UC0y6P-6W_R7m5J6z5c-q7Cg" to "BLACKCAPS",
            "UCuKowzPg2-A0FcmyPa3yWug" to "New Zealand Cricket",
            "UC8p4g7V_0y_sK2qG5S2tJ2Q" to "Cricket South Africa",
            "UCa-0l9X-V5T6n1Q8K-4qG3A" to "Bangladesh Cricket",
            "UCF7a7e0-i0u-Q4e4o5f8h1Q" to "Zimbabwe Cricket",
            "UCW5C8xS5V5Q4m4y8n7o-4XQ" to "Cricket Ireland",
            "UCd1M7xG7tH5uR6S6c3i8w2w" to "Prime Video Sport",
            "UCOQNJjhXwvAScuELTT_i7cQ" to "Sony LIV",
            "UC_WKb6N9iTGc77hxwXLDrbA" to "Sony Sports Network",
            "UCF10AG_t1AYW3mlmX7g1VJA" to "FanCode",
            "UCNU0Vv6U1xZS82cIQrgDeqw" to "Rabbitholebd Sports",
            "UCk4f1W6N2J7q4m3x8l9v5Zw" to "The Hundred",
            "UCh6g9C3S8V5Q0w3b2q9m1aA" to "Big Bash League",
            "UCJ0m4W8w5N0p6v2f4q7e3rA" to "SA20",
            "UC9r8Q4m3n2b1v0c7x6z5a4S" to "CPL T20"
        )

        private val OFFICIAL_CHANNEL_NAMES = setOf(
            "cricket.com.au",
            "england & wales cricket board",
            "icc",
            "asian cricket council",
            "pakistan cricket",
            "sri lanka cricket",
            "windies cricket",
            "blackcaps",
            "new zealand cricket",
            "nz cricket",
            "cricket south africa",
            "bangladesh cricket : the tigers",
            "bangladesh cricket",
            "zimbabwe cricket",
            "cricket ireland",
            "the hundred",
            "big bash league",
            "sa20",
            "cpl t20",
            "major league cricket",
            "pakistan super league",
            "sony liv",
            "sonyliv",
            "sony sports network",
            "fancode",
            "rabbitholebd sports",
            "prime video sport"
        )

        private val TEAM_ALIASES: Map<String, List<String>> = mapOf(
            "india" to listOf("ind", "india"),
            "india women" to listOf("ind w", "indw", "india women", "india w"),
            "india a" to listOf("ind a", "inda", "india a"),
            "india u19" to listOf("ind u19", "india u19", "india under 19"),
            "australia" to listOf("aus", "australia"),
            "australia women" to listOf("aus w", "ausw", "australia women", "australia w"),
            "australia a" to listOf("aus a", "ausa", "australia a"),
            "england" to listOf("eng", "england"),
            "england women" to listOf("eng w", "engw", "england women", "england w"),
            "south africa" to listOf("sa", "rsa", "south africa"),
            "south africa women" to listOf("sa w", "saw", "rsa w", "south africa women", "south africa w"),
            "new zealand" to listOf("nz", "new zealand"),
            "new zealand women" to listOf("nz w", "nzw", "new zealand women", "new zealand w"),
            "pakistan" to listOf("pak", "pakistan"),
            "pakistan a" to listOf("pak a", "paka", "pakistan a"),
            "pakistan women" to listOf("pak w", "pakw", "pakistan women", "pakistan w"),
            "sri lanka" to listOf("sl", "sri lanka"),
            "sri lanka a" to listOf("sl a", "sla", "sri lanka a"),
            "sri lanka women" to listOf("sl w", "slw", "sri lanka women", "sri lanka w"),
            "west indies" to listOf("wi", "windies", "west indies"),
            "west indies women" to listOf("wi w", "wiw", "west indies women", "west indies w"),
            "bangladesh" to listOf("ban", "bangladesh"),
            "bangladesh a" to listOf("ban a", "bana", "bangladesh a"),
            "bangladesh women" to listOf("ban w", "banw", "bangladesh women", "bangladesh w"),
            "afghanistan" to listOf("afg", "afghanistan"),
            "afghanistan a" to listOf("afg a", "afga", "afghanistan a"),
            "zimbabwe" to listOf("zim", "zimbabwe"),
            "zimbabwe women" to listOf("zim w", "zimw", "zimbabwe women", "zimbabwe w"),
            "ireland" to listOf("ire", "ireland"),
            "ireland women" to listOf("ire w", "irew", "ireland women", "ireland w"),
            "hong kong" to listOf("hk", "hkc", "hong kong", "hong kong china", "hk china"),
            "scotland" to listOf("sco", "scotland"),
            "netherlands" to listOf("ned", "netherlands"),
            "nepal" to listOf("nep", "nepal"),
            "usa" to listOf("usa", "united states"),
            "uae" to listOf("uae", "united arab emirates"),
            "namibia" to listOf("nam", "namibia"),
            "oman" to listOf("oma", "oman")
        )

        private val NON_HIGHLIGHT_KEYWORDS = listOf(
            "press conference",
            "post match presentation",
            "post-match presentation",
            "pre-match",
            "pre match",
            "preview",
            "net session",
            "practice session",
            "pitch report",
            "toss",
            "interview",
            "reaction",
            "dream11",
            "fantasy",
            "cricket 24",
            "cricket 22",
            "real cricket",
            "gameplay",
            "simulation",
            "live stream"
        )

        internal fun scoreHighlightMatch(summary: MatchSummary, candidate: YouTubeCandidate): Int? {
            val normTitle = normalizeMatchText(candidate.title)
            if (normTitle.isBlank()) return null
            if (NON_HIGHLIGHT_KEYWORDS.any { normTitle.contains(it) }) return null

            val matchContextText = "${summary.team1.name} ${summary.team1.shortName} ${summary.team2.name} ${summary.team2.shortName} ${summary.series} ${summary.matchDesc}"
            val matchQual = detectQualifiers(matchContextText)
            val videoQual = detectQualifiers(candidate.title)
            if (matchQual != videoQual) return null

            val team1Aliases = getTeamAliases(summary.team1.name, summary.team1.shortName)
            val team2Aliases = getTeamAliases(summary.team2.name, summary.team2.shortName)
            if (team1Aliases.none { hasWholePhrase(normTitle, it) }) return null
            if (team2Aliases.none { hasWholePhrase(normTitle, it) }) return null

            if (summary.startTime > 0L) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = summary.startTime
                }
                val matchYear = cal.get(Calendar.YEAR)
                val matchMonth = cal.get(Calendar.MONTH)
                val titleYears = Regex("\\b(20[1-3]\\d)\\b")
                    .findAll(normTitle)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .toList()
                if (titleYears.isNotEmpty()) {
                    val allowed = if (matchMonth <= 3) setOf(matchYear, matchYear - 1) else setOf(matchYear)
                    if (titleYears.none { it in allowed }) return null
                }

                val ageRange = parseRelativeAgeRangeDays(candidate.publishedText)
                if (ageRange != null) {
                    val matchAgeDays = ((System.currentTimeMillis() - summary.startTime) / 86_400_000.0).coerceAtLeast(0.0)
                    val maxAfterGrace = if (summary.format == MatchFormat.TEST) 10.0 else 6.0
                    if (ageRange.first > matchAgeDays + 5.0 || ageRange.second < matchAgeDays - maxAfterGrace) {
                        return null
                    }
                }
            }

            var score = 60
            val matchStage = extractStageToken(summary.matchDesc)
            val videoStage = extractStageToken(candidate.title)
            if (matchStage != null && videoStage != null) {
                if (matchStage != videoStage) return null
                score += 25
            }

            val formatToken = normalizeMatchText(summary.format.name)
            if (formatToken.isNotBlank() && formatToken != "other" && hasWholePhrase(normTitle, formatToken)) {
                score += 10
            }
            if (Regex("\\bhighlights?\\b").containsMatchIn(normTitle)) {
                score += 25
            }
            return score
        }

        private fun normalizeMatchText(text: String): String = text
            .lowercase()
            .replace("'", "")
            .replace("\u2019", "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

        private fun parseDurationToSeconds(duration: String?): Int {
            if (duration.isNullOrBlank()) return 0
            val parts = duration.trim().split(":").mapNotNull { it.toIntOrNull() }
            return when (parts.size) {
                2 -> parts[0] * 60 + parts[1]
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                else -> 0
            }
        }

        private fun parseRelativeAgeRangeDays(publishedText: String?): Pair<Double, Double>? {
            if (publishedText.isNullOrBlank()) return null
            val match = Regex("(\\d+)\\s*(second|sec|s|minute|min|m|hour|hr|h|day|d|week|wk|w|month|mo|year|yr|y)s?\\s*ago")
                .find(publishedText.lowercase()) ?: return null
            val value = match.groupValues[1].toDoubleOrNull() ?: return null
            return when (match.groupValues[2]) {
                "second", "sec", "s", "minute", "min", "m" -> 0.0 to 1.0
                "hour", "hr", "h" -> 0.0 to maxOf(1.0, value / 24.0 + 0.5)
                "day", "d" -> maxOf(0.0, value - 0.5) to (value + 1.5)
                "week", "wk", "w" -> maxOf(0.0, value * 7 - 2) to (value * 7 + 6)
                "month", "mo" -> maxOf(0.0, value * 28 - 5) to (value * 32 + 10)
                "year", "yr", "y" -> maxOf(0.0, value * 365 - 30) to (value * 366 + 60)
                else -> null
            }
        }

        private fun getTeamAliases(name: String, shortName: String): List<String> {
            val normName = normalizeMatchText(name)
            val normShort = normalizeMatchText(shortName)
            val aliases = linkedSetOf<String>()
            if (normName.isNotBlank()) aliases += normName
            if (normShort.isNotBlank()) aliases += normShort

            for ((canonical, list) in TEAM_ALIASES) {
                val normalized = list.map(::normalizeMatchText)
                if (normName in normalized || normShort in normalized || canonical == normName) {
                    aliases.addAll(normalized.filter { it.isNotBlank() })
                }
            }
            return aliases.sortedByDescending { it.length }
        }

        private fun hasWholePhrase(haystack: String, phrase: String): Boolean {
            if (phrase.isBlank()) return false
            val escaped = Regex.escape(phrase)
            return Regex("(?:^|\\s)$escaped(?:\\s|$)").containsMatchIn(haystack)
        }

        private data class Qualifiers(val isWomen: Boolean, val isATeam: Boolean, val isU19: Boolean)

        private fun detectQualifiers(text: String): Qualifiers {
            val norm = normalizeMatchText(text)
            val isWomen = Regex("\\bwomen\\b|\\bwomens\\b|\\bwpl\\b|\\b[a-z]{2,4} w\\b|\\b[a-z]{2,4}w\\b").containsMatchIn(norm)
            val isATeam = Regex("\\b[a-z]{2,4} a\\b").containsMatchIn(norm)
            val isU19 = Regex("\\bu19\\b|\\bunder 19\\b").containsMatchIn(norm)
            return Qualifiers(isWomen, isATeam, isU19)
        }

        private fun extractStageToken(text: String): String? {
            val norm = normalizeMatchText(text)
            Regex("\\b(\\d+)(?:st|nd|rd|th)\\s+match\\b").find(norm)?.let { return "m:${it.groupValues[1]}" }
            Regex("\\b(?:match|m)\\s*(\\d+)\\b").find(norm)?.let { return "m:${it.groupValues[1]}" }
            Regex("\\b(\\d+)(?:st|nd|rd|th)\\b").find(norm)?.let { return "ord:${it.groupValues[1]}" }
            if (Regex("\\bqualifier 1\\b|\\bq1\\b").containsMatchIn(norm)) return "stage:q1"
            if (Regex("\\bqualifier 2\\b|\\bq2\\b").containsMatchIn(norm)) return "stage:q2"
            if (Regex("\\beliminator\\b").containsMatchIn(norm)) return "stage:eliminator"
            if (Regex("\\bsemi final\\b|\\bsf\\d?\\b").containsMatchIn(norm)) return "stage:sf"
            if (Regex("\\bfinal\\b").containsMatchIn(norm)) return "stage:final"
            return null
        }

        private fun jsonEscape(value: String): String = buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
