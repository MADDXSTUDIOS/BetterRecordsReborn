package com.hemogoblins.betterrecords.item

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import com.hemogoblins.betterrecords.block.entity.SpeakerBlockEntity
import com.hemogoblins.betterrecords.component.ModDataComponents
import com.hemogoblins.betterrecords.wire.WireConnections
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

/**
 * Speaker wire. Right-click a record player and then a speaker (either order) to wire them together.
 * One wire is used per connection. Sneak + right-click the air to cancel a half-placed wire.
 *
 * The first end is stored on the stack (data component), so it survives relogging and works in multiplayer,
 * unlike 1.12.2 which kept it in a client-side static.
 */
class WireItem(properties: Properties) : Item(properties) {

    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        val player = context.player
        val stack = context.itemInHand

        val be = level.getBlockEntity(pos)
        if (be !is RecordPlayerBlockEntity && be !is SpeakerBlockEntity) return InteractionResult.PASS
        if (level !is ServerLevel) return InteractionResult.SUCCESS

        val start = stack.get(ModDataComponents.WIRE_START.get())

        // First end of the wire
        if (start == null || start == pos) {
            stack.set(ModDataComponents.WIRE_START.get(), pos.immutable())
            player?.message("message.${BetterRecords.ID}.wire.started", ChatFormatting.GRAY)
            return InteractionResult.CONSUME
        }

        // Second end: work out which end is the record player and which is the speaker.
        stack.remove(ModDataComponents.WIRE_START.get())
        val startBe = level.getBlockEntity(start)
        val (playerPos, speakerPos) = when {
            startBe is RecordPlayerBlockEntity && be is SpeakerBlockEntity -> start to pos
            startBe is SpeakerBlockEntity && be is RecordPlayerBlockEntity -> pos to start
            else -> {
                player?.message("message.${BetterRecords.ID}.wire.invalid", ChatFormatting.RED)
                return InteractionResult.CONSUME
            }
        }

        when (WireConnections.connect(level, playerPos, speakerPos)) {
            WireConnections.Result.Connected -> {
                if (player?.abilities?.instabuild != true) stack.shrink(1)
                level.playSound(null, pos, SoundEvents.TRIPWIRE_ATTACH, SoundSource.BLOCKS, 1f, 1f)
                player?.message("message.${BetterRecords.ID}.wire.connected", ChatFormatting.GREEN)
            }
            WireConnections.Result.AlreadyConnected ->
                player?.message("message.${BetterRecords.ID}.wire.already_connected", ChatFormatting.YELLOW)
            WireConnections.Result.TooFar ->
                player?.message("message.${BetterRecords.ID}.wire.too_far", ChatFormatting.RED)
            WireConnections.Result.NotAPlayerAndSpeaker ->
                player?.message("message.${BetterRecords.ID}.wire.invalid", ChatFormatting.RED)
        }
        return InteractionResult.CONSUME
    }

    /** Sneak + right-click the air cancels a half-placed wire. */
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isShiftKeyDown && stack.has(ModDataComponents.WIRE_START.get())) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.WIRE_START.get())
                player.message("message.${BetterRecords.ID}.wire.cancelled", ChatFormatting.GRAY)
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        return InteractionResultHolder.pass(stack)
    }

    override fun isFoil(stack: ItemStack): Boolean = stack.has(ModDataComponents.WIRE_START.get())

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        val start: BlockPos? = stack.get(ModDataComponents.WIRE_START.get())
        if (start != null) {
            tooltip.add(
                Component.translatable("tooltip.${BetterRecords.ID}.wire.attached", start.x, start.y, start.z)
                    .withStyle(ChatFormatting.GRAY)
            )
        } else {
            tooltip.add(Component.translatable("tooltip.${BetterRecords.ID}.wire.usage").withStyle(ChatFormatting.GRAY))
        }
        super.appendHoverText(stack, context, tooltip, flag)
    }
}

/** Shows a translated message above the hotbar. */
internal fun Player.message(key: String, color: ChatFormatting) {
    displayClientMessage(Component.translatable(key).withStyle(color), true)
}
