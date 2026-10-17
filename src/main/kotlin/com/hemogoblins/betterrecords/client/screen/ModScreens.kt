package com.hemogoblins.betterrecords.client.screen

import com.hemogoblins.betterrecords.menu.ModMenuTypes
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent

object ModScreens {

    @SubscribeEvent
    fun registerScreenConstructors(event: RegisterMenuScreensEvent) {
        event.register(ModMenuTypes.RECORD_ETCHER_MENU.get(), ::RecordEtcherScreen)
    }

    fun register(eventBus: IEventBus) {
        eventBus.register(this)
    }
}
