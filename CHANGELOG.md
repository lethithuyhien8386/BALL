# Changelog

## v0.5.0 — Trajectory / Bank-shot fix

- Dynamically locate both physical arena walls from screenshot pixel gradients. Fall back to inferred grid edges when the boundary cannot be detected.
- Trace the physical ray against *all* board balls and stop on the nearest object, independently in each reflected segment. Treat white/special balls as blocking objects.
- Reject snap cells on the far side of the first-hit ball or outside a narrow collision tolerance.
- Generate direct and up to three-reflection angles using mirrored virtual targets; improve the best candidates with local angle refinement.
- Score the actual integer screen tap angle; rerank finalists under angle, wall and ball-radius perturbations.
- Score dropped bubbles only if originally connected to ceiling; keep visible-pixel scoring.
- Show `B1`/`B2`/`B3` in floating UI for reflected shots.
- Add physics, geometry and regression tests; bump APK version to 5 / 0.5.0.

## v0.4.0 — Multicolor and continuous-play fix

Recognize purple/blue/special bubbles, self-calibrate grid, keep repeat-shot protection and Astra fallback.
