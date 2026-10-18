package com.hemogoblins.betterrecords.block

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.block.entity.RecordEtcherBlockEntity
import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import com.hemogoblins.betterrecords.block.entity.SpeakerBlockEntity
import com.hemogoblins.betterrecords.item.ModItems
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister
import java.util.function.Supplier

object ModBlocks {

    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(BetterRecords.ID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, BetterRecords.ID)

    val RECORD_ETCHER: DeferredBlock<RecordEtcherBlock> = registerBlock("record_etcher") {
        RecordEtcherBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.JUKEBOX).noOcclusion())
    }

    val RECORD_PLAYER: DeferredBlock<RecordPlayerBlock> = registerBlock("record_player") {
        RecordPlayerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.JUKEBOX).noOcclusion())
    }

    val SMALL_SPEAKER: DeferredBlock<SpeakerBlock> = registerSpeaker(SpeakerSize.SMALL, 2.0f)
    val MEDIUM_SPEAKER: DeferredBlock<SpeakerBlock> = registerSpeaker(SpeakerSize.MEDIUM, 3.0f)
    val LARGE_SPEAKER: DeferredBlock<SpeakerBlock> = registerSpeaker(SpeakerSize.LARGE, 4.0f)

    val SPEAKERS = listOf(SMALL_SPEAKER, MEDIUM_SPEAKER, LARGE_SPEAKER)

    val RECORD_ETCHER_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<RecordEtcherBlockEntity>> =
        registerBlockEntity("record_etcher") {
            BlockEntityType.Builder.of(::RecordEtcherBlockEntity, RECORD_ETCHER.get()).build(null)
        }

    val RECORD_PLAYER_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<RecordPlayerBlockEntity>> =
        registerBlockEntity("record_player") {
            BlockEntityType.Builder.of(::RecordPlayerBlockEntity, RECORD_PLAYER.get()).build(null)
        }

    val SPEAKER_ENTITY: DeferredHolder<BlockEntityType<*>, BlockEntityType<SpeakerBlockEntity>> =
        registerBlockEntity("speaker") {
            BlockEntityType.Builder.of(::SpeakerBlockEntity, *SPEAKERS.map { it.get() }.toTypedArray()).build(null)
        }

    fun register(eventBus: IEventBus) {
        BLOCKS.register(eventBus)
        BLOCK_ENTITIES.register(eventBus)
    }

    private fun <T : Block> registerBlock(name: String, block: Supplier<T>): DeferredBlock<T> {
        return BLOCKS.register(name, block).apply {
            registerBlockItem(name, this)
        }
    }

    private fun registerSpeaker(size: SpeakerSize, strength: Float): DeferredBlock<SpeakerBlock> =
        registerBlock("${size.id}_speaker") {
            SpeakerBlock(
                size,
                BlockBehaviour.Properties.ofFullCopy(Blocks.NOTE_BLOCK)
                    .strength(strength, strength * 3)
                    .noOcclusion()
            )
        }

    private fun <T : Block> registerBlockItem(name: String, block: DeferredBlock<T>): DeferredItem<BlockItem> {
        return ModItems.registerItem(name) { BlockItem(block.get(), Item.Properties()) }
    }

    private fun <T : BlockEntityType<*>> registerBlockEntity(name: String, blockEntity: Supplier<T>): DeferredHolder<BlockEntityType<*>, T> {
        return BLOCK_ENTITIES.register(name, blockEntity)
    }
}
