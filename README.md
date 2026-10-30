# Snabbit — Beyond the booking

A static, single-page case study. The layout and type follow [the Wispr reference](https://wispr-sooty.vercel.app/); all Snabbit story content is drawn from the supplied case-study document.

## Run locally

From this folder, run `npx serve .` and open the printed localhost address. You can also open `index.html` directly, though a local server makes testing and deployment behavior more consistent.

## Deploy

Upload this folder as a static project to Vercel. No build command or framework is required. The output directory is the project root. Keep `index.html`, `styles.css`, `main.js`, and `assets/prototype/` together.

## Replace carousel images

The seven `assets/prototype/screen-XX.png` files are crops from the supplied PDF. Replace them with original screenshots using the same names and order. The carousel remains usable if an image is missing and displays a filename fallback.

## Sources and edits

See `REFERENCE_SPEC.md` for measured reference styles and `CONTENT.md` for Snabbit content mapping. The 8 page sections are Hero, Context, Research, Gap, What I’d Do, Campaign Ideas, Prototype, and Closing.

## Verification

Checked at 1440, 1024, 768, and 390 pixels. The reference and this page use the same measured type scale, colors, document width, inner padding, and component spacing at each breakpoint. The page has 1,036 visible words including labels and carousel captions (about 4.8 minutes at 215 words/minute). Lighthouse desktop scores: Performance 98, Accessibility 91, Best Practices 96, SEO 100. Lighthouse flags some low-contrast small labels inherited from the requested reference palette; the carousel controls have larger hit areas.
