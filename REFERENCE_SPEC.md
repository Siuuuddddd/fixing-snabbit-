# Wispr page reference measurements

Source: https://wispr-sooty.vercel.app/ inspected in Chrome on 31 October 2026. Values below come from `getComputedStyle` and the page's own inline stylesheet at a 1041 × 911 viewport; they supersede the pasted brief's visual guesses.

## Canvas and type

| Element | Family | Size / weight / line | Color | Spacing / other |
|---|---|---|---|---|
| Body | Inter | 16px / 400 / normal | black | Background `#e8e2d4`, padding `40px 20px 80px` |
| Document | Inter | inherited | — | max-width 720px, background `#fdf9f0`, radius 4px, shadow `0 2px 8px rgba(0,0,0,.12), 0 0 0 1px rgba(0,0,0,.06)` |
| Window chrome | Inter | inherited | — | background `#f5f0e4`, bottom border 1px `#e8e2d4`, padding `12px 20px 10px`, 8px gap |
| Inner document | Inter | inherited | — | padding `48px 64px 80px`, content width 592px |
| Eyebrow/date | Inter | 12px / 400 / normal | `#9a9280` | bottom margin 4px, tracking .03em |
| H1 | Newsreader | 38px / 400 / 43.7px | `#1c1a14` | tracking −.01em, bottom margin 6px |
| H1 emphasis | Newsreader italic | 38px / 400 / 43.7px | `#d4500a` | inherits tracking |
| Subtitle | Inter | 15px / 300 / 24px | `#5a5648` | bottom margin 40px |
| Section label | Inter | 11px / 500 / normal | `#d4500a` | uppercase, tracking .12em, bottom margin 4px |
| H2 | Newsreader | 24px / 400 / 31.2px | `#1c1a14` | bottom margin 16px |
| Intro | Newsreader italic | 13px / 300 / ~24px | `#9a9280` | margins −8px 0 18px |
| Paragraph | Inter | 15px / 300 / 1.85 | `#1c1a14` | bottom margin 18px |
| Section | — | — | — | bottom margin 56px |

## Components

| Component | Measured style |
|---|---|
| Findings grid | 2 columns, 12px gap, 24px vertical margins |
| Finding card | `#f5f0e4`, 1px `#e8e2d4` border, 6px radius, 16px padding |
| Finding keyword | Newsreader italic 28px, line-height 1, accent `#d4500a` |
| Finding label / description | Inter 12px/500 and 12px/300, description line-height 1.6 |
| Callout | 2px left accent border, `#fdf0e8`, padding 14px 18px, 20px vertical margins, 4px right radius. Blue variant: `#2a5fc4` / `#eef2fc` |
| Insight box | `#f5f0e4`, 1px border and 2px blue left border, padding 14px 18px |
| Priority block | 1px border and 3px left accent border, padding 20px 22px, 16px bottom margin. Blue/amber left variants |
| Priority tag | Inter 10px/500, uppercase, tracking .1em |
| Priority heading | Newsreader 18px/400, line-height 1.3, bottom margin 12px |
| Campaign card | `#1c1a14`, 6px radius, 24px padding, 12px bottom margin |
| Campaign label | Inter 10px/500, uppercase, tracking .12em, accent |
| Campaign heading / body | Newsreader 20px/400, `#fdf9f0`; Inter 14px, `#c4bcaa`, line-height 1.7 |
| Closing block | `#1c1a14`, radius 6px, padding 36px, centered. Heading Newsreader 26px/400, accent italic `#e8804a` |

Chrome dots are 12px circles in `#ff5f57`, `#ffbd2e`, and `#28c840`. Title-bar text is Inter 13px/500, `#5a5648`.

## Responsive and behavior

The source has one authored breakpoint, `max-width:600px`: inner padding becomes `36px 28px 60px`, H1 becomes 28px, and findings grid becomes one column. The 720px canvas naturally shrinks at 1024, 768, and 390 viewport widths. There is no sticky nav, progress indicator, hover or focus rule, transition, scroll reveal, or animation in the authored reference CSS. The Snabbit page adds interaction only for its requested prototype carousel.

The reference head has UTF-8, viewport meta, title `Wispr Flow — India`, and Google Fonts for Newsreader weights 300/400/500 plus Inter weights 300/400/500. A second Mulish link appears in the observed DOM, but is not used by any reference component; it appears injected by the browser and is not loaded here. The reference has no favicon or Open Graph meta. The Snabbit page adds a description, Open Graph tags, and theme color for sharing; these do not affect layout.
