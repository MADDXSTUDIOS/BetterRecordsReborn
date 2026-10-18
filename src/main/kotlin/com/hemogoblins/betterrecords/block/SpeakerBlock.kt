package com.hemogoblins.betterrecords.block

import com.hemogoblins.betterrecords.block.entity.SpeakerBlockEntity
import com.hemogoblins.betterrecords.wire.WireConnections
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Speakers increase how far a record player can be heard, and let players hear it from wherever the speaker is.
 * Ported from the 1.12.2 version of Better Records, where the three sizes were one block with a size property.
 */
enum class SpeakerSize(
    val id: String,
    /** Blocks added to the record player's play radius. Values from 1.12.2. */
    val radiusIncrease: Float,
    /** Collision/selection box in pixels: x1, y1, z1, x2, y2, z2 (front facing north). */
    box: DoubleArray,
) {
    SMALL("small", 15f, doubleArrayOf(4.0, 0.0, 4.0, 12.0, 10.0, 12.0)),
    MEDIUM("medium", 35f, doubleArrayOf(3.0, 0.0, 3.0, 13.0, 14.0, 13.0)),
    LARGE("large", 70f, doubleArrayOf(2.0, 0.0, 2.0, 14.0, 24.0, 14.0));

    /** Speakers are square from above, so one shape works for every facing. */
    val shape: VoxelShape = Shapes.or(
        Block.box(box[0], box[1], box[2], box[3], box[4], box[5]),
    )

    val displayName get() = "${id.replaceFirstChar { it.uppercase() }} Speaker"
}

class SpeakerBlock(val size: SpeakerSize, properties: Properties) : BaseEntityBlock(properties) {

    companion object {
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    // Each size is its own block instance, so the codec just needs to rebuild the same size.
    override fun codec(): MapCodec<out BaseEntityBlock> = simpleCodec { SpeakerBlock(size, it) }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = SpeakerBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    /** The front of the speaker faces whoever placed it. */
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = size.shape

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoving: Boolean) {
        if (state.block != newState.block && level is ServerLevel) {
            (level.getBlockEntity(pos) as? SpeakerBlockEntity)?.let {
                WireConnections.disconnectSpeaker(level, it, dropWireAt = pos)
            }
        }
        super.onRemove(state, level, pos, newState, isMoving)
    }
}
