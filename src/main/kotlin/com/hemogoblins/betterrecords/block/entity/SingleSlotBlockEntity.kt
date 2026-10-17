package com.hemogoblins.betterrecords.block.entity

import com.hemogoblins.betterrecords.component.ModDataComponents
import com.hemogoblins.betterrecords.item.RecordItem
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.Containers
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.ItemStackHandler

/** A block entity with logic for a single-slot inventory, to be extended by:
 *  Etcher, Player, Tuner, Radio */
open class SingleSlotBlockEntity<T : BlockEntity?>(
        pos: BlockPos,
        state: BlockState,
        entityType: BlockEntityType<T>
): BlockEntity(entityType, pos, state) {

    companion object {
        const val NBT_TAG_INVENTORY = "inventory"

        /** Better Records records, or anything that plays in a vanilla jukebox (vanilla & modded music discs). */
        fun isItemValid(stack: ItemStack): Boolean {
            return stack.item is RecordItem ||
                stack.has(ModDataComponents.MUSIC.get()) ||
                stack.has(DataComponents.JUKEBOX_PLAYABLE)
        }
    }

    /**
     * Exposed to other mods (hoppers, pipes) through the item handler block capability,
     * registered in [com.hemogoblins.betterrecords.capability.ModCapabilities].
     */
    val itemHandler: ItemStackHandler = object : ItemStackHandler(1) {
        override fun onContentsChanged(slot: Int) {
            setChanged() // Marks dirty and notifies redstone
            triggerUpdate()
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean {
            return SingleSlotBlockEntity.isItemValid(stack) && super.isItemValid(slot, stack)
        }

        override fun getSlotLimit(slot: Int): Int = 1
    }

    val isEmpty: Boolean
        get() = itemHandler.getStackInSlot(0).isEmpty


    fun getSlottedItem(): ItemStack {
        return itemHandler.getStackInSlot(0)
    }

    fun removeSlottedItem() {
        this.itemHandler.setStackInSlot(0, ItemStack.EMPTY)
    }

    /** Replaces the item in the slot, notifying listeners. */
    fun setSlottedItem(stack: ItemStack) {
        this.itemHandler.setStackInSlot(0, stack)
    }

    /** Returns true when item is valid and was inserted */
    fun tryInsertItem(itemStack: ItemStack): Boolean {
        if (isItemValid(itemStack) && this.isEmpty && !itemStack.isEmpty) {
            this.itemHandler.setStackInSlot(0, itemStack.copyWithCount(1))
            itemStack.shrink(1)
            return true
        } else return false
    }

    open fun triggerUpdate() {
        level?.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_ALL)
    }

    /** Called on destruction */
    fun dropContents() {
        SimpleContainer(itemHandler.slots).let {
            it.setItem(0, itemHandler.getStackInSlot(0))
            this.level?.let { lvl -> Containers.dropContents(lvl, this.worldPosition, it) }
        }
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        tag.takeIf { it.contains(NBT_TAG_INVENTORY) }?.let {
            itemHandler.deserializeNBT(registries, it.getCompound(NBT_TAG_INVENTORY))
        }
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.put(NBT_TAG_INVENTORY, itemHandler.serializeNBT(registries))
        writeTag(tag)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        return CompoundTag().apply {
            put(NBT_TAG_INVENTORY, itemHandler.serializeNBT(registries))
            writeTag(this)
        }
    }

    /** To be overridden by subclasses to write their specific data. */
    open fun writeTag(tag: CompoundTag): CompoundTag {
        // After everything's been implemented, it might be better to write tags more selectively for optimization (per forge docs).
        return tag
    }
}
