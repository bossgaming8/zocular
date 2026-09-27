# Changelog

## 2.0.0 (Minecraft 26.3)

Zocular 2.0 is a complete rewrite. Settings from 1.x are not carried over; the new config lives in `config/zocular.json`.

**New settings screen**

- Pages for zoom, the cinematic camera, freecam, camera paths, third person, the interface and controls.
- Every option explains itself, changed values are marked and can be reset one at a time or a page at a time.
- Key bindings can be changed right on the Controls page.
- Open it from Mod Menu, with `/zocular`, or with the new Open Zocular Settings key.

**Zoom**

- Smoother animation that feels even at every magnification, from 1.1× up to a configurable maximum of 100×.
- Mouse speed follows the zoom, and look smoothing can steady your aim.
- Zoom level indicator with a small meter under the crosshair.

**Cinematic camera**

- Tripod, chase and orbit camera styles.
- The tripod holds its frame and only pans once you drift toward the edge. If you walk away or behind something, it glides or cuts to a new spot.
- Next Shot (<kbd>R</kbd>) cuts to a fresh angle.

**Freecam and camera paths**

- The camera collides with blocks by default; noclip is optional.
- Scroll to change flying speed, hold Sprint for a boost.
- Freecam closes when you take damage (optional).
- Camera paths are recorded with the mouse in freecam and play back along a centripetal Catmull-Rom spline at constant speed, with easing, looping, letterbox bars and a progress bar.

**Other changes**

- Third-person camera distance and shoulder view.
- Camera roll in freecam and cinematic mode.
- Cinematic Camera is now bound to <kbd>I</kbd> by default, because 26.3 uses <kbd>O</kbd> for the Friends list. Zoom In and Zoom Out are unbound; scroll while zooming instead.
