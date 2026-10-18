package org.kkaemok.shimmer;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PanoramaLoaderTest {
    @Test void highestCompletePackWinsWithoutMixingFaces() {
        var low = Pack.complete("low", 2);
        var high = Pack.complete("high", 8);
        try (var pixels = PanoramaLoader.load(new Manager(List.of(low, high)), () -> false)) {
            assertNotNull(pixels);
            assertEquals(8, Float.float16ToFloat(pixels.layer(0).getShort(0)));
            assertEquals(0, low.opened);
            assertEquals(6, high.opened);
            assertEquals(high.opened, high.closed);
        }
        high.data.remove(5);
        try (var pixels = PanoramaLoader.load(new Manager(List.of(low, high)), () -> false)) {
            assertNotNull(pixels);
            for (int layer = 0; layer < 6; layer++) assertEquals(2, Float.float16ToFloat(pixels.layer(layer).getShort(0)));
            assertEquals(6, high.opened); // incomplete pack was not decoded at all
            assertEquals(low.opened, low.closed);
        }
    }

    @Test void missingAndPngOnlyAssetsFallBackToVanilla() {
        assertNull(PanoramaLoader.load(ResourceManager.Empty.INSTANCE, () -> false));
        var pack = Pack.complete("missing", 4);
        pack.data.remove(2);
        assertNull(PanoramaLoader.load(new Manager(List.of(pack)), () -> false));
        assertEquals(0, pack.opened);
    }

    @Test void corruptOrMismatchedHighPrioritySetDoesNotPreventValidLowerSet() {
        var low = Pack.complete("good", 2);
        var high = Pack.complete("bad", 8);
        high.data.put(3, new byte[]{0, 1, 2});
        try (var pixels = PanoramaLoader.load(new Manager(List.of(low, high)), () -> false)) {
            assertNotNull(pixels);
            assertEquals(2, Float.float16ToFloat(pixels.layer(0).getShort(0)));
        }
        assertEquals(high.opened, high.closed);
        high.data.put(3, HdrFixtures.solid(16, 4));
        try (var pixels = PanoramaLoader.load(new Manager(List.of(low, high)), () -> false)) {
            assertNotNull(pixels);
            assertEquals(8, pixels.size());
        }
        assertEquals(high.opened, high.closed);
        assertEquals(low.opened, low.closed);
    }

    @Test void canceledDecodeStopsBetweenFacesAndClosesStreams() {
        var pack = Pack.complete("cancel", 4);
        assertNull(PanoramaLoader.load(new Manager(List.of(pack)), () -> pack.opened > 0));
        assertEquals(1, pack.opened);
        assertEquals(1, pack.closed);
    }

    static final class Pack implements PackResources {
        final String id;
        final Map<Integer, byte[]> data = new HashMap<>();
        int opened, closed;
        Pack(String id) { this.id = id; }
        static Pack complete(String id, float value) {
            Pack pack = new Pack(id);
            for (int i = 0; i < 6; i++) pack.data.put(i, HdrFixtures.solid(8, value));
            return pack;
        }
        @Override public IoSupplier<InputStream> getResource(PackType type, Identifier path) {
            for (var entry : data.entrySet()) {
                if (PanoramaLoader.BASE.withSuffix("_" + entry.getKey() + ".hdr").equals(path)) {
                    return () -> { opened++; return new ByteArrayInputStream(entry.getValue()) { @Override public void close() { closed++; } }; };
                }
            }
            return null;
        }
        @Override public String packId() { return id; }
        @Override public Set<String> getNamespaces(PackType type) { return Set.of("minecraft"); }
        @Override public void listResources(PackType type, String namespace, String prefix, ResourceOutput output) {}
        @Override public PackLocationInfo location() { return null; }
        @Override public IoSupplier<InputStream> getRootResource(String... paths) { return null; }
        @Override public <T> T getMetadataSection(MetadataSectionType<T> type) { return null; }
        @Override public void close() {}
    }

    record Manager(List<PackResources> packs) implements ResourceManager {
        @Override public Stream<PackResources> listPacks() { return packs.stream(); }
        @Override public Set<String> getNamespaces() { return Set.of("minecraft"); }
        @Override public Optional<Resource> getResource(Identifier id) { return Optional.empty(); }
        @Override public List<Resource> getResourceStack(Identifier id) { return List.of(); }
        @Override public Map<Identifier, Resource> listResources(String directory, Selector selector) { return Map.of(); }
        @Override public Map<Identifier, List<Resource>> listResourceStacks(String directory, Selector selector) { return Map.of(); }
    }
}
