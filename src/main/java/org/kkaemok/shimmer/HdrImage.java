package org.kkaemok.shimmer;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/** Owns exactly one STB allocation. No NativeImage, LDR conversion, or global STB settings. */
public final class HdrImage implements AutoCloseable {
    private final int size;
    private FloatBuffer pixels;
    private final float max;
    private final boolean fullPrecision;

    private HdrImage(int size, FloatBuffer pixels, float max, boolean fullPrecision) {
        this.size = size;
        this.pixels = pixels;
        this.max = max;
        this.fullPrecision = fullPrecision;
    }

    public static HdrImage decode(InputStream stream) throws IOException {
        return decode(stream, RgbTransform.SRGB);
    }

    public static HdrImage decode(InputStream stream, RgbTransform untagged) throws IOException {
        // Bounded encoded storage, growing only as needed. No unbounded readAllBytes on pack data.
        ByteBuffer encoded = MemoryUtil.memAlloc(64 * 1024);
        try {
            byte[] chunk = new byte[16 * 1024];
            int n;
            while ((n = stream.read(chunk)) != -1) {
                if ((long) encoded.position() + n > RadianceHeader.MAX_FILE_BYTES) throw new IOException("HDR file exceeds 64 MiB");
                if (encoded.remaining() < n) encoded = MemoryUtil.memRealloc(encoded, Math.min(RadianceHeader.MAX_FILE_BYTES, Math.max(encoded.capacity() * 2, encoded.position() + n)));
                encoded.put(chunk, 0, n);
            }
            encoded.flip();
            RadianceHeader header = RadianceHeader.inspect(encoded, untagged);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1);
                var h = stack.mallocInt(1);
                var channels = stack.mallocInt(1);
                if (!STBImage.stbi_is_hdr_from_memory(encoded)) throw new IOException("STB did not recognize RGBE");
                // Isolate our orientation from other decoders/mods on this worker thread.
                STBImage.stbi_set_flip_vertically_on_load_thread(0);
                FloatBuffer pixels = STBImage.stbi_loadf_from_memory(encoded, w, h, channels, 4);
                if (pixels == null) throw new IOException("STB HDR decoding failed: " + STBImage.stbi_failure_reason());
                try {
                    if (w.get(0) != header.width() || h.get(0) != header.height()
                            || pixels.remaining() != header.width() * header.height() * 4) throw new IOException("STB image dimensions disagree with header");
                    float maximum = 0;
                    boolean fullPrecision = false;
                    var transform = header.colorTransform();
                    for (int i = 0; i < pixels.limit(); i += 4) {
                        float r = pixels.get(i), g = pixels.get(i + 1), b = pixels.get(i + 2);
                        if (transform != RgbTransform.SRGB) {
                            pixels.put(i, transform.red(r, g, b));
                            pixels.put(i + 1, transform.green(r, g, b));
                            pixels.put(i + 2, transform.blue(r, g, b));
                        }
                        for (int c = 0; c < 4; c++) {
                            float value = pixels.get(i + c);
                            if (!Float.isFinite(value)) throw new IOException("Non-finite HDR sample");
                            float magnitude = Math.abs(value);
                            maximum = Math.max(maximum, magnitude);
                            fullPrecision |= magnitude > 65504 || (magnitude > 0 && magnitude < 0x1.0p-24f);
                        }
                    }
                    return new HdrImage(header.width(), pixels, maximum, fullPrecision);
                } catch (Throwable e) {
                    STBImage.stbi_image_free(pixels);
                    throw e;
                }
            }
        } finally { MemoryUtil.memFree(encoded); }
    }

    public int size() { return size; }
    public float max() { return max; }
    public boolean requiresFloat32() { return fullPrecision; }
    public FloatBuffer pixels() { return pixels; }

    @Override public void close() {
        if (pixels != null) {
            STBImage.stbi_image_free(pixels);
            pixels = null;
        }
    }
}
