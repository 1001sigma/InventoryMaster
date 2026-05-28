package com.example.inventorymaster.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// Spacing Scale — uniform across all screens
// =============================================================================

/** Tight spacing: icon-to-text, inline element gap */
val SpaceXxs = 4.dp

/** Compact spacing: row-internal element gap */
val SpaceXs = 8.dp

/** Standard small: card content padding, list item internal gap */
val SpaceSm = 12.dp

/** Standard medium: page padding, card padding, section gap */
val SpaceMd = 16.dp

/** Standard large: section-to-section spacing */
val SpaceLg = 24.dp

/** Extra large: page-level separation, screen top/bottom padding */
val SpaceXl = 32.dp

// =============================================================================
// Touch Target Minimums — Glove-Compatible (仓库戴手套场景)
// =============================================================================

/** Minimum interactive element size (buttons, icon buttons, checkboxes) */
val TouchTargetMin = 48.dp

/** Minimum list item height for easy tapping */
val ListItemMinHeight = 56.dp

/** Minimum card tap area height */
val CardTapMinHeight = 48.dp

// =============================================================================
// Icon Sizes
// =============================================================================

/** Standard icon within text/rows */
val IconSizeSm = 16.dp

/** Default icon in IconButton */
val IconSizeMd = 20.dp

/** Large standalone icon */
val IconSizeLg = 24.dp

/** Prominent icon (empty states, feature intros) */
val IconSizeXl = 48.dp

// =============================================================================
// Component-Specific Sizes
// =============================================================================

/** Standard button height (matches TouchTargetMin) */
val ButtonHeight = 48.dp

/** Compact button height (for toolbars, secondary actions — avoid for primary) */
val ButtonHeightCompact = 40.dp

/** Input field height */
val InputHeight = 56.dp

/** Table row min height */
val TableRowHeight = 40.dp

/** Camera capture button diameter */
val CaptureButtonSize = 72.dp

// =============================================================================
// Border & Shape
// =============================================================================

/** Standard card border thickness (subtle) */
val BorderThin = 0.5.dp

/** Divider thickness */
val DividerThickness = 1.dp

// =============================================================================
// Typography (re-exports for convenience — primary definitions in Type.kt)
// =============================================================================

/** Monospace digit size for table alignment (re-declared for discoverability) */
// See Type.kt for typography definitions
