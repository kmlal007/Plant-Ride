# Plant-Ride brand

<img src="logo-horizontal.svg" alt="Plant-Ride" width="420">

An original, fictional identity: a route line shaped like a **P**, with a stop at its base and a vehicle on the loop.
It isn't tied to any customer brand, so it can be shown to any plant.

## Files

| File | Use |
|---|---|
| `logo-mark.svg` | App icon, favicon, admin sidebar (employee / admin) |
| `logo-mark-driver.svg` | Driver app icon (orange variant so drivers can tell the apps apart) |
| `logo-horizontal.svg` | Documents and presentations |
| `generate.mjs` | Renders every PNG (app icons, Android adaptive and monochrome icons, splash, favicons) from the SVGs |

Regenerate the PNGs after changing an SVG:

```bash
npm i -D playwright      # in any scratch folder; uses a local Chromium
CHROMIUM_PATH=/path/to/chrome node brand/generate.mjs
```

## Colour

| Token | Hex | Use |
|---|---|---|
| Steel navy | `#0E2A47` | Brand base, headers, sidebar, OTP digits |
| Navy deep | `#0B1F33` | Gradients, splash background |
| Furnace orange | `#F26B1D` | **Decorative only**: logo, active markers, accents on dark backgrounds |
| Accent (burnt orange) | `#C2410C` | Buttons and text on light backgrounds (5.2:1 contrast with white) |
| Success | `#15803D` | Available, completed, accept / start |
| Warning | `#B45309` | Waiting, assigned |
| Danger | `#B91C1C` | Alerts, no-show, decline |
| Info | `#1D4ED8` | Scheduled, shuttles, informational |
| Violet | `#6D28D9` | Approvals, visitors |

Rule: never put white text on `#F26B1D`, because it fails WCAG AA contrast (2.9:1). Use `#C2410C` instead.
Dark themes swap the accent to `#F26B1D` with navy text.

Status colours mean the same thing in every app: a "completed" ride is green on the admin web, the employee app and the
driver app.

## Type and icons

- **Typeface:** the system font stack (Inter if installed). No web-font download, so on-prem installations without
  internet render correctly.
- **Icons:** Lucide on the admin web, Ionicons on mobile; outline style, with filled icons for active states.
- **Number plates:** the driver app shows the vehicle number on a yellow commercial plate, as on Indian taxis, so
  drivers recognise their vehicle at a glance.
