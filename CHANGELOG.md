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
- Zoom level indicator with a small meter.

**Cinematic camera**

- A new Auto style directs the scene for you: it tracks alongside you, chases low behind you, leads from the front, sets up roadside cameras you ride past and pulls out for wide shots, cutting between them like a film.
- Tripod, chase, side and orbit styles are there if you want a single kind of shot.
- Steady footage: walking bob, hurt tilt and sprint FOV changes no longer shake the cinematic camera, and it has its own lens setting.
- Next Shot (<kbd>R</kbd>) cuts to a different angle.

**HUD**

- The zoom level sits in the top right and moves below any potion effect icons.
- The freecam panel shows a Hide button; <kbd>H</kbd> switches it between full and compact, and it can be turned off in the settings.
- Every Zocular HUD element can be dragged to a new spot in the HUD editor (`/zocular hud` or Interface > Move HUD elements).

**Freecam and camera paths**

- The camera collides with blocks by default; noclip is optional.
- Scroll to change flying speed, hold Sprint for a boost.
- Freecam closes when you take damage (optional).
- Camera paths are recorded with the mouse in freecam and play back along a centripetal Catmull-Rom spline at constant speed, with easing, looping, letterbox bars and a progress bar.

**Other changes**

- Third-person camera distance and shoulder view.
- Camera roll in freecam and cinematic mode.
- New default keys: Zoom <kbd>C</kbd>, Freecam <kbd>F4</kbd>, Cinematic Camera <kbd>I</kbd> (26.3 uses <kbd>O</kbd> for the Friends list). Zoom In and Zoom Out are unbound; scroll while zooming instead.
- Links to the Banana Sandwich website, Discord, bug tracker and donations from the settings screen.
