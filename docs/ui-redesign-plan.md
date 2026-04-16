# TrackRecord UI Redesign Plan

## Product Direction

TrackRecord should feel like a calm, premium, trustworthy mobility journal instead of a developer utility panel.

The visual language should be:

- composed, not crowded
- light and editorial, not dashboard-heavy
- map-first, but still legible at a glance
- action-oriented, with one dominant CTA per screen

## Current UI Problems

### Homepage

- The main panel still carries too many semantic layers at once: metric, state, CTA, diagnostics, hint text, and navigation all compete for attention.
- The status treatment is still stronger than it needs to be, which makes the page feel operational rather than product-like.
- The map and panel composition should feel more deliberate: the map is the atmosphere, the panel is the decision surface.

### History

- The screen is structurally sound after the Compose migration, but it still needs polish through spacing, typography rhythm, and card density.
- Summary controls and metric grouping should feel less like admin chips and more like travel record summaries.

### Map Detail

- The bottom card still reads as "information dump" instead of "route story".
- Metadata needs a clearer hierarchy: title first, then date, then trust indicators, then metrics.
- Floating controls should sit in the same visual system as the homepage.

## New Structural Rules

1. One primary action per screen.
2. Never show full diagnostics in the main visual hierarchy by default.
3. Use map overlays only for lightweight status and direct actions.
4. Bottom sheets/cards must feel anchored, not stacked.
5. Reuse one shared scaffold for:
   - map background
   - top overlay status
   - floating map action
   - bottom content surface

## Screen Targets

### Homepage

- Map: 60% to 68% of vertical space depending on visual balance
- Top left: lightweight GPS/status chip
- Top right: single floating locate action
- Bottom surface:
  - hero metric
  - 2 secondary stats
  - record CTA
  - one lightweight supporting state line
  - navigation

### Map Detail

- Full-screen map
- Top left: back button
- Bottom right: refit button
- Bottom card:
  - drag handle
  - route title/date
  - quality + point count chips
  - short summary
  - three metrics

### History

- Keep current Compose list structure
- Polish card rhythm after homepage and map are stable

## Mapbox Migration Prep

Before replacing Baidu Map with Mapbox, keep the screen structure independent from the map SDK:

- `MainComposeScreen` owns layout only
- `MapComposeScreen` owns layout only
- controllers own map state and data shaping
- map SDK usage stays behind a viewport-level wrapper

This allows the future Mapbox migration to swap the map implementation without redesigning the screens again.
