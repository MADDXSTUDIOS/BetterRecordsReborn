package com.hemogoblins.betterrecords.component

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamEncoder

/**
 * The song etched onto a record. Replaces the 1.20.1 `IMusicHolder` capability.
 *
 * Data components must be immutable, so "editing" a record means setting a new [MusicData] on the stack.
 */
data class MusicData(
    val songName: String,
    val url: String,
    val checksum: String,
) {
    companion object {
        val EMPTY = MusicData("", "", "")

        val CODEC: Codec<MusicData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("songName", "").forGetter { it.songName },
                Codec.STRING.optionalFieldOf("url", "").forGetter { it.url },
                Codec.STRING.optionalFieldOf("checksum", "").forGetter { it.checksum },
            ).apply(instance) { songName, url, checksum -> MusicData(songName, url, checksum) }
        }

        val STREAM_CODEC: StreamCodec<ByteBuf, MusicData> = StreamCodec.of(
            StreamEncoder { buf, data ->
                ByteBufCodecs.STRING_UTF8.encode(buf, data.songName)
                ByteBufCodecs.STRING_UTF8.encode(buf, data.url)
                ByteBufCodecs.STRING_UTF8.encode(buf, data.checksum)
            },
            StreamDecoder { buf ->
                MusicData(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                )
            },
        )
    }
}
