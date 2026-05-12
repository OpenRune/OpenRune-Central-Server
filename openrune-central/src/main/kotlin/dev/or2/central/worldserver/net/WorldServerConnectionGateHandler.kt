package dev.or2.central.worldserver.net

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.net.InetSocketAddress

internal class WorldServerConnectionGateHandler(
    private val limits: WorldServerConnectionLimits,
) : ChannelInboundHandlerAdapter() {
    private var acquired = false
    private var remote: InetSocketAddress? = null

    override fun channelActive(ctx: ChannelHandlerContext) {
        remote = ctx.channel().remoteAddress() as? InetSocketAddress
        if (!limits.tryAcquire(remote)) {
            ctx.close()
            return
        }
        acquired = true
        super.channelActive(ctx)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (acquired) {
            limits.release(remote)
            acquired = false
        }
        super.channelInactive(ctx)
    }
}
