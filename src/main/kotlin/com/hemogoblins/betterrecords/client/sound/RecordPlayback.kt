package com.hemogoblins.betterrecords.client.sound

import com.mojang.blaze3d.audio.Channel
import com.mojang.blaze3d.audio.Library
import com.mojang.blaze3d.audio.SoundBuffer
import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.sounds.ChannelAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import java.util.concurrent.Executor

/**
 * Plays decoded records through Minecraft's OpenAL channels as positional (3D) sound.
 *
 * Every place the record is heard from (the record player, or each wired speaker, see [RecordEmitters])
 * gets its own OpenAL source, all sharing one decoded buffer. Sources are kept in step with each other:
 * a speaker wired up mid-song starts at the current position in the song instead of from the top.
 *
 * Main-thread only, apart from the lambdas passed to [ChannelAccess.ChannelHandle.execute],
 * which run on the sound thread.
 */
class RecordPlayback(
    private val channelAccess: ChannelAccess,
    /** The sound engine's own executor (the sound thread), used to free OpenAL buffers. */
    private val soundThread: Executor,
) {

    private inner class Active(val sound: RecordPlayingSound, val buffer: SoundBuffer, val durationSeconds: Float) {
        /** One OpenAL source per position the record is heard from. */
        val channels = mutableMapOf<BlockPos, ChannelAccess.ChannelHandle>()
        /** Positions whose channel has been requested but not handed over yet. */
        val pending = mutableSetOf<BlockPos>()

        /** Song position in seconds, read back from a live source on the sound thread. */
        @Volatile var offsetSeconds = 0f
        @Volatile var offsetReadAt = Util.getMillis()

        var stopping = false

        /** Best guess of where the song is right now, for starting a new source in sync. */
        fun currentOffset(): Float = offsetSeconds + (Util.getMillis() - offsetReadAt) / 1000f
    }

    private val active = mutableMapOf<RecordPlayingSound, Active>()

    /** Records that have stopped but whose sources haven't been released yet, so the buffer can't be freed. */
    private val finishing = mutableListOf<Active>()

    /** Starts playing [sound] from a decoded, mono [buffer]. */
    fun start(sound: RecordPlayingSound, buffer: SoundBuffer, durationSeconds: Float) {
        val record = Active(sound, buffer, durationSeconds)
        active[sound] = record
        sound.startedPlayingAt = Util.getMillis()
        sync(record)
    }

    fun stop(sound: RecordPlayingSound) {
        val record = active.remove(sound) ?: return
        record.stopping = true
        record.channels.values.forEach { it.execute(Channel::stop) }
        finishing += record
    }

    fun stopAll() {
        active.keys.toList().forEach(::stop)
    }

    /** Called every sound engine tick. Sources are left alone while the game is paused. */
    fun tick(paused: Boolean) {
        if (paused) return

        val iterator = active.values.iterator()
        while (iterator.hasNext()) {
            val record = iterator.next()

            // Forget sources that OpenAL has finished with.
            record.channels.entries.removeIf { it.value.isStopped }

            val played = Util.getMillis() - record.sound.startedPlayingAt
            val songOver = record.channels.isEmpty() && record.pending.isEmpty() &&
                played > 1000 && record.currentOffset() >= record.durationSeconds - 0.5f

            if (songOver) {
                iterator.remove()
                finishing += record
                RecordSoundManager.onFinished(record.sound)
                continue
            }

            sync(record)
        }

        // Free OpenAL buffers once every source using them has been released.
        finishing.removeIf { record ->
            record.channels.entries.removeIf { it.value.isStopped }
            val done = record.channels.isEmpty() && record.pending.isEmpty()
            if (done) soundThread.execute { record.buffer.discardAlBuffer() }
            done
        }
    }

    /** Matches the record's sources to where it should be heard from, and updates their settings. */
    private fun sync(record: Active) {
        val pos = record.sound.pos
        val wanted = RecordEmitters.positionsFor(pos).toSet()
        val radius = RecordEmitters.radiusFor(pos)
        val volume = RecordEmitters.categoryVolume()

        // Speakers that were unwired or broken
        record.channels.keys.filter { it !in wanted }.forEach { gone ->
            record.channels.remove(gone)?.execute(Channel::stop)
        }

        // Speakers that were just wired up (or the first source when the song starts)
        wanted.filter { it !in record.channels && it !in record.pending }.forEach { emitter ->
            record.pending += emitter
            val startAt = record.currentOffset()
            channelAccess.createHandle(Library.Pool.STATIC).thenAcceptAsync({ handle ->
                record.pending -= emitter
                if (handle == null) return@thenAcceptAsync
                if (record.stopping || emitter !in RecordEmitters.positionsFor(pos)) {
                    handle.execute(Channel::stop)
                    if (record.stopping) record.channels[emitter] = handle // let tick() see it release
                    return@thenAcceptAsync
                }
                handle.execute { channel ->
                    channel.setPitch(1.0f)
                    channel.setLooping(false)
                    channel.setVolume(volume)
                    channel.linearAttenuation(radius)
                    channel.setRelative(false)
                    channel.setSelfPosition(Vec3.atCenterOf(emitter))
                    channel.attachStaticBuffer(record.buffer)
                    if (startAt > 0.05f) AL10.alSourcef(channel.source, AL11.AL_SEC_OFFSET, startAt)
                    channel.play()
                }
                record.channels[emitter] = handle
            }, Minecraft.getInstance())
        }

        // Keep volume / radius current (slider changes, speakers added elsewhere), and track the song position.
        var readOffset = false
        record.channels.forEach { (_, handle) ->
            val track = !readOffset
            readOffset = true
            handle.execute { channel ->
                channel.setVolume(volume)
                channel.linearAttenuation(radius)
                if (track && channel.playing()) {
                    record.offsetSeconds = AL10.alGetSourcef(channel.source, AL11.AL_SEC_OFFSET)
                    record.offsetReadAt = Util.getMillis()
                }
            }
        }
    }

}

/** A record decoded off-thread, ready to hand to [RecordPlayback.start]. */
class DecodedRecord(val buffer: SoundBuffer, val durationSeconds: Float)
