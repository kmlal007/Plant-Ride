# Partner (co-brand) logo: SRM Eco Tech

Plant-Ride is presented as **"Plant-Ride by SRM Eco Tech"**. The Plant-Ride mark stays on app icons; the SRM Eco Tech
logo appears on login screens, the admin sidebar footer and the loading screens.

Until the official artwork is added, the apps show the text credit "by SRM ECO TECH".

## Adding the logo (one-time)

Get the official file from SRM Eco Tech (SVG preferred, otherwise PNG ≥ 1024 px wide, transparent background).
Use a version that reads on **dark** backgrounds (white or light logo), because the login and loading screens are navy.

| Copy the file to | Used by |
|---|---|
| `admin-web/public/partner-logo.png` (or `.svg`, then set `VITE_PARTNER_LOGO=/partner-logo.svg`) | Admin web; picked up automatically, no code change |
| `user-app/assets/partner-logo.png` and `driver-app/assets/partner-logo.png` | Mobile apps; then set `PARTNER_LOGO` in `src/brand/partner.ts` of each app to `require('../../assets/partner-logo.png')` |

The partner name shown in text is configured in the same places (`VITE_PARTNER_NAME`, `PARTNER_NAME`).
