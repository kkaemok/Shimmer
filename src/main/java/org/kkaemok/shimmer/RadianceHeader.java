package org.kkaemok.shimmer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Bounded preflight before STB allocates native pixels. Also rejects truncated scanlines STB may tolerate. */
public record RadianceHeader(int width, int height, int pixelsOffset, RgbTransform colorTransform) {
    public static final int MAX_EDGE = 2048;
    public static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final Pattern RESOLUTION = Pattern.compile("-Y\\s+(\\d+)\\s+\\+X\\s+(\\d+)");

    public static RadianceHeader inspect(ByteBuffer data) throws IOException {
        return inspect(data, RgbTransform.SRGB);
    }

    public static RadianceHeader inspect(ByteBuffer data, RgbTransform untagged) throws IOException {
        int[] position = {data.position()};
        String magic = line(data, position);
        if (!(magic.equals("#?RADIANCE") || magic.equals("#?RGBE"))) {
            throw new IOException("Not a Radiance RGBE image");
        }
        boolean rgbe = false;
        RgbTransform transform = untagged;
        while (true) {
            String line = line(data, position);
            if (line.isEmpty()) break;
            if (line.startsWith("FORMAT=")) {
                if (rgbe || !line.equals("FORMAT=32-bit_rle_rgbe")) throw new IOException("Unsupported or duplicate Radiance FORMAT");
                rgbe = true;
            }
            if (line.startsWith("PRIMARIES=")) transform = RgbTransform.parse(line.substring(10));
            if (line.startsWith("PIXASPECT=")) {
                try {
                    double aspect = Double.parseDouble(line.substring(10));
                    if (!Double.isFinite(aspect) || Math.abs(aspect - 1) > .00001) throw new IOException("Cubemap pixels must be square");
                } catch (NumberFormatException e) { throw new IOException("Invalid PIXASPECT", e); }
            }
        }
        if (!rgbe) throw new IOException("Missing RGBE FORMAT header");
        var resolution = RESOLUTION.matcher(line(data, position));
        if (!resolution.matches()) throw new IOException("STB requires Radiance orientation -Y height +X width");
        int height;
        int width;
        try {
            height = Integer.parseInt(resolution.group(1));
            width = Integer.parseInt(resolution.group(2));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Radiance dimensions", e);
        }
        if (width < 1 || width != height || width > MAX_EDGE) {
            throw new IOException("HDR faces must be square, with edges 1.." + MAX_EDGE + ": " + width + "x" + height);
        }
        RadianceHeader header = new RadianceHeader(width, height, position[0], transform);
        header.validatePixels(data);
        return header;
    }

    private static String line(ByteBuffer data, int[] cursor) throws IOException {
        int start = cursor[0];
        int end = start;
        while (end < data.limit() && data.get(end) != '\n' && end < 16384) end++;
        if (end == data.limit() || end >= 16384) throw new IOException("Truncated or oversized HDR header");
        byte[] bytes = new byte[end - start];
        for (int i = 0; i < bytes.length; i++) bytes[i] = data.get(start + i);
        cursor[0] = end + 1;
        return new String(bytes, StandardCharsets.US_ASCII).stripTrailing();
    }

    private void validatePixels(ByteBuffer data) throws IOException {
        int p = pixelsOffset;
        for (int y = 0; y < height; y++) {
            need(data, p, 4);
            if (width < 8 || u(data, p) != 2 || u(data, p + 1) != 2 || (u(data, p + 2) & 128) != 0) {
                if (y != 0) throw new IOException("Cannot mix flat RGBE and RLE scanlines");
                // STB supports flat RGBE, but not the obsolete 1,1,1,count repeat encoding.
                int count = width * (height - y);
                need(data, p, count * 4);
                for (int i = 0; i < count; i++, p += 4) {
                    if (u(data, p) == 1 && u(data, p + 1) == 1 && u(data, p + 2) == 1) {
                        throw new IOException("Obsolete Radiance repeat encoding is unsupported by STB");
                    }
                }
                return;
            }
            if ((u(data, p + 2) << 8 | u(data, p + 3)) != width) throw new IOException("RLE scanline width mismatch");
            p += 4;
            for (int channel = 0; channel < 4; channel++) {
                int x = 0;
                while (x < width) {
                    need(data, p, 1);
                    int code = u(data, p++);
                    int count = code > 128 ? code - 128 : code;
                    if (count == 0 || count > width - x) throw new IOException("Invalid HDR RLE run");
                    int bytes = code > 128 ? 1 : count;
                    need(data, p, bytes);
                    p += bytes;
                    x += count;
                }
            }
        }
    }

    private static int u(ByteBuffer data, int i) { return Byte.toUnsignedInt(data.get(i)); }
    private static void need(ByteBuffer data, int offset, int length) throws IOException {
        if (offset < 0 || length < 0 || (long) offset + length > data.limit()) throw new IOException("Truncated HDR pixels");
    }

}
