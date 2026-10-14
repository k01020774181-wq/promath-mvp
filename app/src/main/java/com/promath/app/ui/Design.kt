package com.promath.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF080808)
val Muted = Color(0xFFB8B8B8)
val Line = Color(0xFF454545)
val CardFill = Brush.linearGradient(listOf(Color(0xFF242424), Color(0xFF111111)))
val CardShape = RoundedCornerShape(10.dp)

@Composable fun PromathTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Ink,
        background = Ink, onBackground = Color.White, surface = Color(0xFF171717), onSurface = Color.White,
        secondary = Muted, onSecondary = Ink, outline = Line, onSurfaceVariant = Muted)) {
        Surface(Modifier.fillMaxSize(), color = Ink, contentColor = Color.White, content = content)
    }
}

fun Modifier.mockCard(click: (() -> Unit)? = null) = clip(CardShape).background(CardFill).border(1.dp, Line, CardShape)
    .then(if (click != null) Modifier.clickable(onClick = click) else Modifier)

enum class Glyph { MENU, USER, BOOK, CHART, DOCUMENT, LOCK, PLAY, BACK, ARROW, CHECK, TIMER, BULB, RETRY, PENCIL, SCHOOL }

@Composable fun MonoIcon(kind: Glyph, dimension: Dp = 28.dp, tint: Color = Color.White) {
    Canvas(Modifier.size(dimension)) {
        scale(size.width / 100f, size.height / 100f, Offset.Zero) {
            val stroke = Stroke(6f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 6f, StrokeCap.Round)
            fun path(vararg points: Pair<Float, Float>, closed: Boolean = false): Path = Path().apply {
                moveTo(points[0].first, points[0].second)
                points.drop(1).forEach { lineTo(it.first, it.second) }
                if (closed) close()
            }
            when (kind) {
                Glyph.MENU -> { line(20f, 27f, 80f, 27f); line(20f, 50f, 80f, 50f); line(20f, 73f, 80f, 73f) }
                Glyph.BACK -> drawPath(path(60f to 18f, 28f to 50f, 60f to 82f), tint, style = stroke)
                Glyph.ARROW -> drawPath(path(38f to 24f, 65f to 50f, 38f to 76f), tint, style = stroke)
                Glyph.USER -> { drawCircle(tint, 15f, Offset(50f, 27f), style = stroke); drawPath(path(21f to 84f, 21f to 67f, 35f to 52f, 65f to 52f, 79f to 67f, 79f to 84f, closed = true), tint, style = stroke) }
                Glyph.BOOK -> { drawPath(path(50f to 83f, 50f to 24f, 34f to 14f, 12f to 14f, 12f to 75f, 34f to 75f, 50f to 83f, 66f to 75f, 88f to 75f, 88f to 14f, 66f to 14f, 50f to 24f), tint, style = stroke) }
                Glyph.CHART -> { drawRoundRect(tint, Offset(15f, 58f), Size(16f, 28f), androidx.compose.ui.geometry.CornerRadius(3f)); drawRoundRect(tint, Offset(42f, 38f), Size(16f, 48f), androidx.compose.ui.geometry.CornerRadius(3f)); drawRoundRect(tint, Offset(69f, 14f), Size(16f, 72f), androidx.compose.ui.geometry.CornerRadius(3f)) }
                Glyph.DOCUMENT -> { drawPath(path(24f to 10f, 62f to 10f, 80f to 29f, 80f to 90f, 24f to 90f, closed = true), tint, style = stroke); line(62f, 10f, 62f, 29f); line(62f, 29f, 80f, 29f); line(36f, 45f, 67f, 45f); line(36f, 60f, 67f, 60f); line(36f, 75f, 58f, 75f) }
                Glyph.LOCK -> { drawRoundRect(tint, Offset(23f, 43f), Size(54f, 45f), androidx.compose.ui.geometry.CornerRadius(5f), style = stroke); drawArc(tint, 180f, 180f, false, Offset(33f, 13f), Size(34f, 55f), style = stroke); line(50f, 61f, 50f, 72f) }
                Glyph.PLAY -> drawPath(path(30f to 16f, 84f to 50f, 30f to 84f, closed = true), tint)
                Glyph.CHECK -> { drawCircle(tint, 40f, Offset(50f, 50f)); drawPath(path(28f to 51f, 43f to 66f, 71f to 35f), Ink, style = stroke) }
                Glyph.TIMER -> { drawCircle(tint, 34f, Offset(50f, 57f), style = stroke); line(38f, 9f, 62f, 9f); line(50f, 9f, 50f, 20f); line(50f, 57f, 50f, 36f); line(50f, 57f, 65f, 57f) }
                Glyph.BULB -> { drawCircle(tint, 25f, Offset(50f, 39f), style = stroke); line(38f, 63f, 38f, 76f); line(38f, 76f, 62f, 76f); line(62f, 76f, 62f, 63f); line(42f, 87f, 58f, 87f); line(9f, 39f, 2f, 39f); line(91f, 39f, 98f, 39f); line(50f, 2f, 50f, 0f) }
                Glyph.RETRY -> { drawArc(tint, -65f, 295f, false, Offset(15f, 15f), Size(70f, 70f), style = Stroke(9f, cap = StrokeCap.Round)); drawPath(path(52f to 1f, 81f to 22f, 46f to 28f, closed = true), tint) }
                Glyph.PENCIL -> { drawCircle(tint, 40f, Offset(50f, 50f), style = stroke); drawPath(path(26f to 72f, 35f to 50f, 64f to 22f, 78f to 36f, 49f to 65f, closed = true), tint, style = stroke) }
                Glyph.SCHOOL -> { drawPath(path(8f to 37f, 50f to 14f, 92f to 37f, 50f to 61f, closed = true), tint, style = stroke); line(27f, 51f, 27f, 76f); line(27f, 76f, 50f, 87f); line(50f, 87f, 74f, 76f); line(74f, 76f, 74f, 51f); line(92f, 37f, 92f, 75f) }
            }
        }
    }
}

@Composable fun StripeProgress(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF242424)).border(1.dp, Line, RoundedCornerShape(6.dp))) {
        val width = size.width * progress.coerceIn(0f, 1f)
        drawRect(Color(0xFFAAAAAA), size = Size(width, size.height))
        var x = -size.height
        while (x < width) {
            val p = Path().apply { moveTo(x, size.height); lineTo(x + size.height, 0f); lineTo((x + size.height + 3.dp.toPx()).coerceAtMost(width), 0f); lineTo((x + 3.dp.toPx()).coerceAtMost(width), size.height); close() }
            if (x + size.height <= width) drawPath(p, Color.White)
            x += 7.dp.toPx()
        }
    }
}

