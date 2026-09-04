package com.marcogn.coverdex.ui.theme

import androidx.compose.ui.graphics.Color

// Seeded from the app's brand purple, #5B21B6 — the Capacitor adaptive-icon background and the
// PWA's accent color (see docs/plan/phase-0-foundation.md, "Theme and resources"). The app's
// visual identity does not change in this rewrite. Dynamic colour (Material You) overrides these
// on API 31+; see Theme.kt.

// Light scheme
val BrandPrimary40 = Color(0xFF5B21B6)
val BrandOnPrimary = Color(0xFFFFFFFF)
val BrandPrimaryContainer90 = Color(0xFFE1D0FB)
val BrandOnPrimaryContainer10 = Color(0xFF1E0B3C)
val BrandSecondary40 = Color(0xFF625B71)
val BrandTertiary40 = Color(0xFF7D5260)

// Dark scheme
val BrandPrimary80 = Color(0xFFC5ABED)
val BrandOnPrimaryDark = Color(0xFF1E0B3C)
val BrandPrimaryContainer30 = Color(0xFF411782)
val BrandOnPrimaryContainer90 = Color(0xFFE7DBFA)
val BrandSecondary80 = Color(0xFFCBC2DB)
val BrandTertiary80 = Color(0xFFEFB8C8)
