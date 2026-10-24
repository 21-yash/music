package com.synq.features.music.util

import com.synq.features.music.domain.model.Song

object SongDeduplicator {

    private val STRIP_PATTERNS = listOf(
        Regex("""\((feat|ft|with|remix|cover|acoustic|lofi|slowed|reverb|official|video|audio|from|soundtrack|ost)[^)]*\)""", RegexOption.IGNORE_CASE),
        Regex("""\[(feat|ft|with|remix|cover|acoustic|lofi|slowed|reverb|official|video|audio|from|soundtrack|ost)[^]]*\]""", RegexOption.IGNORE_CASE),
        Regex("""-(.*(remix|cover|acoustic|lofi|slowed|reverb|soundtrack|ost).*)""", RegexOption.IGNORE_CASE),
        Regex("""\b(remix|cover|acoustic|lofi|slowed|reverb|official video|official audio|lyric video|music video)\b""", RegexOption.IGNORE_CASE)
    )

    fun normalizeTitle(title: String): String {
        var normalized = title
        for (pattern in STRIP_PATTERNS) {
            normalized = normalized.replace(pattern, "")
        }
        return normalized.lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun normalizeArtist(artist: String): String {
        return artist.lowercase()
            .split(',', '&', '/')
            .firstOrNull()
            ?.replace(Regex("""[^a-z0-9\s]"""), "")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim() ?: ""
    }

    fun areSongsEquivalent(a: Song, b: Song): Boolean {
        if (a.id == b.id) return true
        val titleA = normalizeTitle(a.title)
        val titleB = normalizeTitle(b.title)
        if (titleA.isNotEmpty() && titleA == titleB) {
            val artistA = normalizeArtist(a.primaryArtistName)
            val artistB = normalizeArtist(b.primaryArtistName)
            if (artistA.isEmpty() || artistB.isEmpty() || artistA == artistB) {
                return true
            }
        }
        return false
    }

    fun filterDuplicateSongs(
        incoming: List<Song>,
        existing: Collection<Song>,
        sessionHistoryIds: Set<String>
    ): List<Song> {
        val existingNormalizedTitles = existing.map { normalizeTitle(it.title) }.toMutableSet()
        val existingIds = existing.map { it.id }.toMutableSet()
        val result = mutableListOf<Song>()

        for (candidate in incoming) {
            if (existingIds.contains(candidate.id) || sessionHistoryIds.contains(candidate.id)) {
                continue
            }
            val normTitle = normalizeTitle(candidate.title)
            if (normTitle.isNotEmpty() && existingNormalizedTitles.contains(normTitle)) {
                continue
            }
            // Candidate is unique
            result.add(candidate)
            existingIds.add(candidate.id)
            if (normTitle.isNotEmpty()) {
                existingNormalizedTitles.add(normTitle)
            }
        }
        return result
    }
}
