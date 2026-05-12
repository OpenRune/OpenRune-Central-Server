package dev.or2.central.util

object RebootLoginBlockPolicy {
    fun leadBlockSeconds(noticeSeconds: Long): Long {
        val t = noticeSeconds.coerceAtLeast(60L)
        return when {
            t <= 10 * 60L -> 150L
            t >= 60 * 60L -> 300L
            else -> (150L + (t - 600L) * 150L / (3600L - 600L)).coerceIn(150L, 300L)
        }
    }
}