package com.hemogoblins.betterrecords

import com.hemogoblins.betterrecords.api.client.MusicCache
import com.hemogoblins.betterrecords.api.client.sound.AudioStreamRegistry
import com.hemogoblins.betterrecords.block.ModBlocks
import com.hemogoblins.betterrecords.block.renderer.ModRenderers
import com.hemogoblins.betterrecords.capability.ModCapabilities
import com.hemogoblins.betterrecords.client.cache.FilesystemCache
import com.hemogoblins.betterrecords.client.gui.ModOverlays
import com.hemogoblins.betterrecords.client.ModItemColors
import com.hemogoblins.betterrecords.client.sound.format.Mp3AudioStreamProvider
import com.hemogoblins.betterrecords.client.sound.format.OggAudioStreamProvider
import com.hemogoblins.betterrecords.client.screen.ModScreens
import com.hemogoblins.betterrecords.component.ModDataComponents
import com.hemogoblins.betterrecords.item.ModItems
import com.hemogoblins.betterrecords.menu.ModMenuTypes
import com.hemogoblins.betterrecords.network.ModNetwork
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLDedicatedServerSetupEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import thedarkcolour.kotlinforforge.neoforge.forge.runForDist
import java.io.File

/**
 * The main entrypoint for the mod, this class handles initialization, grabs the event bus,
 * and delegates registration to any of our registries. Additionally, it contains a place
 * for us to hook in sided behavior in the future, such as record library initialization.
 */
@Mod(BetterRecords.ID)
object BetterRecords {

    /** ID of the mod. Pretty much everything uses this for registry/localization keys, so this should never change */
    const val ID = "betterrecords"

    /** Global logger instance for the mod */
    val logger: Logger = LogManager.getLogger(ID)

    val cache: MusicCache by lazy {
        logger.info("Initializing Cache...")
        FilesystemCache(
            File(BRConfig.Client.cacheTempDirectory.get()),
            File(BRConfig.Client.cacheDirectory.get())
        )
    }

    /** Shorthand for a [ResourceLocation] in the mod's namespace */
    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(ID, path)

    init {
        BRConfig.register()

        ModDataComponents.register(MOD_BUS)
        ModBlocks.register(MOD_BUS)
        ModItems.register(MOD_BUS)
        ModCapabilities.register(MOD_BUS)
        ModMenuTypes.register(MOD_BUS)
        ModNetwork.register(MOD_BUS)

        runForDist(
                clientTarget = {
                    ModItemColors.register(MOD_BUS)
                    ModScreens.register(MOD_BUS)
                    ModRenderers.register(MOD_BUS)
                    MOD_BUS.addListener(::onClientSetup)
                    MOD_BUS.addListener(ModOverlays::register)
                },
                serverTarget = {
                    MOD_BUS.addListener(::onServerSetup)
                }
        )
    }

    /**
     * Client side setup events
     */
    private fun onClientSetup(event: FMLClientSetupEvent) {
        AudioStreamRegistry.register(OggAudioStreamProvider)
        AudioStreamRegistry.register(Mp3AudioStreamProvider)
    }

    /**
     * Server side setup events
     */
    private fun onServerSetup(event: FMLDedicatedServerSetupEvent) {

    }
}
