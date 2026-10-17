package com.hemogoblins.betterrecords.network.clientbound

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.client.sound.RecordSoundManager
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * This packet is sent to the client when a record is removed from a player
 */
class StopRecordPacket(
    val pos: BlockPos,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<StopRecordPacket> = TYPE

    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<StopRecordPacket>(BetterRecords.id("stop_record"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, StopRecordPacket> =
            StreamCodec.ofMember(StopRecordPacket::encode, ::decode)

        fun handle(msg: StopRecordPacket, context: IPayloadContext) {
            RecordSoundManager.stop(msg.pos)
        }

        fun decode(buf: RegistryFriendlyByteBuf): StopRecordPacket {
            return StopRecordPacket(
                buf.readBlockPos(),
            )
        }
    }
}
