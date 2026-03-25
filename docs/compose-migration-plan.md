# TrackRecord Compose Migration Plan

## Goals

- Move the app to Compose incrementally without rewriting business logic or storage.
- Establish a consistent design system before migrating more screens.
- Land the history screen first because it is UI-dense but structurally simpler than the dashboard.

## Phase Plan

1. Foundation
- Add Compose runtime and Material 3 support.
- Introduce `TrackRecordTheme`, color tokens, spacing tokens, shapes, and shared cards/buttons.
- Keep the existing XML dashboard untouched during this phase.

2. History Screen Migration
- Replace the history page view layer with `ComposeView`.
- Keep `MainActivity`, import/export flows, `HistoryStorage`, `HistoryDayItem`, and `MapActivity` intact.
- Preserve long-press delete behavior and item selection.

3. Dashboard Migration
- Convert the dashboard panel and bottom navigation to Compose.
- Keep the Baidu `MapView` via `AndroidView`.
- Move state rendering from `DashboardUiController` into screen-level Compose state.

4. Cleanup
- Remove obsolete XML layouts and RecyclerView adapter/controller code after parity is confirmed.
- Evaluate whether map preview and diagnostics should become dedicated Compose modules.

## Design System Direction

### Product Tone

- Calm, trustworthy, and lightly premium.
- Warm neutral surfaces with a restrained violet accent.
- Clear hierarchy over ornamentation.

### Core Tokens

- Background: warm paper neutral
- Surface: clean white
- Accent: violet for primary action and active navigation only
- Success/Warning/Error: dedicated status colors, never reused as brand color

### Type Hierarchy

- Hero metric: large numeric emphasis
- Screen title: strong but compact
- Card title: medium emphasis
- Supporting text: softer neutral tone
- Metadata chips: compact, low-contrast surfaces

### Component Rules

- Primary actions use filled buttons with strong contrast.
- Secondary actions use outlined or tonal buttons, not tag-like text pills.
- Cards share one corner radius family and one elevation system.
- Empty states must include title, guidance, and a single clear next action.

## Migration Guardrails

- No data model rewrites during UI migration.
- No navigation rewrite before screen parity.
- Keep all import/export, map intent, and delete flows functional.
- Accessibility is a release criterion for every migrated screen.
