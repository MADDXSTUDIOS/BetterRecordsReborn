package com.hemogoblins.betterrecords.block.renderer

import com.hemogoblins.betterrecords.BetterRecords
import com.hemogoblins.betterrecords.block.RecordPlayerBlock.Companion.FACING
import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.core.component.DataComponents
import com.hemogoblins.betterrecords.item.WireCutterItem
import com.hemogoblins.betterrecords.item.WireItem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RecordPlayerRenderer(context: BlockEntityRendererProvider.Context) : BlockEntityRenderer<RecordPlayerBlockEntity> {

    companion object {
        val MODEL_LAYER_LOCATION = ModelLayerLocation(BetterRecords.id("record_player_model"), "main")
        private val TEXTURE: ResourceLocation = BetterRecords.id("textures/block/record_player.png")

        // todo: move to separate model file? or maybe this is fine
        fun createBodyLayer(): LayerDefinition {
            val offset = PartPose.ZERO // PartPose.offset(0.0f, 0.0f, 0.0f)
            val deformation = CubeDeformation.NONE //CubeDeformation(0.0f)

            return LayerDefinition.create(MeshDefinition().apply {
                root.addOrReplaceChild("base", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7.5f, -12.0f, -7.5f, 15.0f, 12.0f, 15.0f, deformation) // Base
                        .texOffs(0, 44).addBox(4.5f, -13.0f, 4.5f, 1.0f, 1.0f, 1.0f, deformation), offset) // Arm Peg (doesn't move)

                root.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 27).addBox(-7.0f, -15.0f, -7.0f, 14.0f, 3.0f, 14.0f, deformation), offset)
                root.addOrReplaceChild("peg", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, -13.0f, -0.5f, 1.0f, 1.0f, 1.0f, deformation), offset)
                root.addOrReplaceChild("arm", CubeListBuilder.create().texOffs(0, 44).addBox(4.5f, -14.0f, -2.5f, 1.0f, 1.0f, 8.0f, deformation), offset)
            }, 64, 64)
        }
    }

    private val itemRenderer: ItemRenderer = context.itemRenderer
    private val model_base: ModelPart
    private val model_lid: ModelPart
    private val model_peg: ModelPart
    private val model_arm: ModelPart

    init {
        context.bakeLayer(RecordPlayerRenderer.MODEL_LAYER_LOCATION).also {
            model_base = it.getChild("base")
            model_lid = it.getChild("lid")
            model_peg = it.getChild("peg")
            model_arm = it.getChild("arm")
        }
    }

    override fun render(
            entity: RecordPlayerBlockEntity,
            partialTick: Float,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int
    ) {
        val consumer: VertexConsumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE))
        val dir = entity.blockState.getValue(FACING).toYRot()

        poseStack.pushPose()
        poseStack.apply {
            translate(0.5f, 0.0f, 0.5f) // Move to center of block
            mulPose(Axis.XP.rotationDegrees(180f))
            mulPose(Axis.YP.rotationDegrees(dir))
        }

        // On 1.21, getBuffer() for a different RenderType ends the previous BufferBuilder,
        // so everything drawn with `consumer` has to happen before the lid / record grab their own buffers.
        this.model_base.render(poseStack, consumer, packedLight, packedOverlay)
        this.renderArm(entity, partialTick, poseStack, consumer, packedLight, packedOverlay)

        val rot = (((entity.level?.gameTime ?: 0) + partialTick) * 3f) % 360
        val toRot = if (entity.isPlaying) rot else 0f

        poseStack.pushPose()
        poseStack.mulPose(Axis.YP.rotationDegrees(toRot))
        this.model_peg.render(poseStack, consumer, packedLight, packedOverlay)
        poseStack.popPose()

        this.renderLid(entity, partialTick, poseStack, bufferSource, packedLight, packedOverlay)

        /** Might as well render anything that's in the slot,
         * so it's easier to tell what's happened in the event something else gets in somehow. */
        this.renderRecord(entity.getSlottedItem(), toRot, poseStack, bufferSource, packedLight, packedOverlay, entity.level)

        poseStack.popPose()

        // Drawn last, in the block's own (unrotated) space, since they grab their own buffers.
        if (isHoldingWireTool()) {
            renderWires(entity, poseStack, bufferSource)
            renderWireInfo(entity, poseStack, bufferSource, packedLight)
        }
    }

    /** Wires and the play radius are only shown while holding a wire or wire cutters, like 1.12.2. */
    private fun isHoldingWireTool(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return listOf(player.mainHandItem, player.offhandItem).any { it.item is WireItem || it.item is WireCutterItem }
    }

    private fun renderWires(entity: RecordPlayerBlockEntity, poseStack: PoseStack, bufferSource: MultiBufferSource) {
        val speakers = entity.speakers
        if (speakers.isEmpty()) return

        val lines = bufferSource.getBuffer(RenderType.lines())
        val pose = poseStack.last()
        val origin = entity.blockPos

        for (speaker in speakers) {
            // From the top of the record player to the middle of the speaker, relative to this block.
            val x1 = 0.5f
            val y1 = 0.9f
            val z1 = 0.5f
            val x2 = (speaker.x - origin.x) + 0.5f
            val y2 = (speaker.y - origin.y) + 0.5f
            val z2 = (speaker.z - origin.z) + 0.5f

            val dx = x2 - x1
            val dy = y2 - y1
            val dz = z2 - z1
            val len = sqrt(dx * dx + dy * dy + dz * dz).takeIf { it > 0f } ?: continue

            lines.addVertex(pose, x1, y1, z1).setColor(20, 20, 20, 255).setNormal(pose, dx / len, dy / len, dz / len)
            lines.addVertex(pose, x2, y2, z2).setColor(20, 20, 20, 255).setNormal(pose, dx / len, dy / len, dz / len)
        }
    }

    /** Floating "Play Radius" text above the player, plus a count of each speaker size. */
    private fun renderWireInfo(entity: RecordPlayerBlockEntity, poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {
        val mc = Minecraft.getInstance()
        val font = mc.font

        val lineList = mutableListOf<Component>(
            Component.translatable("overlay.${BetterRecords.ID}.play_radius", entity.songRadius.roundToInt())
        )
        entity.connectedSpeakers()
            .groupingBy { it.second }
            .eachCount()
            .toSortedMap()
            .forEach { (size, count) ->
                lineList += Component.translatable("overlay.${BetterRecords.ID}.speaker_count", count, Component.translatable("block.${BetterRecords.ID}.${size.id}_speaker"))
            }

        poseStack.pushPose()
        poseStack.translate(0.5f, 1.4f + lineList.size * 0.25f, 0.5f)
        poseStack.mulPose(mc.entityRenderDispatcher.cameraOrientation())
        poseStack.scale(0.025f, -0.025f, 0.025f)

        val matrix = poseStack.last().pose()
        val background = (mc.options.getBackgroundOpacity(0.25f) * 255f).toInt() shl 24
        lineList.forEachIndexed { i, line ->
            val x = -font.width(line) / 2f
            font.drawInBatch(line, x, i * 10f, 0xFFFFFF, false, matrix, bufferSource, Font.DisplayMode.NORMAL, background, packedLight)
        }
        poseStack.popPose()
    }

    // Wires can reach well past the block, so don't cull the renderer just because the player block is off screen.
    override fun getRenderBoundingBox(blockEntity: RecordPlayerBlockEntity): AABB {
        val pos = blockEntity.blockPos
        var box = AABB(pos).inflate(0.0, 1.5, 0.0)
        for (speaker in blockEntity.speakers) box = box.minmax(AABB(speaker))
        return box
    }

    private fun renderLid(
            entity: RecordPlayerBlockEntity,
            partialTick: Float,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int
    ) {
        val consumerNoCull: VertexConsumer = bufferSource.getBuffer(RenderType.armorCutoutNoCull(TEXTURE))

        var f1 = entity.getLidOpenness(partialTick)
        f1 = 1.0f - f1
        f1 = 1.0f - f1 * f1 * f1 * f1
        val lidDegrees = mapRange(0f, 1f, 0f, 60f, f1)

        poseStack.pushPose()
        poseStack.rotateAround(Axis.XN.rotationDegrees(lidDegrees), 0f, -pix(12f), pix(7f))
        this.model_lid.render(poseStack, consumerNoCull, packedLight, packedOverlay)
        poseStack.popPose()
    }

    private fun renderArm(
            entity: RecordPlayerBlockEntity,
            partialTick: Float,
            poseStack: PoseStack,
            consumer: VertexConsumer,
            packedLight: Int,
            packedOverlay: Int
    ) {
        var f1 = entity.getArmOpenness(partialTick)
        f1 = 1.0f - f1
        f1 = 1.0f - f1 * f1 * f1
        val lidDegrees = mapRange(0f, 1f, 0f, 20f, f1)

        poseStack.pushPose()
        poseStack.rotateAround(Axis.YP.rotationDegrees(lidDegrees), pix(5f), -pix(13.5f), pix(5f))
        this.model_arm.render(poseStack, consumer, packedLight, packedOverlay)
        poseStack.popPose()
    }

    private fun renderRecord(
            itemStack: ItemStack,
            rotation: Float,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int,
            level: Level?
    ) {
        poseStack.pushPose()
        poseStack.apply {
            translate(0f, -0.76f, 0f) // 0.78125
            mulPose(Axis.XP.rotationDegrees(90f))
            mulPose(Axis.ZN.rotationDegrees(rotation))
        }

        if (itemStack.has(DataComponents.JUKEBOX_PLAYABLE)) {
            renderVanillaDisc(itemStack, poseStack, bufferSource, packedLight, packedOverlay, level)
        } else {
            renderItem(itemStack, poseStack, bufferSource, packedLight, packedOverlay, level)
        }

        poseStack.popPose()
    }

    private fun renderVanillaDisc(
            itemStack: ItemStack,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int,
            level: Level?
    ) {
        val proxy = VanillaDiscProxies.proxyFor(itemStack, itemRenderer, level)
        renderItem(proxy, poseStack, bufferSource, packedLight, packedOverlay, level)
    }

    private fun renderItem(
            itemStack: ItemStack,
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            packedOverlay: Int,
            level: Level?
    ) {
        itemRenderer.renderStatic(itemStack, ItemDisplayContext.FIXED, packedLight,
                packedOverlay, poseStack, bufferSource, level, 1)
    }
}

/** For adjusting values in 16ths (1 = 1 pixel). */
private fun pix(float: Float) = (float/16)

/** My very favorite function in the whole world, MapRange, my beloved... */
private fun mapRange(fromMin: Float, fromMax: Float, toMin: Float, toMax: Float, value: Float): Float {
    return (value-fromMin) / (fromMax-fromMin) * (toMax-toMin) + toMin
}