package com.hemogoblins.betterrecords.menu

import com.hemogoblins.betterrecords.BetterRecords
import net.minecraft.core.registries.Registries
import net.minecraft.world.inventory.MenuType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModMenuTypes {

    val MENU_TYPES: DeferredRegister<MenuType<*>> = DeferredRegister.create(Registries.MENU, BetterRecords.ID)

    val RECORD_ETCHER_MENU: DeferredHolder<MenuType<*>, MenuType<RecordEtcherMenu>> = registerMenu("record_etcher") {
        IMenuTypeExtension.create { windowId, inventory, buf -> RecordEtcherMenu(windowId, inventory, buf) }
    }

    fun register(eventBus: IEventBus) {
        MENU_TYPES.register(eventBus)
    }

    private fun <T : MenuType<*>> registerMenu(name: String, menu: Supplier<T>): DeferredHolder<MenuType<*>, T> {
        return MENU_TYPES.register(name, menu)
    }
}
