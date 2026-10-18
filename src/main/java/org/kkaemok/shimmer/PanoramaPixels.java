package org.kkaemok.shimmer;

import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/** CPU upload data. Cubemap layer and row orientation exactly match vanilla CubeMapTexture. */
public final class PanoramaPixels implements AutoCloseable {
    public static final int[] VANILLA_FACE_FOR_LAYER = {1, 3, 5, 4, 0, 2};
    private final ByteBuffer[] layers = new ByteBuffer[6];
    private final int size;
    private final boolean half;

    private PanoramaPixels(int size, boolean half) { this.size = size; this.half = half; }

    public static PanoramaPixels fromImages(List<HdrImage> images) throws IOException {
        if (images.size() != 6) throw new IOException("Six HDR faces are required");
        int size = images.getFirst().size();
        boolean half = true;
        for (HdrImage image : images) {
            if (image.size() != size) throw new IOException("HDR panorama dimensions do not match");
            half &= !image.requiresFloat32();
        }
        PanoramaPixels result = new PanoramaPixels(size, half);
        try {
            for (int layer = 0; layer < 6; layer++) {
                HdrImage image = images.get(VANILLA_FACE_FOR_LAYER[layer]);
                ByteBuffer target = MemoryUtil.memAlloc(Math.multiplyExact(size * size, half ? 8 : 16)).order(ByteOrder.nativeOrder());
                result.layers[layer] = target;
                for (int y = size - 1; y >= 0; y--) {
                    int row = y * size * 4;
                    for (int x = 0; x < size * 4; x++) {
                        float value = image.pixels().get(row + x);
                        if (half) target.putShort(Float.floatToFloat16(value));
                        else target.putFloat(value);
                    }
                }
                target.flip();
                image.close();
            }
            return result;
        } catch (Throwable e) {
            result.close();
            throw e;
        }
    }

    public int size() { return size; }
    public boolean half() { return half; }
    public ByteBuffer layer(int layer) { return layers[layer]; }

    @Override public void close() {
        for (int i = 0; i < 6; i++) {
            if (layers[i] != null) { MemoryUtil.memFree(layers[i]); layers[i] = null; }
        }
    }
}
