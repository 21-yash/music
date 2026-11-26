package com.synq.features.sports.highlights

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.synq.features.sports.domain.model.HighlightPlaybackType
import com.synq.features.sports.domain.model.MatchHighlight
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

data class VideoQualityOption(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String? = null,
    val isHls: Boolean = false
)

data class ResolvedHighlightPlayback(
    val activeQuality: VideoQualityOption,
    val qualityOptions: List<VideoQualityOption>,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Resolves a normalized [MatchHighlight] into playable Media3 stream URL(s) and selectable quality tiers.
 *
 * - For `HLS` (BCCI / IPLT20 Mux streams): exposes Auto + 1080p/720p/480p/270p caps enforced via
 *   ExoPlayer TrackSelectionParameters; Auto dynamically adapts to bandwidth.
 * - For `YOUTUBE`: uses NewPipeExtractor on-device; prefers YouTube's adaptive HLS when available,
 *   otherwise chooses an initial Auto resolution matching ExoPlayer's measured bandwidth estimate
 *   so poor networks automatically start on a lighter stream.
 */
@Singleton
class HighlightStreamResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @OptIn(UnstableApi::class)
    suspend fun resolve(highlight: MatchHighlight): Result<ResolvedHighlightPlayback> =
        withContext(Dispatchers.IO) {
            when (highlight.playbackType) {
                HighlightPlaybackType.HLS -> {
                    if (highlight.streamTarget.isBlank()) {
                        Result.failure(IllegalStateException("Missing stream URL"))
                    } else {
                        val auto = VideoQualityOption(
                            label = "Auto",
                            height = 0,
                            videoUrl = highlight.streamTarget,
                            isHls = true
                        )
                        val tiers = listOf(1080, 720, 480, 270).map { h ->
                            VideoQualityOption(
                                label = "${h}p",
                                height = h,
                                videoUrl = highlight.streamTarget,
                                isHls = true
                            )
                        }
                        Result.success(
                            ResolvedHighlightPlayback(
                                activeQuality = auto,
                                qualityOptions = listOf(auto) + tiers,
                                headers = highlight.playbackHeaders
                            )
                        )
                    }
                }
                HighlightPlaybackType.YOUTUBE -> resolveYouTubeVideo(highlight.streamTarget)
            }
        }

    @OptIn(UnstableApi::class)
    private fun resolveYouTubeVideo(videoId: String): Result<ResolvedHighlightPlayback> {
        return try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val extractor = NewPipe.getService(0).getStreamExtractor(url) as YoutubeStreamExtractor
            extractor.fetchPage()

            val options = mutableListOf<VideoQualityOption>()
            val hlsUrl = extractor.hlsUrl
            if (!hlsUrl.isNullOrBlank()) {
                options += VideoQualityOption(
                    label = "Auto",
                    height = 0,
                    videoUrl = hlsUrl,
                    isHls = true
                )
            }

            val bestAudio = extractor.audioStreams
                .filter { !it.content.isNullOrBlank() }
                .maxByOrNull { it.averageBitrate }

            val muxedByHeight = extractor.videoStreams
                .filter { !it.content.isNullOrBlank() }
                .filter { parseHeight(it) in 144..1080 }
                .groupBy { parseHeight(it) }
                .mapValues { (_, list) -> pickPreferredStream(list) }

            val videoOnlyByHeight = if (bestAudio != null) {
                extractor.videoOnlyStreams
                    .filter { !it.content.isNullOrBlank() }
                    .filter { parseHeight(it) in 144..1080 }
                    .groupBy { parseHeight(it) }
                    .mapValues { (_, list) -> pickPreferredStream(list) }
            } else emptyMap()

            val allHeights = (muxedByHeight.keys + videoOnlyByHeight.keys)
                .distinct()
                .sortedDescending()

            for (height in allHeights) {
                val muxed = muxedByHeight[height]
                val adaptive = videoOnlyByHeight[height]
                val chosen = when {
                    height >= 720 && adaptive != null && bestAudio != null -> {
                        VideoQualityOption(
                            label = "${height}p",
                            height = height,
                            videoUrl = adaptive.content,
                            audioUrl = bestAudio.content,
                            isHls = false
                        )
                    }
                    muxed != null -> {
                        VideoQualityOption(
                            label = "${height}p",
                            height = height,
                            videoUrl = muxed.content,
                            audioUrl = null,
                            isHls = false
                        )
                    }
                    adaptive != null && bestAudio != null -> {
                        VideoQualityOption(
                            label = "${height}p",
                            height = height,
                            videoUrl = adaptive.content,
                            audioUrl = bestAudio.content,
                            isHls = false
                        )
                    }
                    else -> null
                }
                if (chosen != null) options += chosen
            }

            val hasAutoHls = options.any { it.height == 0 && it.isHls }
            val finalOptions = if (!hasAutoHls) {
                val targetCap = pickAdaptiveTargetHeight()
                val concreteChoices = options.filter { it.height > 0 }
                val chosenForAuto = concreteChoices
                    .filter { it.height <= targetCap }
                    .maxByOrNull { it.height }
                    ?: concreteChoices.minByOrNull { it.height }
                if (chosenForAuto != null) {
                    listOf(chosenForAuto.copy(label = "Auto", height = 0)) + concreteChoices
                } else {
                    options
                }
            } else {
                options
            }

            val active = finalOptions.firstOrNull()
                ?: return Result.failure(IllegalStateException("No playable stream found for this highlight"))

            Result.success(
                ResolvedHighlightPlayback(
                    activeQuality = active,
                    qualityOptions = finalOptions,
                    headers = emptyMap()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @OptIn(UnstableApi::class)
    private fun pickAdaptiveTargetHeight(): Int {
        val bps = runCatching {
            DefaultBandwidthMeter.getSingletonInstance(context).bitrateEstimate
        }.getOrDefault(4_000_000L)
        return when {
            bps < 900_000L -> 360
            bps < 2_200_000L -> 480
            bps < 5_000_000L -> 720
            else -> 1080
        }
    }

    private fun pickPreferredStream(streams: List<VideoStream>): VideoStream? {
        return streams
            .sortedWith(
                compareByDescending<VideoStream> { it.format == MediaFormat.MPEG_4 }
                    .thenByDescending { it.fps }
            )
            .firstOrNull()
    }

    @Suppress("DEPRECATION")
    private fun parseHeight(stream: VideoStream): Int {
        val raw = stream.getResolution() ?: return 0
        val digits = raw.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}
