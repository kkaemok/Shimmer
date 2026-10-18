package org.kkaemok.shimmer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.stb.STBImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HdrDecodingTest {
    static HdrImage decode(byte[] data) throws IOException { return HdrImage.decode(new ByteArrayInputStream(data)); }

    @ParameterizedTest @ValueSource(ints = {2, 8, 512, 1024, 1080, 2048})
    void flatAndRlePreserveSceneLinearValuesAndOpaqueAlpha(int size) throws IOException {
        try (var image = decode(HdrFixtures.solid(size, 8))) {
            assertEquals(size, image.size());
            assertEquals(8, image.pixels().get(0));
            assertEquals(8, image.pixels().get(image.pixels().limit() - 2));
            assertEquals(1, image.pixels().get(3));
            assertEquals(8, image.max());
        }
    }

    @Test void decodingIsIndependentOfThreadLocalStbFlip() throws IOException {
        STBImage.stbi_set_flip_vertically_on_load_thread(1);
        byte[] bytes = HdrFixtures.image(8, "", (x, y, rgb) -> Arrays.fill(rgb, y + 1));
        try (var image = decode(bytes)) {
            assertEquals(1, image.pixels().get(0));
            assertEquals(8, image.pixels().get(8 * 7 * 4));
        }
    }

    @Test void differentHdrHighlightsSurviveBeforeToneMapping() throws IOException {
        byte[] bytes = HdrFixtures.image(8, "", (x, y, rgb) -> Arrays.fill(rgb, 1 << x));
        try (var image = decode(bytes)) {
            for (int x = 0; x < 8; x++) assertEquals(1 << x, image.pixels().get(x * 4));
        }
    }

    @Test void halfPackingUsesVanillaLayerOrderAndVerticalFlip() throws IOException {
        var images = new ArrayList<HdrImage>();
        try {
            for (int face = 0; face < 6; face++) {
                int f = face;
                images.add(decode(HdrFixtures.image(8, "", (x, y, rgb) -> Arrays.fill(rgb, f * 16 + y + 1))));
            }
            try (var pixels = PanoramaPixels.fromImages(images)) {
                assertTrue(pixels.half());
                int[] order = {1, 3, 5, 4, 0, 2};
                for (int layer = 0; layer < 6; layer++) {
                    assertEquals(order[layer] * 16 + 8, Float.float16ToFloat(pixels.layer(layer).getShort(0)));
                    assertEquals(order[layer] * 16 + 1, Float.float16ToFloat(pixels.layer(layer).getShort(8 * 7 * 8)));
                    assertEquals(1, Float.float16ToFloat(pixels.layer(layer).getShort(6)));
                }
                pixels.close();
                assertNull(pixels.layer(0));
            }
        } finally { images.forEach(HdrImage::close); }
    }

    @ParameterizedTest @ValueSource(floats = {131072f, 0x1.0p-30f})
    void valuesOutsideHalfRangeSelectFloat32(float value) throws IOException {
        var images = new ArrayList<HdrImage>();
        try {
            for (int i = 0; i < 6; i++) images.add(decode(HdrFixtures.solid(8, value)));
            try (var pixels = PanoramaPixels.fromImages(images)) {
                assertFalse(pixels.half());
                assertEquals(value, pixels.layer(0).getFloat(0));
            }
        } finally { images.forEach(HdrImage::close); }
    }

    @Test void mismatchedOrIncompleteSetsCannotBePacked() throws IOException {
        var images = new ArrayList<HdrImage>();
        try {
            for (int i = 0; i < 5; i++) images.add(decode(HdrFixtures.solid(8, 4)));
            assertThrows(IOException.class, () -> PanoramaPixels.fromImages(images));
            images.add(decode(HdrFixtures.solid(16, 4)));
            assertThrows(IOException.class, () -> PanoramaPixels.fromImages(images));
        } finally { images.forEach(HdrImage::close); }
    }

    @Test void corruptInputsAreRejectedBeforeStbAllocation() {
        assertThrows(IOException.class, () -> decode(new byte[]{1, 2, 3}));
        byte[] valid = HdrFixtures.solid(8, 4);
        assertThrows(IOException.class, () -> decode(Arrays.copyOf(valid, valid.length - 1)));
        int offset;
        try { offset = RadianceHeader.inspect(ByteBuffer.wrap(valid)).pixelsOffset(); }
        catch (IOException e) { throw new AssertionError(e); }
        byte[] zeroRun = valid.clone(); zeroRun[offset + 4] = 0;
        assertThrows(IOException.class, () -> decode(zeroRun));
        byte[] overRun = valid.clone(); overRun[offset + 4] = (byte) 255;
        assertThrows(IOException.class, () -> decode(overRun));
        byte[] wrongWidth = valid.clone(); wrongWidth[offset + 3] = 7;
        assertThrows(IOException.class, () -> decode(wrongWidth));
        for (String dimensions : new String[]{"-Y 999999999999 +X 8", "-Y 8 +X 9", "-Y 4096 +X 4096", "+Y 8 +X 8", "-Y 0 +X 0"}) {
            byte[] invalid = ("#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n" + dimensions + "\n").getBytes(StandardCharsets.US_ASCII);
            assertThrows(IOException.class, () -> decode(invalid));
        }
        assertThrows(IOException.class, () -> decode("#?RADIANCE\nFORMAT=32-bit_rle_xyze\n\n-Y 8 +X 8\n".getBytes(StandardCharsets.US_ASCII)));
        assertThrows(IOException.class, () -> decode(HdrFixtures.image(8, "PIXASPECT=2\n", (x, y, rgb) -> Arrays.fill(rgb, 1))));
    }

    @Test void closeIsIdempotent() throws IOException {
        var image = decode(HdrFixtures.solid(8, 2));
        image.close(); image.close();
        assertNull(image.pixels());
    }

    @Test void declaredPrimariesAreConvertedInLinearLight() throws IOException {
        try (var srgb = decode(HdrFixtures.image(8, HdrFixtures.SRGB_HEADER, (x, y, rgb) -> { rgb[0] = 4; rgb[1] = 2; rgb[2] = 1; }))) {
            assertEquals(4, srgb.pixels().get(0));
        }
        var radiance = RgbTransform.RADIANCE;
        assertEquals(1, radiance.red(1, 1, 1), .00001);
        assertEquals(1, radiance.green(1, 1, 1), .00001);
        assertEquals(1, radiance.blue(1, 1, 1), .00001);
        try (var tagged = decode(HdrFixtures.image(8, "PRIMARIES=0.64 0.33 0.29 0.60 0.15 0.06 0.3333333333 0.3333333333\n", (x, y, rgb) -> { rgb[0] = 4; rgb[1] = 2; rgb[2] = 1; }))) {
            assertEquals(radiance.red(4, 2, 1), tagged.pixels().get(0), .00001);
            assertEquals(radiance.green(4, 2, 1), tagged.pixels().get(1), .00001);
            assertEquals(radiance.blue(4, 2, 1), tagged.pixels().get(2), .00001);
        }
        assertThrows(IOException.class, () -> RgbTransform.parse("NaN 0.3 0.3 0.6 0.15 0.06 0.3 0.3"));
        assertThrows(IOException.class, () -> RgbTransform.parse("0.3 0.3 0.3 0.3 0.3 0.3 0.3 0.3"));
    }
}
