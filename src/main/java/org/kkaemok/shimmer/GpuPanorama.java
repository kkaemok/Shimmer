package org.kkaemok.shimmer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.util.OptionalDouble;

public final class GpuPanorama implements AutoCloseable {
    private GpuTexture texture;
    private GpuTextureView view;
    private GpuSampler sampler;
    private GpuBuffer settings;
    private GpuBufferSlice settingsSlice;
    private final CompiledRenderPipeline pipeline;
    private final PanoramaConfig config;

    private GpuPanorama(CompiledRenderPipeline pipeline, PanoramaConfig config) { this.pipeline = pipeline; this.config = config; }

    public static GpuPanorama upload(PanoramaPixels pixels, PanoramaConfig config) throws IOException {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        GpuFormat format = pixels.half() ? GpuFormat.RGBA16_FLOAT : GpuFormat.RGBA32_FLOAT;
        var limits = device.getDeviceInfo().limits();
        long bytes = (long) pixels.size() * pixels.size() * 6 * format.blockSize();
        if (pixels.size() > limits.maxTextureSizeForFormat(format) || bytes > limits.maxMemoryAllocationSize()) throw new IOException("HDR cubemap exceeds GPU limits");
        // PipelineCache owns compiled shaders and handles reload/shutdown. Resolve once, never
        // compile in an animation frame. Failure disables HDR without breaking the resource reload.
        CompiledRenderPipeline pipeline = RenderSystem.getCompiledPipelineNullable(HdrPipeline.PIPELINE);
        if (pipeline == null) throw new IOException("HDR panorama shader could not be compiled");
        GpuPanorama result = new GpuPanorama(pipeline, config);
        try {
            result.texture = device.createTexture("Shimmer HDR panorama", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_CUBEMAP_COMPATIBLE, format, pixels.size(), pixels.size(), 6, 1);
            result.view = device.createTextureView(result.texture);
            result.sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            var encoder = device.createCommandEncoder();
            for (int layer = 0; layer < 6; layer++) encoder.writeToTexture(result.texture, pixels.layer(layer), 0, layer, 0, 0, pixels.size(), pixels.size());
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var data = stack.malloc(16);
                data.putFloat((float) config.exposure()).putFloat(config.toneMapping().equals("ACES") ? 0 : 1).putFloat(0).putFloat(0).flip();
                result.settings = device.createBuffer(() -> "HDR panorama settings", GpuBuffer.USAGE_UNIFORM, data);
                result.settingsSlice = result.settings.slice();
            }
            HdrPanorama.LOGGER.info("Uploaded one {}x{} {} HDR cubemap on {}", pixels.size(), pixels.size(), format, device.getDeviceInfo().backendName());
            return result;
        } catch (Throwable e) { result.close(); throw e; }
    }

    public CompiledRenderPipeline pipeline() { return pipeline; }
    public boolean preserveOverlay() { return config.preserveVanillaOverlay(); }
    public void bind(RenderPass pass, String name) {
        pass.setUniform(name, view, sampler);
        pass.setUniform("HdrSettings", settingsSlice);
    }

    @Override public void close() {
        if (texture != null) HdrPanorama.LOGGER.info("Releasing HDR cubemap");
        if (settings != null) { settings.close(); settings = null; }
        if (sampler != null) { sampler.close(); sampler = null; }
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
    }
}
