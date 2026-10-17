package com.hemogoblins.betterrecords.client

import com.hemogoblins.betterrecords.item.ModItems
import com.hemogoblins.betterrecords.item.RecordItem
import net.minecraft.client.color.item.ItemColor
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent

/**
 * Client-only item tinting. Split out of ModItems so the dedicated server never
 * loads client classes.
 */
object ModItemColors {

    /** Since 1.21 item tints are ARGB, an RGB value without alpha renders fully transparent. */
    private const val OPAQUE = 0xFF000000.toInt()

    @SubscribeEvent
    fun registerItemColors(event: RegisterColorHandlersEvent.Item) {
        val color = ItemColor { stack, tintIndex ->
            if (tintIndex < 1) {
                return@ItemColor OPAQUE or 0xFFFFFF
            }

            OPAQUE or RecordItem.getColor(stack)
        }

        event.register(color, ModItems.RECORD.get())
    }

    fun register(eventBus: IEventBus) {
        eventBus.register(this)
    }
}
