package com.hemogoblins.betterrecords.client.sound

import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundSource

/**
 * Works out where a record's sound comes from.
 *
 * With no speakers wired up the record player plays the song itself. Once speakers are connected the
 * player goes quiet and every speaker becomes its own positional (3D) sound source, so you hear the
 * music coming from the speakers and it gets louder as you walk towards one.
 * Each source fades out linearly to silence at the player's [RecordPlayerBlockEntity.songRadius].
 */
object RecordEmitters {

    /** Positions the record at [recordPlayerPos] should currently be heard from. */
    @JvmStatic
    fun positionsFor(recordPlayerPos: BlockPos): List<BlockPos> {
        val level = Minecraft.getInstance().level ?: return listOf(recordPlayerPos)
        val player = level.getBlockEntity(recordPlayerPos) as? RecordPlayerBlockEntity ?: return listOf(recordPlayerPos)
        val speakers = player.connectedSpeakers().map { it.first }
        return speakers.ifEmpty { listOf(recordPlayerPos) }
    }

    /** Distance (in blocks) at which each source fades to silence. */
    @JvmStatic
    fun radiusFor(recordPlayerPos: BlockPos): Float {
        val level = Minecraft.getInstance().level ?: return RecordPlayerBlockEntity.BASE_RADIUS
        val player = level.getBlockEntity(recordPlayerPos) as? RecordPlayerBlockEntity ?: return RecordPlayerBlockEntity.BASE_RADIUS
        return player.songRadius
    }

    /**
     * Source volume before distance falloff. The master slider is applied by the OpenAL listener,
     * so this only needs the "Jukebox/Note Blocks" slider.
     */
    @JvmStatic
    fun categoryVolume(): Float = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.RECORDS)
}
