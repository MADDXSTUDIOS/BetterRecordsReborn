package com.hemogoblins.betterrecords

import net.minecraft.client.Minecraft
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import java.io.File

object BRConfig {

    fun register() {
        ModLoadingContext.get().activeContainer.registerConfig(ModConfig.Type.CLIENT, Client.spec)
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

    object Server {

    }
}
