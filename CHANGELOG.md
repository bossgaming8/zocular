# Changelog

## 2.1.0 (Minecraft 26.3)

**Cinematic camera**

- A new Auto style directs the scene for you: it tracks alongside you, chases low behind you, leads from the front, sets up roadside cameras you ride past and pulls out for wide shots, cutting between them like a film. Tripod, chase, side and orbit are still there if you want one kind of shot.
- Fixed the cinematic camera shaking: walking bob, hurt tilt and sprint FOV changes no longer reach it, and pulling in from walls is smoothed.
- Cinematic shots have their own lens (field of view) setting.
- Next Shot (<kbd>R</kbd>) always cuts to a different angle.

**HUD**

- Every Zocular HUD element can be dragged to a new spot in the new HUD editor (Interface > Move HUD elements, or `/zocular hud`).
- The zoom level moved to the top right and steps below any potion effect icons.
- The freecam panel shows a Hide button: <kbd>H</kbd> switches it between full and compact, and it can be turned off completely in the settings.
- The camera path progress bar sits above the hotbar.

**Other changes**

- New default keys: Zoom <kbd>C</kbd>, Freecam <kbd>F4</kbd>. If you were still on the 2.0 keys (Z and K), they move over automatically; keys you picked yourself are left alone.
- The settings screen links to the Banana Sandwich website (bananasandwich.us), Discord, the bug tracker and donations.

## 2.0.0 (Minecraft 26.3)

Zocular 2.0 is a complete rewrite. Settings from 1.x are not carried over; the new config lives in `config/zocular.json`.

- New settings screen with pages for zoom, the cinematic camera, freecam, camera paths, third person, the interface and controls. Every option explains itself, and changed values can be reset one at a time or a page at a time.
- Smoother zoom from 1.1× up to a configurable maximum of 100×, with mouse speed that follows the zoom and optional look smoothing.
- Cinematic camera with tripod, chase and orbit shots, letterbox bars, vignette and optional Iris shader switching.
- Freecam that collides with blocks by default, with sprint boost, scroll-wheel speed and an automatic exit when you take damage.
- Camera paths recorded with the mouse in freecam and played back along a smooth spline.
- Third-person distance, shoulder view and camera roll.
