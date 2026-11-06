package com.synq.features.music.playback

object WarmRange {
    sealed class RangePlan {
        data class Warm(val startByte: Long, val endByte: Long) : RangePlan()
        object AlreadyWarmed : RangePlan()
    }

    const val DEFAULT_TARGET_BYTES = 524_288L // 512 KB

    fun plan(
        totalLengthKnown: Long?,
        cachedBytes: Long,
        targetBytes: Long = DEFAULT_TARGET_BYTES
    ): RangePlan {
        if (cachedBytes >= targetBytes) {
            return RangePlan.AlreadyWarmed
        }
        if (totalLengthKnown != null && totalLengthKnown > 0L) {
            if (cachedBytes >= totalLengthKnown) {
                return RangePlan.AlreadyWarmed
            }
            val target = minOf(targetBytes, totalLengthKnown)
            if (cachedBytes >= target) {
                return RangePlan.AlreadyWarmed
            }
            return RangePlan.Warm(cachedBytes, target - 1)
        }
        return RangePlan.Warm(cachedBytes, targetBytes - 1)
    }
}
