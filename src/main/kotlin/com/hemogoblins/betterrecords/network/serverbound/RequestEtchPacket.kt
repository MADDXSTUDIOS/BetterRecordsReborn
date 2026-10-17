package com.hemogoblins.betterrecords.network.serverbound

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.block.entity.RecordEtcherBlockEntity
import com.hemogoblins.betterrecords.component.ModDataComponents
import com.hemogoblins.betterrecords.component.MusicData
import com.hemogoblins.betterrecords.item.RecordItem
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * This packet is sent to the server when the player writes information to a record
 */
class RequestEtchPacket(
    val pos: BlockPos,
    val name: String,
    val url: String,
    val checksum: String,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<RequestEtchPacket> = TYPE

    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        buf.writeUtf(name)
        buf.writeUtf(url)
        buf.writeUtf(checksum)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<RequestEtchPacket>(BetterRecords.id("request_etch"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RequestEtchPacket> =
            StreamCodec.ofMember(RequestEtchPacket::encode, ::decode)

        fun handle(msg: RequestEtchPacket, context: IPayloadContext) {
            val player = context.player() as? ServerPlayer ?: return
            val level = player.serverLevel()
            if (!level.isLoaded(msg.pos)) return

            val blockEntity = level.getBlockEntity(msg.pos)

            if (blockEntity is RecordEtcherBlockEntity) {
                val stack = blockEntity.getSlottedItem()

                // Only Better Records records can be etched (vanilla discs can be played, not etched)
                if (stack.item is RecordItem) {
                    BetterRecords.logger.info("Etching record at ${msg.pos}") // TODO: More information

                    // Components are immutable, so write a fresh copy back into the slot.
                    // Setting the slot marks the block entity dirty and notifies clients.
                    blockEntity.setSlottedItem(stack.copy().apply {
                        set(ModDataComponents.MUSIC.get(), MusicData(msg.name, msg.url, msg.checksum))
                    })
                } else {
                    blockEntity.triggerUpdate() // Notifies clients
                }
            }
        }

        fun decode(buf: RegistryFriendlyByteBuf): RequestEtchPacket {
            return RequestEtchPacket(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf()
            )
        }
    }
}
