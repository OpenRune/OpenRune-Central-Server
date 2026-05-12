package dev.or2.central.worldserver.net

import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class WorldServerConnectionLimits(
    private val maxPerIp: Int,
    private val maxTotal: Int,
) {
    private val perIp = ConcurrentHashMap<String, AtomicInteger>()
    private val total = AtomicInteger()

    fun tryAcquire(remote: InetSocketAddress?): Boolean {
        val key = remote?.address?.hostAddress ?: "unknown"
        if (maxTotal > 0) {
            if (total.incrementAndGet() > maxTotal) {
                total.decrementAndGet()
                return false
            }
        }
        if (maxPerIp > 0) {
            val c = perIp.computeIfAbsent(key) { AtomicInteger(0) }
            while (true) {
                val cur = c.get()
                if (cur >= maxPerIp) {
                    if (maxTotal > 0) {
                        total.decrementAndGet()
                    }
                    return false
                }
                if (c.compareAndSet(cur, cur + 1)) {
                    return true
                }
            }
        }
        return true
    }

    fun release(remote: InetSocketAddress?) {
        val key = remote?.address?.hostAddress ?: "unknown"
        if (maxPerIp > 0) {
            perIp.computeIfPresent(key) { _, v ->
                val n = v.decrementAndGet()
                if (n <= 0) null else v
            }
        }
        if (maxTotal > 0) {
            total.decrementAndGet()
        }
    }
}
