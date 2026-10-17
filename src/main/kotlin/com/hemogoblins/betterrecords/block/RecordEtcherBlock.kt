package com.hemogoblins.betterrecords.block

import com.hemogoblins.betterrecords.block.entity.RecordEtcherBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class RecordEtcherBlock(properties: Properties): BaseEntityBlock(properties) {

    /* (Y-Up)
    / X1 Y1 Z1
    / X2 Y2 Z2 */
    val SHAPE: VoxelShape = Block.box(
            1.0, 0.0, 1.0,
            15.0, 14.0, 15.0
    )

    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec(::RecordEtcherBlock)

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return RecordEtcherBlockEntity(pos, state)
    }

    // Opening the GUI doesn't depend on the held item, so useItemOn falls through
    // to this (its default is PASS_TO_DEFAULT_BLOCK_INTERACTION).
    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS
        }

        val blockEntity = level.getBlockEntity(pos) as? RecordEtcherBlockEntity ?: return InteractionResult.PASS
        (player as ServerPlayer).openMenu(blockEntity, pos)

        return InteractionResult.CONSUME
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block) {
            (level.getBlockEntity(pos) as? RecordEtcherBlockEntity)?.dropContents()
        }

        super.onRemove(state, level, pos, newState, isMoving)
    }

    override fun getShape(state: BlockState, getter: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return SHAPE
    }

    override fun getRenderShape(state: BlockState): RenderShape {
        return RenderShape.MODEL
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, entityType: BlockEntityType<T>): BlockEntityTicker<T>? {
        return if (level.isClientSide) {
            createTickerHelper(entityType, ModBlocks.RECORD_ETCHER_ENTITY.get(), RecordEtcherBlockEntity.animationTicker)
        } else null
    }
}
