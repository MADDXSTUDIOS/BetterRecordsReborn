package com.hemogoblins.betterrecords.component

import com.hemogoblins.betterrecords.BetterRecords
import com.mojang.serialization.Codec
import net.minecraft.core.component.DataComponentType
import net.minecraft.network.codec.ByteBufCodecs
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister

/**
 * Item data stored on records.
 *
 * On 1.20.1 this lived in the `colorable` and `musicHolder` item capabilities.
 * Item capabilities can no longer hold data on 1.20.5+, so it is stored as data components instead.
 * A component that is absent means "default" (white / not etched).
 */
object ModDataComponents {

    val DATA_COMPONENTS: DeferredRegister.DataComponents = DeferredRegister.createDataComponents(BetterRecords.ID)

    /** RGB colour of a record, used to tint its label. */
    val COLOR: DeferredHolder<DataComponentType<*>, DataComponentType<Int>> =
        DATA_COMPONENTS.registerComponentType<Int>("color") { builder ->
            builder
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT)
        }

    /** The song etched onto a record. */
    val MUSIC: DeferredHolder<DataComponentType<*>, DataComponentType<MusicData>> =
        DATA_COMPONENTS.registerComponentType<MusicData>("music") { builder ->
            builder
                .persistent(MusicData.CODEC)
                .networkSynchronized(MusicData.STREAM_CODEC)
        }

    fun register(eventBus: IEventBus) {
        DATA_COMPONENTS.register(eventBus)
    }
}
