# Project-specific R8 / ProGuard rules.
#
# The current app stack mainly uses AndroidX, Compose, Room, and Mapbox.
# These libraries already ship the consumer rules required for release builds,
# so this file intentionally stays minimal.
#
# Add keep rules here only when a release-only issue is confirmed and the
# affected types are reached via reflection, JNI, or dynamic loading.
