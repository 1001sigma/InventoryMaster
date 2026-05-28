package com.example.inventorymaster.ui.theme

import androidx.compose.ui.graphics.Color

// =============================================================================
// Industrial-Medical Color System — Dark-First for Warehouse Inventory
// =============================================================================

// --- Material Palette: Vibrant Teal (Default) ---
// Primary:   Teal/Cyan — medical, precise, professional
// Secondary: Deep Green — calm, trustworthy
// Tertiary:  Orange-Red — action, alert, call-to-action
val MdPrimary = Color(0xFF00BFA5)
val MdSecondary = Color(0xFF00897B)
val MdTertiary = Color(0xFFFF6E40)
val MdError = Color(0xFFCF6679)

// Light variants (for light mode containers / on-colors)
val MdPrimaryLight = Color(0xFFA7FFEB)
val MdSecondaryLight = Color(0xFFB2DFDB)
val MdTertiaryLight = Color(0xFFFFCCBC)

// --- Dark Mode Surface Hierarchy ---
// Darkest → lightest: Background → Surface → SurfaceVariant → Card → Elevated
val DarkBackground = Color(0xFF0D0D0D)      // page background
val DarkSurface = Color(0xFF141414)          // scaffold / nav surfaces
val DarkSurfaceVariant = Color(0xFF1E1E1E)   // card / dialog backgrounds
val DarkSurfaceVariant2 = Color(0xFF2A2A2A)  // elevated cards, table headers
val DarkSurfaceVariant3 = Color(0xFF363636)  // hover / selected state

// On-Surface variants for dark mode (text hierarchy)
val DarkOnSurfacePrimary = Color(0xFFF5F5F5)    // primary text on dark surfaces
val DarkOnSurfaceSecondary = Color(0xFFAAAAAA)   // secondary text
val DarkOnSurfaceTertiary = Color(0xFF6A6A6A)    // disabled / placeholder text

// --- Light Mode Surface Hierarchy ---
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFF5F5F5)
val LightSurfaceVariant = Color(0xFFE8E8E8)

// --- Semantic Status Colors ---
// These remain consistent across dark and light modes
val StatusSuccess = Color(0xFF4CAF50)       // checked, matched, verified
val StatusWarning = Color(0xFFFFC107)       // near-expiry, quantity diff
val StatusError = Color(0xFFE53935)         // expired, mismatch, error
val StatusInfo = Color(0xFF2196F3)          // pending, in-progress
val StatusArchived = Color(0xFF9E9E9E)      // archived, locked
val StatusPending = Color(0xFF0288D1)       // pending check (darker blue for contrast)

// --- Data Highlight Colors (used for search result highlighting) ---
val HighlightSearch = Color(0xFF33B5E5)     // search term highlight background
val HighlightField = Color(0xFF40C4FF)      // field highlight (successor to #84E7F5)

// --- Scanner Overlay Colors ---
val ScannerMask = Color(0x99000000)         // semi-transparent black mask
val ScannerCorner = Color(0xFF4E6F80)       // corner brackets
val ScannerLine = Color(0xFF41B246)         // scanning line green

// --- Canvas/Drawing Colors ---
val CanvasBackground = Color(0xFF121212)    // camera/scanner background

// --- Legacy (kept for ExtendedColors.kt compatibility, do not use directly) ---
@Suppress("unused")
val Purple80 = Color(0xFFD0BCFF)
@Suppress("unused")
val PurpleGrey80 = Color(0xFFCCC2DC)
@Suppress("unused")
val Pink80 = Color(0xFFEFB8C8)
@Suppress("unused")
val Purple40 = Color(0xFF6650a4)
@Suppress("unused")
val PurpleGrey40 = Color(0xFF625b71)
@Suppress("unused")
val Pink40 = Color(0xFF7D5260)
