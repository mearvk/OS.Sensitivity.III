# JDesk CMD Start Icons — Design Specification

## Posit

The CMD start icon is a **24×24 pixel square** with a 3D semi-gloss finish.

### Structure (outside-in)

| Layer | Description | Width |
|-------|-------------|-------|
| 1 — Border | Deep purple (#2D0A3E) | 2px |
| 2 — Purple band | Medium-rich purple (#6B1FA8 to #8030C0), swirl-capable | 4–6px |
| 3 — Blue band | Transitional blue (#3044B8 to #4466DD), curves into red | ~3px |
| 4 — Red band | Warm red (#C02040 to #E03050), thin outer radius | ~2px |
| 5 — Center | Pinkish square-off (#E878A0 to #F0A0C0), rounded/squared central fill | ~6–8px |

### Characteristics

- **Size**: 24×24 pixels exactly
- **Shape**: Square, no outer radius (hard edge)
- **Finish**: 3D semi-gloss — subtle specular highlight (top-left to center), soft shadow (bottom-right)
- **Bands**: Swirl and curve — not strictly concentric rectangles. Each icon has semi-random organic flow in the band boundaries.
- **Center**: A "square-off" pinkish region — roughly square but with softened/organic edges
- **Variation**: Each of the 100 icons uses a semi-random wash — the bands shift, swirl differently, the pink center drifts slightly, the gloss highlight angle varies. No two are identical.

### Color Palette

```
Deep Purple Border:  #2D0A3E
Purple Band:         #6B1FA8 → #8030C0 (gradient, varies per icon)
Blue Band:           #3044B8 → #4466DD (gradient, varies per icon)
Red Band:            #C02040 → #E03050 (gradient, varies per icon)
Pink Center:         #E878A0 → #F0A0C0 (gradient, varies per icon)
Gloss Highlight:     rgba(255,255,255,0.25) → rgba(255,255,255,0)
Shadow:              rgba(0,0,0,0.15) bottom-right
```

### File Format

- SVG, viewBox="0 0 24 24"
- Named `cmd-icon-001.svg` through `cmd-icon-100.svg`
- Each contains a unique semi-random wash variant

---

*Copyright (C) 2026 MEARVK LLC*
*Author: Maximilian Eric Alexander Rupplin von Keffikon*
