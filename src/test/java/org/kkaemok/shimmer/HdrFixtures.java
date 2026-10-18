package org.kkaemok.shimmer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class HdrFixtures {
    interface Pixel { void sample(int x, int y, float[] rgb); }
    static final String SRGB_HEADER = "PRIMARIES=0.64 0.33 0.30 0.60 0.15 0.06 0.3127 0.3290\n";
    private HdrFixtures() {}
    static byte[] solid(int size, float value) { return image(size, "", (x, y, rgb) -> { rgb[0] = value; rgb[1] = value; rgb[2] = value; }); }
    static byte[] image(int size, String extraHeader, Pixel pixel) {
        var out = new ByteArrayOutputStream();
        try {
            out.write(("#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n" + extraHeader + "\n-Y " + size + " +X " + size + "\n").getBytes(StandardCharsets.US_ASCII));
            float[] rgb = new float[3];
            byte[][] components = new byte[4][size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    pixel.sample(x, y, rgb);
                    float maximum = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
                    int exponent = maximum < 1e-32f ? -128 : Math.getExponent(maximum) + 1;
                    float scale = Math.scalb(256.0f, -exponent);
                    for (int c = 0; c < 3; c++) components[c][x] = (byte) Math.min(255, (int) (rgb[c] * scale));
                    components[3][x] = (byte) (exponent + 128);
                }
                if (size < 8) {
                    for (int x = 0; x < size; x++) for (int c = 0; c < 4; c++) out.write(components[c][x]);
                } else {
                    out.write(new byte[]{2, 2, (byte) (size >> 8), (byte) size});
                    for (byte[] channel : components) {
                        for (int x = 0; x < size;) {
                            int run = 1;
                            while (x + run < size && run < 127 && channel[x + run] == channel[x]) run++;
                            if (run >= 3) { out.write(128 + run); out.write(channel[x]); x += run; }
                            else {
                                int count = Math.min(128, size - x);
                                out.write(count);
                                out.write(channel, x, count);
                                x += count;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) { throw new AssertionError(e); }
        return out.toByteArray();
    }
}
