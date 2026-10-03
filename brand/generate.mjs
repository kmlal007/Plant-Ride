// Renders brand PNGs (app icons, adaptive icons, splash, favicons) from the SVG sources.
// Usage: node brand/generate.mjs   (needs the `playwright` package and a Chromium; dev-only tool)
import { chromium } from 'playwright';
import { readFileSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const svg = (name) => readFileSync(join(root, 'brand', name), 'utf8');
const NAVY = '#0E2A47';
const ORANGE = '#C2410C';

/** Strips the rounded background so the mark can be reused on transparent or square canvases. */
const inner = (s) => s.replace(/<svg[^>]*>/, '').replace('</svg>', '');
const markOnly = (s) => inner(s).replace(/<rect[^>]*\/>/, '').replace(/<path d="M60 420[^>]*\/>\s*<path d="M380[^>]*\/>/, '');

function page(body, size, background = 'transparent') {
  return `<html><body style="margin:0;width:${size}px;height:${size}px;background:${background}">${body}</body></html>`;
}

const targets = [];
for (const [app, file, bg] of [['user-app', 'logo-mark.svg', NAVY], ['driver-app', 'logo-mark-driver.svg', ORANGE]]) {
  const s = svg(file);
  const a = join(root, app, 'assets');
  // iOS/store icon: full-bleed square (the OS applies its own mask).
  targets.push([join(a, 'icon.png'), 1024, page(s.replace(/rx="116"/, 'rx="0"').replace('<svg', '<svg width="1024" height="1024"'), 1024)]);
  // Android adaptive icon: mark inside the 66% safe zone on a transparent canvas, plus a solid background.
  targets.push([join(a, 'android-icon-foreground.png'), 1024,
    page(`<svg width="1024" height="1024" viewBox="-34 -40 600 600">${markOnly(s)}</svg>`, 1024)]);
  targets.push([join(a, 'android-icon-background.png'), 1024, page('', 1024, bg)]);
  targets.push([join(a, 'android-icon-monochrome.png'), 1024,
    page(`<svg width="1024" height="1024" viewBox="-34 -40 600 600"><g transform="translate(-14 -24)">
      <path d="M184 396 V164 H292 a86 86 0 0 1 0 172 H236" fill="none" stroke="#fff" stroke-width="52" stroke-linecap="round" stroke-linejoin="round"/>
      <circle cx="184" cy="396" r="34" fill="#fff"/><circle cx="362" cy="250" r="20" fill="#fff"/></g></svg>`, 1024)]);
  targets.push([join(a, 'splash-icon.png'), 1024, page(s.replace('<svg', '<svg width="1024" height="1024"'), 1024)]);
  targets.push([join(a, 'favicon.png'), 64, page(s.replace('<svg', '<svg width="64" height="64"'), 64)]);
}
targets.push([join(root, 'admin-web', 'public', 'apple-touch-icon.png'), 180,
  page(svg('logo-mark.svg').replace(/rx="116"/, 'rx="0"').replace('<svg', '<svg width="180" height="180"'), 180)]);

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || undefined });
for (const [out, size, html] of targets) {
  const p = await browser.newPage({ viewport: { width: size, height: size } });
  await p.setContent(html);
  await p.screenshot({ path: out, omitBackground: true });
  await p.close();
  console.log('wrote', out.replace(root + '/', ''));
}
await browser.close();
