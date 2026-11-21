package com.example.bubblepilot;

/**
 * A small, deterministic state machine for continuous play.
 *
 * Never repeat a shot while the captured board has not demonstrably progressed.
 * Wait for two matching valid frames after a visible change before firing
 * again. Time gating is intentionally short; stable screenshots, rather than a
 * long fixed sleep, determine when the next projectile is ready.
 */
public final class ShotGate {
    static final long NONE = Long.MIN_VALUE;
    private long lastFiredSignature = NONE;
    private long lastFiredAt = 0;
    private boolean transitionSeen = false;
    private long stableSignature = NONE;
    private int consecutiveStableFrames = 0;

    public void reset() {
        lastFiredSignature = NONE;
        lastFiredAt = 0;
        transitionSeen = false;
        stableSignature = NONE;
        consecutiveStableFrames = 0;
    }

    /** Called only when the game is visibly changing (not on screenshot errors). */
    public void observedNoBoard() {
        if (lastFiredSignature != NONE) transitionSeen = true;
        stableSignature = NONE;
        consecutiveStableFrames = 0;
    }

    public boolean canShoot(long currentSignature, long elapsedRealtimeMs) {
        if (currentSignature == NONE) return false;
        if (lastFiredSignature != NONE) {
            // Missing/unstable screenshots alone are not proof that a previous
            // tap was accepted by the game. Never double-fire the same board.
            if (currentSignature == lastFiredSignature) return false;
            transitionSeen = true;
            if (elapsedRealtimeMs - lastFiredAt < 120) return false;
        }
        if (stableSignature != currentSignature) {
            stableSignature = currentSignature;
            consecutiveStableFrames = 1;
            return false;
        }
        if (consecutiveStableFrames < 2) consecutiveStableFrames++;
        return consecutiveStableFrames >= 2;
    }

    /** Mark only when Android actually accepted the dispatchGesture request. */
    public void shotDispatched(long signature, long elapsedRealtimeMs) {
        lastFiredSignature = signature;
        lastFiredAt = elapsedRealtimeMs;
        transitionSeen = false;
        stableSignature = NONE;
        consecutiveStableFrames = 0;
    }
}
