package com.sharawang.fridge.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Morandi palette: blue-led accents on a clean beige base.
 *
 * The neutrals sit at a yellow hue (around 45°), never red — a beige with any red in it
 * reads as pink the moment it goes pale, which is the one thing this palette must not do.
 *
 * Named by colour, not by role — roles are assigned in [FridgeTheme].
 */
internal object Morandi {
    // Blue does most of the work: chrome, primary actions, selected states.
    val Blue = Color(0xFF7C8B99)
    val BlueSoft = Color(0xFF9CAAB6)
    val BlueLight = Color(0xFFDDE3E7)
    val BluePale = Color(0xFFE9EDF0)
    val BlueDeep = Color(0xFF33414D)
    val BlueShadow = Color(0xFF3F4E5A)
    val BlueMuted = Color(0xFFA8B7C4)

    // Sage is the only other family, reserved for "this is fine".
    val Sage = Color(0xFF7E8C7C)
    val SageLight = Color(0xFFE1E5D8)
    val SageDeep = Color(0xFF2F382E)
    val SageMuted = Color(0xFFA6B4A3)

    // Status accents. Ochre for "soon", rust for "too late" — both heavily desaturated, and
    // both kept on the brown side of orange so their pale fills stay tan, not peach.
    val Ochre = Color(0xFF8F7A3E)
    val OchreLight = Color(0xFFEDE7D5)
    val OchreMuted = Color(0xFFC7B584)
    val Rust = Color(0xFF97684C)
    val RustLight = Color(0xFFEDE4D6)
    val RustDeep = Color(0xFF4F3020)
    val RustMuted = Color(0xFFD2A184)

    // Beige neutrals.
    val Paper = Color(0xFFFBFAF5)
    val Canvas = Color(0xFFF5F3EC)
    val Linen = Color(0xFFE8E5DA)
    val Ink = Color(0xFF35332C)
    val InkSoft = Color(0xFF5E5B50)
    val Stone = Color(0xFF9A968A)

    val NightCanvas = Color(0xFF1F1E1A)
    val NightPaper = Color(0xFF26251F)
    val NightLinen = Color(0xFF454339)
    val NightInk = Color(0xFFE8E5DA)
    val NightInkSoft = Color(0xFFC7C3B6)
    val NightStone = Color(0xFF918D80)
}
