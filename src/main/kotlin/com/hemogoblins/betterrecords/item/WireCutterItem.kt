package com.hemogoblins.betterrecords.item

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import com.hemogoblins.betterrecords.block.entity.SpeakerBlockEntity
import com.hemogoblins.betterrecords.wire.WireConnections
import net.minecraft.ChatFormatting
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext

/**
 * Wire cutters. Right-click a speaker to unwire it, or a record player to unwire all of its speakers.
 * The wire drops so it can be reused. (1.12.2 only allowed cutting speakers.)
 */
class WireCutterItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val be = level.getBlockEntity(pos)

        val hasWires = when (be) {
            is SpeakerBlockEntity -> be.home != null
            is RecordPlayerBlockEntity -> be.speakers.isNotEmpty()
            else -> return InteractionResult.PASS
        }
        if (!hasWires) return InteractionResult.PASS
        if (level !is ServerLevel) return InteractionResult.SUCCESS

        when (be) {
            is SpeakerBlockEntity -> WireConnections.disconnectSpeaker(level, be, dropWireAt = pos)
            is RecordPlayerBlockEntity -> WireConnections.disconnectAll(level, be, dropWireAt = pos)
        }

        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 1f, 1f)
        context.player?.let { player ->
            context.itemInHand.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.hand))
            player.message("message.${BetterRecords.ID}.wire.cut", ChatFormatting.GRAY)
        }
        return InteractionResult.CONSUME
    }
}
