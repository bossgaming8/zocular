<div align="center">

![Zocular](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/zocular-logo.png)

[![Discord](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/badges/discord.png)](https://discord.gg/bananasandwich)&nbsp;&nbsp;
[![Issues](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/badges/issues.png)](https://github.com/bossgaming8/zocular/issues)&nbsp;&nbsp;
[![PayPal](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/badges/paypal.png)](https://www.paypal.com/paypalme/BhoredM)&nbsp;&nbsp;
[![Source](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/badges/source.png)](https://github.com/bossgaming8/zocular)

![A camera path recorded with Zocular](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/showcase/camera-path.webp)

**Smooth zoom, a film-style cinematic camera, freecam and keyframed camera paths. Client-side, and configured from one clean settings screen.**

</div>

## Features

### Zoom
- Hold **Z** to zoom and scroll to go anywhere from 1.1× to 50×.
- Mouse speed follows the zoom, so your aim stays steady at any level.
- Optional look smoothing, hide-hand, and "remember my last zoom".

### Cinematic camera
Press **I** and the camera flies out to frame you like a shot from a film, complete with letterbox bars and a soft vignette.
- **Tripod** holds its frame and only pans once you drift toward the edge. Walk out of range or behind a wall and it glides, or cuts, to a new angle.
- **Chase** follows behind you. **Orbit** slowly circles you.
- **R** cuts to a fresh angle whenever you want one.
- Can switch Iris shaders on or off for the shot and put them back afterwards.

### Freecam
Press **K** to fly the camera anywhere while your character waits where you left it. Blocks stop the camera unless you turn on noclip, and taking damage snaps you straight back.

### Camera paths
In freecam, **left-click** to drop keyframes along a route and **right-click** to play them back as one smooth shot. The camera follows a spline at an even speed with gentle easing, which makes it easy to film build tours and trailers.

### Third person
Adjust the third-person distance, switch to an over-the-shoulder view, and roll the horizon for dutch angles in freecam and cinematic shots.

## Settings

Everything lives in one settings screen: open it from Mod Menu, type `/zocular`, or bind a key to it. Every option explains what it does, and anything you've changed can be reset with one click.

![Zocular settings screen](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/showcase/settings.webp)

## Controls

| Action | Default key |
|---|---|
| Zoom (hold, scroll to adjust) | `Z` |
| Cinematic camera | `I` |
| Next shot | `R` |
| Freecam | `K` |
| Roll left / right / level | `[` / `]` / `\` |
| Switch shoulder | not bound |
| Open settings | not bound, or `/zocular` |

In freecam, **Attack** adds a keyframe, **Use** plays or stops the path, **Pick Block** removes the last keyframe and the scroll wheel sets your flying speed. Every key can be changed in the settings.

## Requirements

- Minecraft **26.3** with Fabric Loader 0.19.5+
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional)

Zocular only runs on your client, so it works on any server. Some servers don't allow freecam, so check the rules first.

---

<div align="center">

## Play on Banana Sandwich SMP

[![Banana Sandwich SMP](https://raw.githubusercontent.com/bossgaming8/zocular/main/docs/assets/banana-sandwich-smp.gif)](https://discord.gg/bananasandwich)

Zocular is made by Bhored from **Banana Sandwich SMP**, a mature (18+), Hermitcraft-inspired vanilla server with proximity voice chat, built around genuine collaboration and fun.

**[Join the Discord](https://discord.gg/bananasandwich)** to hop on.

</div>

---

Found a bug or have an idea? [Open an issue on GitHub](https://github.com/bossgaming8/zocular/issues). If you'd like to support development, you can [donate through PayPal](https://www.paypal.com/paypalme/BhoredM).

<sub>Zocular 2.0 is a from-scratch rewrite. The older 1.x releases were built on Zume by Nolij (OSL-3.0).</sub>
