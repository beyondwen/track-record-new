# Mapbox Migration Plan

## Goal

Replace the current Baidu Map dependency with a Mapbox-ready Compose-first map stack while preserving:

- homepage live map
- route polylines
- start/end markers
- viewport refit and focus
- history detail map screen

## Phase Plan

1. Decouple screen structure from the map SDK.
2. Introduce a map-provider abstraction so the page layer does not depend directly on Baidu APIs.
3. Add Mapbox dependencies and token placeholders.
4. Replace homepage map implementation.
5. Replace history detail map implementation.
6. Reintroduce advanced map layers such as heatmap in a second pass.

## Token Notes

Mapbox requires:

- runtime access token for map usage
- downloads token for dependency access

The user will provide tokens later, so the current phase only prepares the project structure and placeholders.

## UI Rule

All map screens remain Compose-driven.
Only the map rendering node may temporarily stay behind a Compose wrapper until the final Mapbox Compose implementation is connected.
