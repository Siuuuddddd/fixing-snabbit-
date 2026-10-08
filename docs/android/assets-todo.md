# Android asset backlog

No AI-generated or copied worker photography has been added to the repository. The foundation uses neutral placeholder artwork until rights-cleared production assets are supplied.

| Asset | Needed format | Notes |
|---|---|---|
| Snabbit launcher icon | Adaptive foreground/background vector or high-resolution PNG | Recreate from approved brand source; do not crop the screenshot logo |
| Splash wordmark | Vector drawable/SVG source | Pink background `#F80E78`, accessible reduced-motion animation |
| Onboarding illustrations (3) | SVG/vector or WebP | Book quickly, verified workers, safe payments |
| Home service categories | Consistent WebP set | Cleaning, dusting, bathroom, laundry, dishes, more |
| Worker portraits | Rights-cleared WebP/AVIF | Multiple aspect ratios; avoid embedding names in pixels |
| Map styling and markers | JSON plus vectors | Requires approved Maps provider/project key |
| Empty/error/offline illustrations | SVG/vector | Friendly, small, reusable |
| Plus membership artwork | SVG/vector | Avoid text baked into the image |
| Notification/status icons | Vector drawables | Confirmed, pending, cancelled, refund, location |

For every asset record its owner, license, source file, export settings, and accessibility description. Keep large originals outside the APK and check compressed output into the appropriate density/resource directory.

## Home redesign assets

- Instant-card worker mascot: supply an approved transparent SVG/WebP sized for the card edge; the app currently uses the named `Worker mascot placeholder` Material icon and must not reconstruct the reference artwork.
- Favourite-expert avatars: supply three rights-cleared square portraits with focal-point metadata; the app currently uses named overlapping avatar placeholders, with expert names kept as live accessible text when an API exposes them.
- Habit-pack ticket: supply approved transparent SVG/vector art without baked-in visit count, prices, or offer copy; the app currently uses the named `Habit pack ticket artwork placeholder` Material icon.
