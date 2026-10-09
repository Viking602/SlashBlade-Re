package jp.nyatla.nymmd.types;

/** VMD stores four independent cubic curves on the destination key, in columns. */
public final class VmdBezier {
    public static float evaluate(int[] controls, int channel, float time) {
        if (time <= 0 || time >= 1) return Math.clamp(time, 0, 1);
        float x1 = (controls[channel] & 255) / 127F;
        float y1 = (controls[channel + 4] & 255) / 127F;
        float x2 = (controls[channel + 8] & 255) / 127F;
        float y2 = (controls[channel + 12] & 255) / 127F;
        if (x1 == y1 && x2 == y2) return time;
        // Bracketed inversion stays stable at vertical tangents and flat end points.
        float low = 0, high = 1, t = time;
        for (int i = 0; i < 20; i++) {
            if (cubic(t, x1, x2) < time) low = t; else high = t;
            t = (low + high) * .5F;
        }
        return cubic(t, y1, y2);
    }
    private static float cubic(float t, float a, float b) {
        float s = 1 - t;
        return 3 * s * s * t * a + 3 * s * t * t * b + t * t * t;
    }
    private VmdBezier() {}
}
