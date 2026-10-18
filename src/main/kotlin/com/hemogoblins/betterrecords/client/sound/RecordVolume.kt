package com.hemogoblins.betterrecords.client.sound

import com.hemogoblins.betterrecords.block.entity.RecordPlayerBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/**
 * Works out how loud a record should be for the local player.
 *
 * Distance is measured to the record player or the closest speaker wired to it, whichever is nearer,
 * and the volume falls off linearly to silence at the player's [RecordPlayerBlockEntity.songRadius].
 * This is the 1.12.2 speaker behaviour (VolumeCalcUtil), with the 1.20.1 rewrite's linear falloff.
 */
object RecordVolume {

    /** Base volume of a record at point blank range */
    private const val BASE_VOLUME = 1.0f

    @JvmStatic
    fun forRecordAt(pos: BlockPos): Float {
        val mc = Minecraft.getInstance()
        val listener = mc.player ?: return BASE_VOLUME
        val level = mc.level ?: return BASE_VOLUME
        val listenerPos = listener.position()

        val recordPlayer = level.getBlockEntity(pos) as? RecordPlayerBlockEntity
        val radius = recordPlayer?.songRadius ?: RecordPlayerBlockEntity.BASE_RADIUS

        var distance = listenerPos.distanceTo(Vec3.atCenterOf(pos))
        recordPlayer?.connectedSpeakers()?.forEach { (speakerPos, _) ->
            val d = listenerPos.distanceTo(Vec3.atCenterOf(speakerPos))
            if (d < distance) distance = d
        }

        if (distance >= radius) return 0f
        return BASE_VOLUME * (1f - (distance / radius).toFloat())
    }
}
