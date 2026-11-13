# Changelog

## 2.3.0 (Minecraft 26.3)

- New League mode (<kbd>=</kbd>): the overhead camera with League-style controls. Right-click somewhere to walk there (hold it to steer), left-click to mine or hit (out of reach, you walk over first), and Shift + right-click to place blocks or open things. <kbd>R</kbd> switches between three cameras: Follow, Free (push the mouse against a screen edge to pan, middle-click brings it back) and Auto-turn (swings round behind the way you walk). A box in the corner shows which camera is on.
- Fixed the screen bouncing when you held Zoom in third person. Minecraft counts a held key as many presses, which flicked the far view on and off; every Zocular key now reacts once per press.
- The far view now goes to a set Pull-back distance (12 blocks to start, under Camera) and stays there until you press Zoom again. Scroll still moves it in or out.
- The overhead view moved from <kbd>U</kbd> to <kbd>-</kbd>, because Xaero's Minimap uses <kbd>U</kbd> for waypoints. If you were still on <kbd>U</kbd>, it moves over by itself.

## 2.2.0 (Minecraft 26.3)

- New overhead view (<kbd>U</kbd>), like a strategy game: the camera hangs above you at a fixed angle and a mouse cursor appears. Click what the cursor is on to mine, build, open chests or hit mobs, within your normal reach (the block outline shows when you can). <kbd>W</kbd> <kbd>A</kbd> <kbd>S</kbd> <kbd>D</kbd> move you up, left, down and right on the screen, <kbd>[</kbd> and <kbd>]</kbd> turn the camera, and holding Zoom while scrolling raises or lowers it. Height, angle and lens are under Camera > Overhead view. The new Zocular title shows for a moment when it switches on (Show the title turns that off).
- New blue Zocular title and icon, and the settings screen and HUD panels are blue to match.
- Far third-person view is back: in F5, press Zoom (<kbd>C</kbd>) to swing the camera far out behind you, scroll to take it as far as 96 blocks (up to 256 in the settings) or back in, and press Zoom again to come back. You keep playing normally the whole time, and fog is pushed out with the camera so the world doesn't vanish. Turn it off under Camera > Zoom key pulls the camera back.
- Camera paths can now play while you keep playing: turn on Camera Path > Play while it runs, set your keyframes in freecam and press Use. During the shot, your movement, mouse and clicks go to your player, so you can walk, build or fight on camera. Freecam (<kbd>F4</kbd>) stops the shot, and when it ends the camera comes back to you.

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
