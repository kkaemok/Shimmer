package org.kkaemok.shimmer.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.Identifier;
import org.kkaemok.shimmer.HdrPanorama;
import org.kkaemok.shimmer.PanoramaLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CubeMap.class)
abstract class CubeMapMixin {
    @Shadow @Final private Identifier location;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;getCompiledPipeline(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;)Lcom/mojang/renderpearl/api/pipeline/CompiledRenderPipeline;"))
    private CompiledRenderPipeline hdr$pipeline(RenderPipeline vanilla) {
        var hdr = HdrPanorama.INSTANCE.active();
        return hdr != null && location.equals(PanoramaLoader.BASE) ? hdr.pipeline() : RenderSystem.getCompiledPipeline(vanilla);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;setUniform(Ljava/lang/String;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;)V"))
    private void hdr$texture(RenderPass pass, String name, GpuTextureView view, GpuSampler sampler) {
        var hdr = HdrPanorama.INSTANCE.active();
        if (hdr != null && location.equals(PanoramaLoader.BASE)) hdr.bind(pass, name);
        else pass.setUniform(name, view, sampler);
    }
}
