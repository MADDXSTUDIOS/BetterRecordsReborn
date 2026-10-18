package com.hemogoblins.betterrecords

import net.minecraft.client.Minecraft
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import java.io.File

object BRConfig {

    fun register() {
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.CLIENT, Client.spec)
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.SERVER, Server.spec)
    }

    object Client {
        val spec: ModConfigSpec

        // For now we only support the filesystem cache... This may break
        // config in the future
        val cacheTempDirectory: ModConfigSpec.ConfigValue<String>
            get() {
                File(Minecraft.getInstance().gameDirectory, field.get()).mkdirs()
                return field
            }

        val cacheDirectory: ModConfigSpec.ConfigValue<String>
            get() {
                File(Minecraft.getInstance().gameDirectory, field.get()).mkdirs()
                return field
            }

        init {
            val builder = ModConfigSpec.Builder()

            builder
                .comment("Music Download Cache")
                .push("cache")

            this.cacheTempDirectory = builder
                .comment("Temporary directory for downloads")
                .define("tempDirectory", "betterrecords/temp")

            this.cacheDirectory = builder
                .comment("Location to store cached files")
                .define("directory", "betterrecords/cache")

            builder.pop()
            spec = builder.build()
        }
    }

    object Common {
    }

    /** Server config, synced to clients on join so both sides agree on wire length and radius. */
    object Server {
        val spec: ModConfigSpec

        val wireLength: ModConfigSpec.IntValue
        val recordPlayerRadius: ModConfigSpec.IntValue
        val maxSpeakerRadius: ModConfigSpec.IntValue

        init {
            val builder = ModConfigSpec.Builder()

            builder
                .comment("Speakers and wires")
                .push("speakers")

            wireLength = builder
                .comment("Maximum length of a speaker wire, in blocks")
                .defineInRange("wireLength", 16, 5, 80)

            recordPlayerRadius = builder
                .comment("How far a record player can be heard on its own, in blocks. Each wired speaker adds to this")
                .defineInRange("recordPlayerRadius", 24, 4, 256)

            maxSpeakerRadius = builder
                .comment("Maximum distance a record player can be heard from, in blocks, including speakers. -1 for no limit")
                .defineInRange("maxSpeakerRadius", -1, -1, 1000)

            builder.pop()
            spec = builder.build()
        }
    }
}
