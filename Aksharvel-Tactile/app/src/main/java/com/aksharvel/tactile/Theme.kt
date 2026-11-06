package com.aksharvel.tactile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Cream = Color(0xFFFFF8EA)
val Navy = Color(0xFF2B2D6B)
val Purple = Color(0xFF6C4BDE)
val PurpleDark = Color(0xFF4F32B5)
val Teal = Color(0xFF2EC4B6)
val TealDark = Color(0xFF1F9C90)
val TealTint = Color(0xFFDFF6F2)
val Coral = Color(0xFFFF6B5B)
val CoralDark = Color(0xFFD9483A)
val Yellow = Color(0xFFFFC93C)
val YellowDark = Color(0xFFE0A800)
val SoftYellow = Color(0xFFFFF2D0)
val Gold = Color(0xFFC9962B)
val Lavender = Color(0xFFE9E6F5)
val Muted = Color(0xFF8E89B0)
val LockedFg = Color(0xFFBDB8D6)
val Green = Color(0xFF2EB86B)
val GreenDark = Color(0xFF1F8F4E)

/** A button/tile with a solid "edge" underneath, as in the wireframes. Always give it an explicit size. */
@Composable
fun Chunky(
    modifier: Modifier,
    face: Color,
    edge: Color,
    shape: Shape = RoundedCornerShape(24.dp),
    depth: Dp = 6.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val base = modifier.clip(shape).background(edge)
    val clickable = if (onClick != null) base.clickable(enabled = enabled, onClick = onClick) else base
    Box(clickable) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = depth)
                .clip(shape)
                .background(face),
            contentAlignment = Alignment.Center,
            content = content
        )
    }
}

@Composable
fun Pill(
    text: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    onClick: (() -> Unit)? = null
) {
    val base = modifier.clip(RoundedCornerShape(50)).background(bg)
    val m = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(m.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = fg, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Mascot(size: Dp) {
    Chunky(Modifier.size(size), Teal, TealDark, CircleShape, depth = 4.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(size / 6)) {
            repeat(2) { Box(Modifier.size(size / 7).clip(CircleShape).background(Color.White)) }
        }
    }
}

@Composable
fun ProgressBar(
    fraction: Float,
    fill: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp
) {
    Box(modifier.height(height).clip(RoundedCornerShape(height)).background(track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height))
                .background(fill)
        )
    }
}

fun marathiNumber(n: Int): String =
    n.toString().map { if (it in '0'..'9') '०' + (it - '0') else it }.joinToString("")
