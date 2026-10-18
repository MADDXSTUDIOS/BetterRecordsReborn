package com.hemogoblins.betterrecords.block.entity

import com.hemogoblins.betterrecords.BRConfig
import com.hemogoblins.betterrecords.block.ModBlocks
import com.hemogoblins.betterrecords.block.SpeakerBlock
import com.hemogoblins.betterrecords.block.SpeakerSize
import com.hemogoblins.betterrecords.block.RecordPlayerBlock
import com.hemogoblins.betterrecords.block.RecordPlayerBlock.Companion.FACING
import com.hemogoblins.betterrecords.network.clientbound.PlayRecordPacket
import com.hemogoblins.betterrecords.network.clientbound.StopRecordPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.ChestLidController
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.PacketDistributor

class RecordPlayerBlockEntity(
        pos: BlockPos,
        state: BlockState
): SingleSlotBlockEntity<RecordPlayerBlockEntity>(
        pos,
        state,
        ModBlocks.RECORD_PLAYER_ENTITY.get()
) {

    companion object {
        const val NBT_TAG_PLAYING = "isPlaying"
        const val NBT_TAG_SPEAKERS = "speakers"

        /** How far a record player can be heard on its own, in blocks (server config, synced to clients). */
        val BASE_RADIUS: Float
            get() = try {
                BRConfig.Server.recordPlayerRadius.get().toFloat()
            } catch (e: IllegalStateException) {
                24f // config not loaded yet (main menu)
            }

        val animationTicker = BlockEntityTicker<RecordPlayerBlockEntity> { _, _, _, entity ->
            entity.lidController.tickLid()
            entity.armController.tickLid()
        }
    }

    private val lidController = ChestLidController()
    private val armController = ChestLidController()
    fun getLidOpenness(partialTick: Float) = lidController.getOpenness(partialTick)
    fun getArmOpenness(partialTick: Float) = armController.getOpenness(partialTick)

    var isPlaying = false

    /** Speakers wired to this player. Synced to clients so they can work out volume and draw the wires. */
    val speakers: List<BlockPos> get() = speakerList
    private val speakerList = mutableListOf<BlockPos>()

    fun addSpeaker(pos: BlockPos) {
        if (pos !in speakerList) {
            speakerList.add(pos.immutable())
            onSpeakersChanged()
        }
    }

    fun removeSpeaker(pos: BlockPos) {
        if (speakerList.remove(pos)) onSpeakersChanged()
    }

    fun clearSpeakers() {
        if (speakerList.isNotEmpty()) {
            speakerList.clear()
            onSpeakersChanged()
        }
    }

    private fun onSpeakersChanged() {
        setChanged()
        level?.sendBlockUpdated(blockPos, blockState, blockState, net.minecraft.world.level.block.Block.UPDATE_CLIENTS)
    }

    /** Speakers that are still there, paired with their size. Unloaded or missing speakers don't count. */
    fun connectedSpeakers(): List<Pair<BlockPos, SpeakerSize>> {
        val lvl = level ?: return emptyList()
        return speakerList.mapNotNull { pos ->
            if (!lvl.isLoaded(pos)) return@mapNotNull null
            (lvl.getBlockState(pos).block as? SpeakerBlock)?.let { pos to it.size }
        }
    }

    /**
     * How far the record can be heard, in blocks, measured from the player or whichever speaker is closest.
     * Each speaker adds to the radius (1.12.2 behaviour), capped by the server config.
     */
    val songRadius: Float
        get() {
            val radius = BASE_RADIUS + connectedSpeakers().sumOf { it.second.radiusIncrease.toDouble() }.toFloat()
            val max = try { BRConfig.Server.maxSpeakerRadius.get() } catch (e: IllegalStateException) { -1 }
            return if (max < 0) radius else minOf(radius, max.toFloat())
        }

    fun toggleOpenState(state: BlockState, boo: Boolean) {
        level!!.setBlock(this.blockPos, state.setValue(RecordPlayerBlock.OPEN, boo), 3)
        lidController.shouldBeOpen(RecordPlayerBlock.isOpen(state))
    }

    private fun togglePlaying(boo: Boolean) {
        isPlaying = boo
        armController.shouldBeOpen(isPlaying)
    }

    private fun shouldBePlaying(): Boolean {
        // temp logic to be replaced with playback state later
        return getSlottedItem().let{(!it.isEmpty) && isItemValid(it)}
    }

    fun popOutRecord() {
        if (!isEmpty && this.level != null && !this.level!!.isClientSide) {
            // level really likes to be null here
            level?.let{
                val dir = Vec3.atLowerCornerOf(blockState.getValue(FACING).normal)
                val vec = Vec3.atLowerCornerWithOffset(blockPos, 0.5, 0.76, 0.5)
                val dirMul = 0.2
                val itemEntity = ItemEntity(it, vec.x, vec.y, vec.z, getSlottedItem().copy())
                itemEntity.setDefaultPickUpDelay()

                /** Give the record some directional velocity forward,
                 * to make it easier to pick up, and so that it doesn't
                 * go backwards and clip through the lid (which looks bad) */
                itemEntity.deltaMovement = Vec3(
                        (2.0 * it.random.nextDouble() - 1) * 0.05f,
                        (2.0 * it.random.nextDouble() - 1) * 0.1f ,
                        (2.0 * it.random.nextDouble() - 1) * 0.05f).add(
                            dir.multiply(dirMul, dirMul, dirMul)
                        )

                removeSlottedItem()
                it.addFreshEntity(itemEntity)
            }
        }
    }

    override fun triggerUpdate() {
        val wasPlaying = isPlaying
        togglePlaying(shouldBePlaying())

        // Server side, send a start or stop packet if the state changed.
        if (level?.isClientSide == false && wasPlaying != isPlaying) {
            if (isPlaying) sendPlayRecord() else sendStopRecord()
        }

        super.triggerUpdate()
    }

    private fun sendPlayRecord() {
        val source = PlayRecordPacket.RecordSource.of(getSlottedItem(), level!!.registryAccess()) ?: return
        sendToTracking(PlayRecordPacket(blockPos, source))
    }

    fun stopPlaying() {
        if (level?.isClientSide == false && isPlaying) {
            togglePlaying(false)
            sendStopRecord()
            dropContents()
        }
    }

    private fun sendStopRecord() {
        sendToTracking(StopRecordPacket(blockPos))
    }

    /** Sends [payload] to every player that has this block's chunk loaded. */
    private fun sendToTracking(payload: CustomPacketPayload) {
        val serverLevel = level as? ServerLevel ?: return
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, ChunkPos(blockPos), payload)
    }

    // todo: once playback is implemented, save and resume song playback state
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        tag.takeIf { it.contains(NBT_TAG_PLAYING) }?.let{
            togglePlaying(it.getBoolean(NBT_TAG_PLAYING))
        }

        speakerList.clear()
        if (tag.contains(NBT_TAG_SPEAKERS)) {
            tag.getLongArray(NBT_TAG_SPEAKERS).mapTo(speakerList) { BlockPos.of(it) }
        }

        lidController.shouldBeOpen(RecordPlayerBlock.isOpen(blockState))
    }

    override fun writeTag(tag: CompoundTag): CompoundTag {
        return tag.apply {
            putBoolean(NBT_TAG_PLAYING, isPlaying)
            putLongArray(NBT_TAG_SPEAKERS, speakerList.map { it.asLong() }.toLongArray())
        }
    }
}
