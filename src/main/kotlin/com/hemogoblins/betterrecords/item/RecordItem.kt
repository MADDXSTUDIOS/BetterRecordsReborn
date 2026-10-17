package com.hemogoblins.betterrecords.item

import com.hemogoblins.betterrecords.component.ModDataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag

class RecordItem(properties: Properties) : Item(properties) {

    companion object {
        const val DEFAULT_COLOR = 0xFFFFFF

        /** RGB colour of the record's label, white if it has never been coloured. */
        fun getColor(stack: ItemStack): Int = stack.getOrDefault(ModDataComponents.COLOR.get(), DEFAULT_COLOR)
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.literal(getColor(stack).toString()))

        stack.get(ModDataComponents.MUSIC.get())?.let {
            tooltip.add(Component.literal(it.songName))
            tooltip.add(Component.literal(it.url))
            tooltip.add(Component.literal(it.checksum))
        }

        super.appendHoverText(stack, context, tooltip, flag)
    }
}
