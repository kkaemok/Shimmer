package org.kkaemok.shimmer;

import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.io.IOException;

/** Linear RGB primaries -> XYZ, Bradford white adaptation -> linear sRGB/Rec.709. */
public final class RgbTransform {
    public static final RgbTransform SRGB = new RgbTransform(new Matrix3d());
    public static final RgbTransform RADIANCE;
    private static final double[] REC709 = {.64, .33, .30, .60, .15, .06, .3127, .3290};
    private static final Matrix3d BRADFORD = new Matrix3d(
            .8951, -.7502, .0389, .2664, 1.7135, -.0685, -.1614, .0367, 1.0296);
    static {
        try { RADIANCE = fromPrimaries(new double[]{.64, .33, .29, .60, .15, .06, 1.0 / 3, 1.0 / 3}); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    private final Matrix3d matrix;
    private RgbTransform(Matrix3d matrix) { this.matrix = matrix; }

    public static RgbTransform parse(String value) throws IOException {
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 8) throw new IOException("HDR PRIMARIES must contain eight xy coordinates");
        double[] xy = new double[8];
        try { for (int i = 0; i < 8; i++) xy[i] = Double.parseDouble(parts[i]); }
        catch (NumberFormatException e) { throw new IOException("Invalid HDR PRIMARIES", e); }
        return fromPrimaries(xy);
    }

    private static RgbTransform fromPrimaries(double[] xy) throws IOException {
        for (int i = 0; i < 8; i += 2) {
            if (!Double.isFinite(xy[i]) || !Double.isFinite(xy[i + 1]) || xy[i] <= 0 || xy[i + 1] <= 0
                    || xy[i] + xy[i + 1] > 1.000001) throw new IOException("Invalid HDR chromaticity");
        }
        boolean srgb = true;
        for (int i = 0; i < 8; i++) srgb &= Math.abs(xy[i] - REC709[i]) < .000001;
        if (srgb) return SRGB;
        Matrix3d source = rgbToXyz(xy);
        Vector3d white = white(xy);
        Vector3d sourceCone = BRADFORD.transform(white, new Vector3d());
        Vector3d targetCone = BRADFORD.transform(white(REC709), new Vector3d());
        if (sourceCone.x <= 0 || sourceCone.y <= 0 || sourceCone.z <= 0) throw new IOException("Unsupported HDR white point");
        Matrix3d adaptation = new Matrix3d(BRADFORD).invert()
                .scale(targetCone.x / sourceCone.x, targetCone.y / sourceCone.y, targetCone.z / sourceCone.z).mul(BRADFORD);
        Matrix3d transform = rgbToXyz(REC709).invert().mul(adaptation).mul(source);
        if (!transform.isFinite()) throw new IOException("Invalid HDR color transform");
        return new RgbTransform(transform);
    }

    private static Matrix3d rgbToXyz(double[] xy) throws IOException {
        Matrix3d basis = new Matrix3d();
        for (int i = 0; i < 3; i++) basis.setColumn(i, new Vector3d(xy[i * 2] / xy[i * 2 + 1], 1, (1 - xy[i * 2] - xy[i * 2 + 1]) / xy[i * 2 + 1]));
        if (Math.abs(basis.determinant()) < 1e-8) throw new IOException("Degenerate HDR PRIMARIES");
        Vector3d scale = new Matrix3d(basis).invert().transform(white(xy));
        if (scale.x <= 0 || scale.y <= 0 || scale.z <= 0) throw new IOException("HDR white point is outside its primaries");
        return basis.scale(scale);
    }

    private static Vector3d white(double[] xy) { return new Vector3d(xy[6] / xy[7], 1, (1 - xy[6] - xy[7]) / xy[7]); }
    public float red(float r, float g, float b) { return (float) (matrix.m00() * r + matrix.m10() * g + matrix.m20() * b); }
    public float green(float r, float g, float b) { return (float) (matrix.m01() * r + matrix.m11() * g + matrix.m21() * b); }
    public float blue(float r, float g, float b) { return (float) (matrix.m02() * r + matrix.m12() * g + matrix.m22() * b); }
}
