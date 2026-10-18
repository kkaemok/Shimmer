package org.kkaemok.shimmer.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.kkaemok.shimmer.HdrPanorama;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow @Final private ReloadableResourceManager resourceManager;

    // Before the first reload, after vanilla listeners (especially ShaderManager) are installed.
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;registerPanoramaTextures(Lnet/minecraft/client/renderer/texture/TextureManager;)V"))
    private void hdr$register(CallbackInfo ci) { resourceManager.registerReloadListener(HdrPanorama.INSTANCE); }

    @Inject(method = "close", at = @At("HEAD"))
    private void hdr$close(CallbackInfo ci) { HdrPanorama.INSTANCE.close(); }
}
