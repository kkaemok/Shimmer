package org.kkaemok.shimmer;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Original synthetic HDR assets only; no Mojang/Bedrock art and no PNG conversion. */
public final class GenerateTestPack {
    private GenerateTestPack() {}
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path textures = root.resolve("assets/minecraft/textures/gui/title/background");
        Files.createDirectories(textures);
        Files.writeString(root.resolve("pack.mcmeta"), """
                {"pack":{"description":"Shimmer: original directional RGBE and HDR highlight test","min_format":[97,1],"max_format":[97,1]}}
                """);
        int size = args.length > 2 ? Integer.parseInt(args[2]) : 512;
        boolean float32 = args.length > 1 && args[1].equals("float32");
        float[] highlightSteps = {.25f, 1, 4, 16, 64};
        String[] directions = {"0 FRONT", "1 RIGHT", "2 BACK", "3 LEFT", "4 TOP", "5 BOTTOM"};
        for (int face = 0; face < 6; face++) {
            var mask = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);
            var graphics = mask.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 48));
            graphics.drawString(directions[face], 24, 245);
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
            graphics.drawString("UP ^", 210, 65);
            graphics.drawString("LEFT", 20, 150);
            graphics.drawString("RIGHT", 390, 150);
            graphics.drawString("0.25  1   4   16  64", 70, 420);
            graphics.dispose();
            int f = face;
            byte[] hdr = HdrFixtures.image(size, HdrFixtures.SRGB_HEADER, (x, y, rgb) -> {
                // Seam-continuous direction gradient except intentionally labeled areas.
                double s = (x + .5) / size * 2 - 1;
                double t = (y + .5) / size * 2 - 1;
                double dx, dy, dz;
                switch (f) {
                    case 0 -> { dx = s; dy = t; dz = 1; }
                    case 1 -> { dx = 1; dy = t; dz = -s; }
                    case 2 -> { dx = -s; dy = t; dz = -1; }
                    case 3 -> { dx = -1; dy = t; dz = s; }
                    case 4 -> { dx = s; dy = -1; dz = -t; }
                    default -> { dx = s; dy = 1; dz = t; }
                }
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                rgb[0] = (float) (.08 + .65 * (dx / length + 1));
                rgb[1] = (float) (.08 + .65 * (dy / length + 1));
                rgb[2] = (float) (.08 + .65 * (dz / length + 1));
                if (y >= 320 && y < 385 && x >= 50 && x < 450) {
                    float value = highlightSteps[(x - 50) / 80];
                    rgb[0] = value; rgb[1] = value; rgb[2] = value;
                }
                if (mask.getRaster().getSample(x, y, 0) != 0) { rgb[0] = .015f; rgb[1] = .015f; rgb[2] = .015f; }
                if (float32 && x == 0 && y == 0) { rgb[0] = 131072; rgb[1] = 0x1.0p-30f; rgb[2] = 0; }
            });
            Files.write(textures.resolve("panorama_" + face + ".hdr"), hdr);
        }
        System.out.println("Generated six original " + size + "x" + size + " RGBE faces in " + root);
    }
}
