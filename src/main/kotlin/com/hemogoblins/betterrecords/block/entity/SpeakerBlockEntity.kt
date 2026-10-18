package com.hemogoblins.betterrecords.block.entity

import com.hemogoblins.betterrecords.block.ModBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

/**
 * Remembers which record player a speaker is wired to, so breaking or cutting the speaker can
 * unhook it from that player. The player keeps the list of its speakers (see [RecordPlayerBlockEntity.speakers]).
 */
class SpeakerBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(ModBlocks.SPEAKER_ENTITY.get(), pos, state) {

    companion object {
        private const val NBT_HOME = "home"
    }

    private var storedHome: BlockPos? = null

    /** Position of the record player this speaker is wired to, or null if it isn't connected. */
    var home: BlockPos?
        get() = storedHome
        set(value) {
            storedHome = value
            setChanged()
            level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_CLIENTS)
        }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        storedHome = if (tag.contains(NBT_HOME)) BlockPos.of(tag.getLong(NBT_HOME)) else null
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        storedHome?.let { tag.putLong(NBT_HOME, it.asLong()) }
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        CompoundTag().apply { storedHome?.let { putLong(NBT_HOME, it.asLong()) } }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)
}
