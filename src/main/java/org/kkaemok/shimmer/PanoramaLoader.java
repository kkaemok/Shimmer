package org.kkaemok.shimmer;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class PanoramaLoader {
    public static final Identifier BASE = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
    private PanoramaLoader() {}

    public static PanoramaPixels load(ResourceManager manager, BooleanSupplier canceled) {
        return load(manager, canceled, RgbTransform.SRGB);
    }

    public static PanoramaPixels load(ResourceManager manager, BooleanSupplier canceled, RgbTransform untagged) {
        // MultiPackResourceManager.listPacks is low-to-high priority. Read each PackResources
        // directly, never six individually resolved resources that could mix unrelated packs.
        List<PackResources> packs;
        try (var stream = manager.listPacks()) { packs = stream.toList().reversed(); }
        for (PackResources pack : packs) {
            if (canceled.getAsBoolean()) return null;
            List<HdrImage> images = new ArrayList<>(6);
            try {
                boolean complete = true;
                for (int i = 0; i < 6; i++) {
                    if (pack.getResource(PackType.CLIENT_RESOURCES, BASE.withSuffix("_" + i + ".hdr")) == null) {
                        complete = false;
                        break;
                    }
                }
                if (!complete) continue;
                for (int i = 0; i < 6; i++) {
                    if (canceled.getAsBoolean()) return null;
                    var resource = pack.getResource(PackType.CLIENT_RESOURCES, BASE.withSuffix("_" + i + ".hdr"));
                    if (resource == null) throw new java.io.IOException("HDR face disappeared");
                    try (InputStream input = resource.get()) { images.add(HdrImage.decode(input, untagged)); }
                    if (images.getLast().size() != images.getFirst().size()) throw new java.io.IOException("Mismatched HDR face sizes");
                }
                if (canceled.getAsBoolean()) return null;
                PanoramaPixels result = PanoramaPixels.fromImages(images);
                HdrPanorama.LOGGER.info("Loaded {}x{} HDR panorama from {} ({})", result.size(), result.size(), pack.packId(), result.half() ? "RGBA16_FLOAT" : "RGBA32_FLOAT");
                return result;
            } catch (Exception | OutOfMemoryError e) {
                HdrPanorama.LOGGER.warn("Skipping invalid HDR panorama in {}: {}", pack.packId(), e.toString());
            } finally { images.forEach(HdrImage::close); }
        }
        return null;
    }
}
