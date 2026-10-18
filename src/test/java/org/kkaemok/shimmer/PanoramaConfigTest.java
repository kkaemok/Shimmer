package org.kkaemok.shimmer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PanoramaConfigTest {
    @TempDir Path directory;

    @Test void missingConfigIsCreatedWithWorkingDefaults() throws Exception {
        Path path = directory.resolve("shimmer.json");
        assertEquals(PanoramaConfig.DEFAULT, PanoramaConfig.load(path));
        assertTrue(Files.readString(path).contains("LINEAR_SRGB"));
        assertEquals(PanoramaConfig.DEFAULT, PanoramaConfig.load(path));
    }

    @Test void partialConfigRetainsDefaultsAndEvConvention() throws Exception {
        Path path = directory.resolve("shimmer.json");
        Files.writeString(path, "{\"exposure\":-2,\"toneMapping\":\"reinhard\",\"preserveVanillaOverlay\":false}");
        var config = PanoramaConfig.load(path);
        assertTrue(config.enabled());
        assertEquals(-2, config.exposure());
        assertEquals("REINHARD", config.toneMapping());
        assertFalse(config.preserveVanillaOverlay());
        assertSame(RgbTransform.SRGB, config.untaggedTransform());
    }

    @Test void invalidSettingsCannotPoisonShaderUniforms() throws Exception {
        Path path = directory.resolve("shimmer.json");
        for (String json : new String[]{"broken", "null", "{\"exposure\":1e300}", "{\"exposure\":\"NaN\"}", "{\"toneMapping\":\"INVALID\"}", "{\"untaggedColorSpace\":\"XYZ\"}"}) {
            Files.writeString(path, json);
            assertEquals(PanoramaConfig.DEFAULT, PanoramaConfig.load(path));
            assertEquals(json, Files.readString(path));
        }
    }

    @Test void savingRoundTripsEveryManualSettingAndReplacesExistingConfig() throws Exception {
        Path path = directory.resolve("nested/shimmer.json");
        PanoramaConfig.DEFAULT.save(path);
        var edited = new PanoramaConfig(false, -4.5, "REINHARD", false, "RADIANCE");
        edited.save(path);
        assertEquals(edited, PanoramaConfig.load(path));
        try (var files = Files.list(path.getParent())) {
            assertEquals(java.util.List.of(path), files.toList());
        }
    }

    @Test void failedReplacementKeepsExistingTargetAndCleansTemporaryFile() throws Exception {
        Path path = Files.createDirectory(directory.resolve("shimmer.json"));
        Path marker = path.resolve("keep.txt");
        Files.writeString(marker, "keep");
        assertThrows(java.io.IOException.class, () -> PanoramaConfig.DEFAULT.save(path));
        assertEquals("keep", Files.readString(marker));
        try (var files = Files.list(directory)) {
            assertEquals(java.util.List.of(path), files.toList());
        }
    }

    @Test void invalidRecordsCannotBeSaved() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -20.1, 20.1}) {
            assertThrows(IllegalArgumentException.class, () -> new PanoramaConfig(true, value, "ACES", true, "LINEAR_SRGB"));
        }
        assertThrows(IllegalArgumentException.class, () -> new PanoramaConfig(true, 0, "INVALID", true, "LINEAR_SRGB"));
        assertThrows(IllegalArgumentException.class, () -> new PanoramaConfig(true, 0, "ACES", true, null));
    }
}
