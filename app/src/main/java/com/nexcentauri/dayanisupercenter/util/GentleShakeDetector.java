package com.nexcentauri.dayanisupercenter.util;

/** Two light acceleration pulses; ignores gravity, isolated bumps and repeat triggers. */
public final class GentleShakeDetector {
    private static final float THRESHOLD = 3.8f;
    private float gx, gy, gz;
    private boolean initialized;
    private boolean aboveThreshold;
    private long firstPulse = -1;
    private long lastPulse = -1;
    private long lastTrigger = -5000;

    public boolean sample(float x, float y, float z, long now) {
        if (!initialized) { gx = x; gy = y; gz = z; initialized = true; return false; }
        gx = .8f * gx + .2f * x; gy = .8f * gy + .2f * y; gz = .8f * gz + .2f * z;
        float ax = x - gx, ay = y - gy, az = z - gz;
        float magnitude = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (magnitude < THRESHOLD * .65f) aboveThreshold = false;
        if (magnitude < THRESHOLD || aboveThreshold || now - lastTrigger < 2500) return false;
        aboveThreshold = true;
        if (lastPulse >= 0 && now - lastPulse < 180) return false;
        lastPulse = now;
        if (firstPulse < 0 || now - firstPulse > 1200) { firstPulse = now; return false; }
        firstPulse = -1;
        lastTrigger = now;
        return true;
    }
    public void reset() { initialized = false; aboveThreshold = false; firstPulse = -1; lastPulse = -1; }
}
