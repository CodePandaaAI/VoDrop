package com.liftley.vodrop.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Expressive Design uses "Extra Large" rounding
val VoDropShape = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),      // Chips
    medium = RoundedCornerShape(16.dp),     // Cards
    large = RoundedCornerShape(28.dp),      // Dialogs / Large Cards
    extraLarge = RoundedCornerShape(48.dp)  // Hero sections / Bottom Sheets
)