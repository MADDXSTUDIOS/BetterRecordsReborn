package com.hemogoblins.betterrecords.mixin;

import com.hemogoblins.betterrecords.BRConfig;
import com.hemogoblins.betterrecords.BetterRecords;
import com.hemogoblins.betterrecords.api.client.sound.AudioStreamRegistry;
import com.hemogoblins.betterrecords.api.client.sound.RecordAudioStream;
import com.hemogoblins.betterrecords.client.sound.DecodedRecord;
import com.hemogoblins.betterrecords.client.sound.MonoDownmix;
import com.hemogoblins.betterrecords.client.sound.ProgressInputStream;
import com.hemogoblins.betterrecords.client.sound.RecordPlayback;
import com.hemogoblins.betterrecords.client.sound.RecordPlayingSound;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Hooks Better Records' file-backed sounds into the vanilla sound engine.
 *
 * Decoding happens off-thread here, playback (one positional OpenAL source per speaker,
 * kept in sync) is handled by {@link RecordPlayback}.
 */
@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    @Shadow @Final private ChannelAccess channelAccess;
    @Shadow @Final private SoundEngineExecutor executor;

    @Unique
    private RecordPlayback betterRecords$playback;

    /**
     * Sounds whose audio is still being decoded off-thread. A stop() that
     * arrives while a sound is in here cancels it before it ever plays.
     * Only ever touched on the main thread.
     */
    @Unique
    private final Set<RecordPlayingSound> betterRecords$loading = new HashSet<>();

    @Unique
    private RecordPlayback betterRecords$playback() {
        if (betterRecords$playback == null) {
            betterRecords$playback = new RecordPlayback(this.channelAccess, this.executor);
        }
        return betterRecords$playback;
    }

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    public void onPlay(SoundInstance sound, CallbackInfo ci) {
        if (!(sound instanceof RecordPlayingSound recordSound)) {
            return;
        }

        // We fully handle our own file-backed sounds, so skip vanilla's handling.
        ci.cancel();

        File soundFile = new File(BRConfig.Client.INSTANCE.getCacheDirectory().get(), recordSound.getFile());

        // Mark the sound as loading so a stop() arriving before it is ready can cancel it,
        betterRecords$loading.add(recordSound);
        recordSound.setLoading(true);

        CompletableFuture
                .supplyAsync(() -> betterRecords$decode(recordSound, soundFile), Util.backgroundExecutor())
                .thenAcceptAsync(decoded -> {
                    boolean wanted = betterRecords$loading.remove(recordSound);
                    recordSound.setLoading(false);
                    if (wanted) {
                        betterRecords$playback().start(recordSound, decoded.getBuffer(), decoded.getDurationSeconds());
                    }
                }, Minecraft.getInstance())
                .exceptionally(throwable -> {
                    Minecraft.getInstance().execute(() -> betterRecords$loading.remove(recordSound));
                    recordSound.setLoading(false);
                    BetterRecords.INSTANCE.getLogger().error("Failed to play record sound", throwable);
                    return null;
                });
    }

    /**
     * Reads and decodes a cached audio file into a static buffer, mixed down to mono
     * so OpenAL can position it in the world.
     */
    @Unique
    private DecodedRecord betterRecords$decode(RecordPlayingSound recordSound, File soundFile) {
        if (!soundFile.exists()) {
            throw new RuntimeException("Sound file not found: " + soundFile.getAbsolutePath());
        }

        try (FileInputStream stream = new FileInputStream(soundFile);
             ProgressInputStream progressStream = new ProgressInputStream(stream, soundFile.length(), recordSound);
             RecordAudioStream audioStream = AudioStreamRegistry.INSTANCE.open(progressStream)) {
            var pcm = audioStream.readAll();
            MonoDownmix.Result mono = MonoDownmix.toMono(pcm, audioStream.getFormat());
            float duration = MonoDownmix.durationSeconds(mono.data, mono.format);
            SoundBuffer soundBuffer = new SoundBuffer(mono.data, mono.format);
            recordSound.setLoadProgress(1.0F);
            return new DecodedRecord(soundBuffer, duration);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // SoundEngine has two stop overloads, target the SoundInstance one explicitly.
    @Inject(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    public void onStop(SoundInstance sound, CallbackInfo ci) {
        if (sound instanceof RecordPlayingSound recordSound) {
            // Cancel playback if the sound is still being decoded
            betterRecords$loading.remove(recordSound);
            betterRecords$playback().stop(recordSound);
            ci.cancel();
        }
    }

    /** Leaving a world stops every sound, records included. */
    @Inject(method = "stopAll()V", at = @At("HEAD"))
    public void onStopAll(CallbackInfo ci) {
        betterRecords$loading.clear();
        betterRecords$playback().stopAll();
    }

    /** Keeps each record's speakers in step with its wiring, volume slider and play radius. */
    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    public void onTick(boolean isGamePaused, CallbackInfo ci) {
        betterRecords$playback().tick(isGamePaused);
    }
}
