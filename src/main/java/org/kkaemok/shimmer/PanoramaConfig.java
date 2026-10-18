package org.kkaemok.shimmer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public record PanoramaConfig(boolean enabled, double exposure, String toneMapping, boolean preserveVanillaOverlay, String untaggedColorSpace) {
    public static final PanoramaConfig DEFAULT = new PanoramaConfig(true, 0.0, "ACES", true, "LINEAR_SRGB");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public PanoramaConfig {
        if (!Double.isFinite(exposure) || exposure < -20 || exposure > 20) {
            throw new IllegalArgumentException("Exposure must be finite and within -20..20 EV");
        }
        if (!("ACES".equals(toneMapping) || "REINHARD".equals(toneMapping))) {
            throw new IllegalArgumentException("Tone mapping must be ACES or REINHARD");
        }
        if (!("LINEAR_SRGB".equals(untaggedColorSpace) || "RADIANCE".equals(untaggedColorSpace))) {
            throw new IllegalArgumentException("Untagged color space must be LINEAR_SRGB or RADIANCE");
        }
    }

    public void save() throws IOException {
        save(FabricLoader.getInstance().getConfigDir().resolve("shimmer.json"));
    }

    void save(Path path) throws IOException {
        Path target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "shimmer-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(this) + "\n");
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static PanoramaConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve("shimmer.json"));
    }

    static PanoramaConfig load(Path path) {
        try {
            if (!Files.exists(path)) {
                DEFAULT.save(path);
                return DEFAULT;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                // Merge with defaults so partial JSON does not silently disable the mod.
                var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                boolean enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : true;
                double exposure = json.has("exposure") ? json.get("exposure").getAsDouble() : 0.0;
                String curve = json.has("toneMapping") ? json.get("toneMapping").getAsString().toUpperCase(Locale.ROOT) : "ACES";
                boolean overlay = json.has("preserveVanillaOverlay") ? json.get("preserveVanillaOverlay").getAsBoolean() : true;
                String colorSpace = json.has("untaggedColorSpace") ? json.get("untaggedColorSpace").getAsString().toUpperCase(Locale.ROOT) : "LINEAR_SRGB";
                return new PanoramaConfig(enabled, exposure, curve, overlay, colorSpace);
            }
        } catch (IOException | RuntimeException e) {
            HdrPanorama.LOGGER.warn("Cannot read HDR panorama config; using defaults: {}", e.getMessage());
            return DEFAULT;
        }
    }

    public RgbTransform untaggedTransform() { return untaggedColorSpace.equals("RADIANCE") ? RgbTransform.RADIANCE : RgbTransform.SRGB; }
}
