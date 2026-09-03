package com.ovi.handoff.mobile.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ============================================================================
// Material 3 Expressive Corner Radius Scale
// ============================================================================
val ShapeExtraSmall: RoundedCornerShape = RoundedCornerShape(4.dp)
val ShapeSmall: RoundedCornerShape = RoundedCornerShape(8.dp)
val ShapeMedium: RoundedCornerShape = RoundedCornerShape(12.dp)
val ShapeLarge: RoundedCornerShape = RoundedCornerShape(16.dp)
val ShapeLargeIncreased: RoundedCornerShape = RoundedCornerShape(20.dp)
val ShapeExtraLarge: RoundedCornerShape = RoundedCornerShape(28.dp)
val ShapeExtraLargeIncreased: RoundedCornerShape = RoundedCornerShape(32.dp)
val ShapeExtraExtraLarge: RoundedCornerShape = RoundedCornerShape(48.dp)
val ShapeFull: RoundedCornerShape = RoundedCornerShape(100.dp)

val HandoffShapes: Shapes = Shapes(
    extraSmall = ShapeExtraSmall,
    small = ShapeSmall,
    medium = ShapeMedium,
    large = ShapeLarge,
    extraLarge = ShapeExtraLarge
)
