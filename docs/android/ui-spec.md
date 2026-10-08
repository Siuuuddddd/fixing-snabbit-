# Snabbit Android UI specification

## Source of truth

The implementation follows `reference/screens/design-board.png`. It is a visual reference, not a source of literal dimensions for every device. Compose layouts must be responsive and inset-aware.

## Target frame

| Property | Value |
|---|---|
| Reference device | Google Pixel 9 Pro |
| Reference pixels | 1280 × 2856 |
| Aspect ratio | Approximately 20:9 |
| Navigation | Edge-to-edge with system inset handling |
| Screen consistency | Every design frame uses the same device frame; long content scrolls inside it |

## Design tokens

| Token | Value | Purpose |
|---|---:|---|
| Primary | `#F80E78` | Main actions, selected navigation, brand accents |
| Saffron | `#F8B020` | Offers and warm highlights |
| Mint | `#E6F7F1` | Calm supporting surfaces |
| Peach | `#FFF1C5` | Promotional/supporting cards |
| Warm white | `#FFFBFC` | Light background |
| Charcoal | `#181B1F` | Primary text |
| Secondary | `#7A7A83` | Supporting text |
| Confirmed | `#0F9D74` | Successful/confirmed status |
| Pending | `#FFB020` | Pending status |
| Error | `#EF4444` | Failed/cancelled/destructive status |
| In progress | `#3882F6` | Active work status |

Spacing uses 4, 8, 12, 16, 24, and 32 dp. Cards use 16 dp corners; prominent surfaces use 20 dp. Minimum touch targets are 48 dp.

Typography is routed through the single `SnabbitFontFamily` theme switch for both flavors. Google Sans is not distributed in the open Google Fonts catalogue, so the current legally bundled fallback is Android system sans (Roboto on supported devices); licensed brand font resources can replace that one switch later. The theme exposes Regular, Medium, SemiBold, and Bold weights. Schedule page titles use the named 22sp SemiBold style and section headings use the named 16sp SemiBold style. Dynamic type is supported; layouts must not depend on a fixed line count, and RTL layout remains supported.

## Shared components

The foundation supplies `BrandLogo`, `SnabbitButton`, `SnabbitCard`, `InfoChip`, `StatusBadge`, `PlaceholderArtwork`, and role-aware bottom navigation. Real screens should extend these primitives rather than duplicate style values.

## Navigation model

The app uses AndroidX Navigation 3 with serializable typed keys. Koin provides the entries and ViewModels. Customer and worker product flavors select separate shells.

Each bottom tab owns a separate `NavBackStack`, so changing tabs preserves that tab's history. Selecting a different tab changes the active stack instead of pushing a destination onto the current tab. Android back pops only the active stack.

Customer tabs: Home, Bookings, Wallet, Plus, Profile. Worker tabs: Jobs, Schedule, Earnings, Support, Profile.

All 23 board destinations are registered as typed routes. Implementation status is tracked in `PROGRESS.md`; routes not yet completed still render a clearly labelled foundation placeholder.

## Customer Schedule flow

Schedule is two typed Navigation 3 pages with independently scoped ViewModels. Page 1 contains only the date and duration choices. Today, Tomorrow, and Multi-day are mutually exclusive; Multi-day accepts any 2–7 distinct choices from the next seven UI dates and requires an explicit Done/Apply action. A confirmed date selection plus duration automatically pushes Page 2 exactly once. Returning with Back retains Page 1 state, while changing a date or duration invalidates the previous start-time selection.

Page 2 repeats the Schedule title, shows a compact date/duration summary, Morning/Afternoon/Evening tabs, the real start-time grid, the Habit Pack strip, and a bottom price/Continue action leading to the existing summary/payment flow. The flow follows the backend’s actual 1–4 whole-hour duration and 8 AM–7 PM operating contracts. Visible day-part slots use 15-minute increments and real availability reads; past Today candidates are hidden, sold-out candidates remain visible and disabled, and the selected backend price drives the bottom total. Large text changes the time grid from four to two columns.

## Required screen states

Every real screen must define loading, content, empty, recoverable error, offline, and retry behavior. Money and booking screens also need pending/idempotent, success, rejected, expired, and unknown-provider states. Live tracking needs stale-location and stream-reconnect states. OTP needs resend cooldown and generic unknown-account behavior.

## Accessibility and localization

Strings and content descriptions use Android resources. Color is never the only status cue. Components must work with TalkBack, font scaling, RTL, keyboard navigation, and reduced motion. Currency is represented from backend minor units and formatted for the active locale; calculations never use floating point.
