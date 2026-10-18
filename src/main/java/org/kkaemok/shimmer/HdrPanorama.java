package org.kkaemok.shimmer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class HdrPanorama implements PreparableReloadListener {
    public static final Logger LOGGER = LoggerFactory.getLogger("shimmer");
    public static final HdrPanorama INSTANCE = new HdrPanorama();
    private final Object decodeLock = new Object();
    private volatile Ticket ticket;
    private volatile boolean closed;
    // Accessed only by render/apply thread, including mixin draw calls and shutdown.
    private GpuPanorama active;

    private HdrPanorama() {}
    public GpuPanorama active() { return active; }

    @Override public CompletableFuture<Void> reload(SharedState state, Executor worker, PreparationBarrier barrier, Executor renderExecutor) {
        Ticket next = new Ticket();
        Ticket previous = ticket;
        ticket = next;
        if (previous != null) previous.cancel();
        // ShaderManager replaces/closes its pipeline cache during this reload. Stop using
        // the previous compiled pipeline before that happens, even if a frame is drawn
        // between the vanilla shader apply and our texture apply.
        renderExecutor.execute(() -> { if (ticket == next) clearActive(); });
        var prepared = CompletableFuture.supplyAsync(() -> {
            // Bound memory even if reloads overlap. Canceled work checks between faces.
            synchronized (decodeLock) {
                if (!next.canceled && !closed) {
                    try {
                        PanoramaConfig config = PanoramaConfig.load();
                        PanoramaPixels pixels = config.enabled() ? PanoramaLoader.load(state.resourceManager(), () -> next.canceled || closed, config.untaggedTransform()) : null;
                        next.publish(pixels, config);
                    } catch (Exception | OutOfMemoryError e) {
                        LOGGER.warn("HDR preparation failed; using vanilla PNG: {}", e.toString());
                    }
                }
            }
            return next;
        }, worker);
        return prepared.thenCompose(barrier::wait).thenAcceptAsync(value -> {
            RenderSystem.assertOnRenderThread();
            synchronized (value) {
                if (closed || ticket != value || value.canceled) return;
                clearActive();
                if (value.pixels != null) {
                    try { active = GpuPanorama.upload(value.pixels, value.config); }
                    catch (Exception | OutOfMemoryError e) { LOGGER.warn("HDR panorama upload failed; using vanilla PNG: {}", e.toString()); }
                }
            }
        }, renderExecutor).whenComplete((ignored, error) -> next.cancel());
    }

    public void close() {
        closed = true;
        Ticket current = ticket;
        if (current != null) current.cancel();
        clearActive();
    }

    private void clearActive() { if (active != null) { active.close(); active = null; } }
    @Override public String getName() { return "Shimmer"; }

    private static final class Ticket {
        volatile boolean canceled;
        PanoramaPixels pixels;
        PanoramaConfig config;
        synchronized void publish(PanoramaPixels value, PanoramaConfig settings) {
            if (canceled) { if (value != null) value.close(); }
            else { pixels = value; config = settings; }
        }
        synchronized void cancel() {
            canceled = true;
            if (pixels != null) { pixels.close(); pixels = null; }
        }
    }
}
