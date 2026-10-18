package com.hemogoblins.betterrecords.wire

import com.hemogoblins.betterrecords.BRConfig
import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import com.hemogoblins.betterrecords.block.entity.SpeakerBlockEntity
import com.hemogoblins.betterrecords.item.ModItems
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack

/**
 * Speaker wire logic, kept in one place so blocks and items share it.
 *
 * Ported from the 1.12.2 ConnectionHelper, simplified: a wire always runs between one record player
 * and one speaker. The record player holds the list of its speakers, each speaker remembers its player.
 * Everything here runs on the server, block entity updates sync the result to clients.
 */
object WireConnections {

    sealed interface Result {
        data object Connected : Result
        data object AlreadyConnected : Result
        data object TooFar : Result
        data object NotAPlayerAndSpeaker : Result
    }

    /** Wires [speakerPos] to the record player at [playerPos]. A speaker can only be wired to one player. */
    fun connect(level: ServerLevel, playerPos: BlockPos, speakerPos: BlockPos): Result {
        val player = level.getBlockEntity(playerPos) as? RecordPlayerBlockEntity ?: return Result.NotAPlayerAndSpeaker
        val speaker = level.getBlockEntity(speakerPos) as? SpeakerBlockEntity ?: return Result.NotAPlayerAndSpeaker

        if (!playerPos.closerThan(speakerPos, BRConfig.Server.wireLength.get().toDouble() + 0.5)) {
            return Result.TooFar
        }
        if (speaker.home == playerPos && speakerPos in player.speakers) {
            return Result.AlreadyConnected
        }

        // Re-wiring a speaker to a new player unhooks it from the old one first (and gives that wire back).
        speaker.home?.let { oldHome ->
            if (oldHome != playerPos) disconnectSpeaker(level, speaker, dropWireAt = speakerPos)
        }

        player.addSpeaker(speakerPos)
        speaker.home = playerPos
        return Result.Connected
    }

    /** Unwires a speaker from its record player, dropping the wire at [dropWireAt] (null = don't drop). */
    fun disconnectSpeaker(level: ServerLevel, speaker: SpeakerBlockEntity, dropWireAt: BlockPos?) {
        val home = speaker.home ?: return
        (level.getBlockEntity(home) as? RecordPlayerBlockEntity)?.removeSpeaker(speaker.blockPos)
        if (!speaker.isRemoved) speaker.home = null
        dropWireAt?.let { dropWire(level, it, 1) }
    }

    /** Unwires every speaker from a record player, dropping one wire per speaker at [dropWireAt]. */
    fun disconnectAll(level: ServerLevel, player: RecordPlayerBlockEntity, dropWireAt: BlockPos?) {
        val speakers = player.speakers.toList()
        speakers.forEach { pos ->
            (level.getBlockEntity(pos) as? SpeakerBlockEntity)?.takeIf { it.home == player.blockPos }?.home = null
        }
        player.clearSpeakers()
        if (dropWireAt != null && speakers.isNotEmpty()) dropWire(level, dropWireAt, speakers.size)
    }

    private fun dropWire(level: ServerLevel, pos: BlockPos, count: Int) {
        Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, ItemStack(ModItems.WIRE.get(), count))
    }
}
