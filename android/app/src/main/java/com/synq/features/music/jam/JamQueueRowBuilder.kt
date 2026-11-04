package com.synq.features.music.jam

data class JamQueueRow(
    val item: JamQueueItem,
    val isActive: Boolean
)

object JamQueueRowBuilder {

    /**
     * Projects nowPlaying and queued items into a unified list of rows.
     * If nowPlayingItem is present, it is prepended as the active first row.
     * All following items have isActive = false.
     * Preserves the ordering of the input queue.
     */
    fun buildJamQueueRows(
        nowPlayingItem: JamQueueItem?,
        queue: List<JamQueueItem>
    ): List<JamQueueRow> {
        val rows = ArrayList<JamQueueRow>(queue.size + (if (nowPlayingItem != null) 1 else 0))
        if (nowPlayingItem != null) {
            rows.add(JamQueueRow(item = nowPlayingItem, isActive = true))
        }
        for (item in queue) {
            rows.add(JamQueueRow(item = item, isActive = false))
        }
        return rows
    }

    /**
     * Formats the queue bottom sheet title to reflect the total song count.
     */
    fun formatJamQueueTitle(totalCount: Int): String {
        return "Jam Queue ($totalCount songs)"
    }
}
