package com.hemogoblins.betterrecords.network.clientbound

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.client.sound.RecordSoundManager
import com.hemogoblins.betterrecords.component.ModDataComponents
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.JukeboxSong
import net.neoforged.neoforge.network.handling.IPayloadContext

/**
 * This packet is sent to the client when a record is inserted into a player
 */
class PlayRecordPacket(
    val pos: BlockPos,
    val source: RecordSource,
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<PlayRecordPacket> = TYPE

    fun encode(buf: RegistryFriendlyByteBuf) {
        buf.writeBlockPos(pos)
        source.encode(buf)
    }

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayRecordPacket>(BetterRecords.id("play_record"))

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PlayRecordPacket> =
            StreamCodec.ofMember(PlayRecordPacket::encode, ::decode)

        fun handle(msg: PlayRecordPacket, context: IPayloadContext) {
            RecordSoundManager.play(msg.pos, msg.source)
        }

        fun decode(buf: RegistryFriendlyByteBuf): PlayRecordPacket {
            return PlayRecordPacket(
                buf.readBlockPos(),
                RecordSource.decode(buf),
            )
        }
    }

    sealed class RecordSource {

        abstract fun encode(buf: RegistryFriendlyByteBuf)

        /** A BetterRecords record  */
        data class File(val name: String, val url: String, val checksum: String) : RecordSource() {
            override fun encode(buf: RegistryFriendlyByteBuf) {
                buf.writeByte(Type.FILE.ordinal)
                buf.writeUtf(name)
                buf.writeUtf(url)
                buf.writeUtf(checksum)
            }
        }

        /**
         * A vanilla (or modded) music disc.
         *
         * Since 1.21 discs are data driven jukebox songs, so the song's display name
         * is sent along with the sound rather than looked up from the item on the client.
         */
        data class Vanilla(val sound: ResourceLocation, val name: Component) : RecordSource() {
            override fun encode(buf: RegistryFriendlyByteBuf) {
                buf.writeByte(Type.VANILLA.ordinal)
                buf.writeResourceLocation(sound)
                ComponentSerialization.STREAM_CODEC.encode(buf, name)
            }
        }

        private enum class Type { FILE, VANILLA }

        companion object {
            fun decode(buf: RegistryFriendlyByteBuf): RecordSource =
                when (Type.entries[buf.readByte().toInt()]) {
                    Type.FILE -> File(buf.readUtf(), buf.readUtf(), buf.readUtf())
                    Type.VANILLA -> Vanilla(buf.readResourceLocation(), ComponentSerialization.STREAM_CODEC.decode(buf))
                }

            fun of(stack: ItemStack, registries: HolderLookup.Provider): RecordSource? {
                stack.get(ModDataComponents.MUSIC.get())?.let { music ->
                    return if (music.checksum.isNotEmpty()) File(music.songName, music.url, music.checksum) else null
                }

                return JukeboxSong.fromStack(registries, stack)
                    .map { song -> Vanilla(song.value().soundEvent().value().location, song.value().description()) }
                    .orElse(null)
            }
        }
    }
}
