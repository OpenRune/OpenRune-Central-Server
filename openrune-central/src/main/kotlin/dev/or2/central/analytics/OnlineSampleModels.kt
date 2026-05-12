package dev.or2.central.analytics

data class OnlineHourlyBucket(
    val bucketStartUtcMillis: Long,
    val worldId: Int,
    val peakOnline: Int,
    val avgOnline: Double,
)

data class OnlineDailyBucket(
    val dayUtc: String,
    val worldId: Int,
    val peakOnline: Int,
    val avgOnline: Double,
    val sampleCount: Long,
)
