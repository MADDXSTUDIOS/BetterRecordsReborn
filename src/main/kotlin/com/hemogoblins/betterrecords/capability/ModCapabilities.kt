package com.hemogoblins.betterrecords.capability

import com.hemogoblins.betterrecords.block.ModBlocks
import com.hemogoblins.betterrecords.block.entity.SingleSlotBlockEntity
import net.minecraft.core.Direction
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.ICapabilityProvider
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.items.IItemHandler

/**
 * Capability registration.
 *
 * Record data that used to be stored in item capabilities (colour, music) now lives in
 * [com.hemogoblins.betterrecords.component.ModDataComponents]. The only capability left is the
 * item handler exposed by the single slot block entities, so hoppers and pipes can still insert records.
 */
object ModCapabilities {

    @SubscribeEvent
    fun registerCapabilities(event: RegisterCapabilitiesEvent) {
        val itemHandler = ICapabilityProvider<SingleSlotBlockEntity<*>, Direction?, IItemHandler> { be, _ -> be.itemHandler }

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlocks.RECORD_ETCHER_ENTITY.get(), itemHandler)
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlocks.RECORD_PLAYER_ENTITY.get(), itemHandler)
    }

    fun register(eventBus: IEventBus) {
        eventBus.register(this)
    }
}
