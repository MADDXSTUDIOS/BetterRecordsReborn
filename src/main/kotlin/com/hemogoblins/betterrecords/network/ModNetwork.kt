package com.hemogoblins.betterrecords.network

import com.hemogoblins.betterrecords.network.clientbound.PlayRecordPacket
import com.hemogoblins.betterrecords.network.clientbound.StopRecordPacket
import com.hemogoblins.betterrecords.network.serverbound.RequestEtchPacket
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

/**
 * Packet registration. On NeoForge 1.20.5+ packets are [net.minecraft.network.protocol.common.custom.CustomPacketPayload]s
 * registered through [RegisterPayloadHandlersEvent], and sent with [net.neoforged.neoforge.network.PacketDistributor].
 *
 * Handlers run on the main thread by default, so they no longer need to enqueue their work.
 */
object ModNetwork {

    private const val PROTOCOL_VERSION = "1"

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        event.registrar(PROTOCOL_VERSION)
            // Clientbound
            .playToClient(PlayRecordPacket.TYPE, PlayRecordPacket.STREAM_CODEC, PlayRecordPacket::handle)
            .playToClient(StopRecordPacket.TYPE, StopRecordPacket.STREAM_CODEC, StopRecordPacket::handle)
            // Serverbound
            .playToServer(RequestEtchPacket.TYPE, RequestEtchPacket.STREAM_CODEC, RequestEtchPacket::handle)
    }

    fun register(eventBus: IEventBus) {
        eventBus.addListener(::registerPayloads)
    }
}
