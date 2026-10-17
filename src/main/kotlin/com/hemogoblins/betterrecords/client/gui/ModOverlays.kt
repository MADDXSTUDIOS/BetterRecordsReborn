package com.hemogoblins.betterrecords.client.gui

import com.hemogoblins.betterrecords.BetterRecords
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers

/**
 * Registers the mod's HUD overlays (called "GUI layers" on NeoForge 1.20.6+). Wired up on the client from
 * [com.hemogoblins.betterrecords.BetterRecords].
 */
object ModOverlays {

    fun register(event: RegisterGuiLayersEvent) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, BetterRecords.id("record_loading"), RecordLoadingOverlay)
    }
}
