package com.hemogoblins.betterrecords.item

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.block.ModBlocks
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModItems {

    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(BetterRecords.ID)
    val CREATIVE_TABS: DeferredRegister<CreativeModeTab> = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BetterRecords.ID)

    val RECORD: DeferredItem<RecordItem> = registerItem("record") {
        RecordItem(Item.Properties().stacksTo(1))
    }

    val WIRE: DeferredItem<WireItem> = registerItem("wire") {
        WireItem(Item.Properties())
    }

    val WIRE_CUTTERS: DeferredItem<WireCutterItem> = registerItem("wire_cutters") {
        WireCutterItem(Item.Properties().stacksTo(1).durability(238))
    }

    // TODO: Move somewhere common to items and blocks
    val CREATIVE_TAB: DeferredHolder<CreativeModeTab, CreativeModeTab> = CREATIVE_TABS.register(BetterRecords.ID, Supplier {
        with(CreativeModeTab.builder()) {
            title(Component.translatable("item_group.${BetterRecords.ID}"))
            icon { ItemStack(RECORD.get()) }

            displayItems { _, output ->
                output.apply {
                    accept(RECORD.get())
                    accept(ModBlocks.RECORD_ETCHER.get())
                    accept(ModBlocks.RECORD_PLAYER.get())
                    ModBlocks.SPEAKERS.forEach { accept(it.get()) }
                    accept(WIRE.get())
                    accept(WIRE_CUTTERS.get())
                }
            }

            build()
        }
    })

    fun register(eventBus: IEventBus) {
        ITEMS.register(eventBus)
        CREATIVE_TABS.register(eventBus)
    }

    fun <T : Item> registerItem(name: String, item: Supplier<T>): DeferredItem<T> {
        return ITEMS.register(name, item)
    }
}
