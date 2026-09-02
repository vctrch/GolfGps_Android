package com.vctrch.golfgps.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.vctrch.golfgps.domain.GreenMappingConfidence
import com.vctrch.golfgps.domain.TeeMappingConfidence
import kotlin.math.roundToInt

/**
 * Opaque hole-map pins. Outline / alpha-only glyphs disappear on satellite and hybrid imagery.
 */
internal enum class HoleMapMarkerKind {
    GREEN,
    TEE,
    PLAYER,
}

internal object HoleMapMarkerPalette {
    const val STROKE = 0xFF1A1A1A.toInt()
    const val HALO = 0xFFFFFFFF.toInt()
    const val GLYPH = 0xFFFFFFFF.toInt()
    const val PLAYER_FILL = 0xFF1565C0.toInt()

    /** GolfTheme.Accent — matches mapped-green legend. */
    const val GREEN_MAPPED = 0xFF33B873.toInt()
    const val GREEN_ESTIMATED = 0xFFD98C1F.toInt()
    const val GREEN_LOADING = 0xFFE67E22.toInt()

    /** GolfTheme.Fairway — matches mapped-tee legend on iOS. */
    const val TEE_MAPPED = 0xFF1F6B47.toInt()
    const val TEE_POSSIBLE = 0xFFE67E22.toInt()
    const val TEE_UNMAPPED = 0xFF6B6B6B.toInt()

    fun greenFill(confidence: GreenMappingConfidence): Int =
        when (confidence) {
            GreenMappingConfidence.MAPPED -> GREEN_MAPPED
            GreenMappingConfidence.ESTIMATED -> GREEN_ESTIMATED
            GreenMappingConfidence.LOADING -> GREEN_LOADING
        }

    fun teeFill(confidence: TeeMappingConfidence): Int =
        when (confidence) {
            TeeMappingConfidence.MAPPED -> TEE_MAPPED
            TeeMappingConfidence.MATCHED,
            TeeMappingConfidence.FAIRWAY_DERIVED,
            TeeMappingConfidence.ESTIMATED,
            -> TEE_POSSIBLE
            TeeMappingConfidence.UNAVAILABLE,
            TeeMappingConfidence.NOT_MAPPED,
            -> TEE_UNMAPPED
        }

    fun greenTitle(confidence: GreenMappingConfidence): String =
        if (confidence == GreenMappingConfidence.MAPPED) "Green" else confidence.shortLabel

    fun isFullyOpaque(argb: Int): Boolean = (argb ushr 24) == 0xFF
}

internal object HoleMapMarkerIcons {
    fun sizePx(density: Float): Int = (40f * density).roundToInt().coerceAtLeast(72)

    fun bitmap(
        kind: HoleMapMarkerKind,
        fillArgb: Int,
        sizePx: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val circleCy = sizePx * 0.40f
        val radius = sizePx * 0.30f
        val tipY = sizePx * 0.94f

        val pin =
            when (kind) {
                HoleMapMarkerKind.PLAYER ->
                    Path().apply { addCircle(cx, sizePx / 2f, radius * 1.05f, Path.Direction.CW) }
                HoleMapMarkerKind.GREEN,
                HoleMapMarkerKind.TEE,
                -> pinPath(cx, circleCy, radius, tipY)
            }

        val strokeWidth = sizePx * 0.07f
        val haloWidth = sizePx * 0.045f
        canvas.drawPath(pin, fillPaint(fillArgb))
        canvas.drawPath(pin, strokePaint(HoleMapMarkerPalette.HALO, haloWidth))
        canvas.drawPath(pin, strokePaint(HoleMapMarkerPalette.STROKE, strokeWidth))
        drawGlyph(canvas, kind, cx, if (kind == HoleMapMarkerKind.PLAYER) sizePx / 2f else circleCy, radius)
        return bitmap
    }

    private fun pinPath(
        cx: Float,
        cy: Float,
        radius: Float,
        tipY: Float,
    ): Path {
        val circle = Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) }
        val point =
            Path().apply {
                moveTo(cx - radius * 0.78f, cy + radius * 0.42f)
                lineTo(cx, tipY)
                lineTo(cx + radius * 0.78f, cy + radius * 0.42f)
                close()
            }
        circle.op(point, Path.Op.UNION)
        return circle
    }

    private fun drawGlyph(
        canvas: Canvas,
        kind: HoleMapMarkerKind,
        cx: Float,
        cy: Float,
        radius: Float,
    ) {
        val glyph = fillPaint(HoleMapMarkerPalette.GLYPH)
        when (kind) {
            HoleMapMarkerKind.GREEN -> {
                val pole =
                    Path().apply {
                        addRect(
                            cx - radius * 0.08f,
                            cy - radius * 0.46f,
                            cx + radius * 0.02f,
                            cy + radius * 0.42f,
                            Path.Direction.CW,
                        )
                    }
                val flag =
                    Path().apply {
                        moveTo(cx + radius * 0.02f, cy - radius * 0.46f)
                        lineTo(cx + radius * 0.52f, cy - radius * 0.18f)
                        lineTo(cx + radius * 0.02f, cy + radius * 0.04f)
                        close()
                    }
                canvas.drawPath(pole, glyph)
                canvas.drawPath(flag, glyph)
            }
            HoleMapMarkerKind.TEE ->
                canvas.drawCircle(cx, cy, radius * 0.28f, glyph)
            HoleMapMarkerKind.PLAYER ->
                canvas.drawCircle(cx, cy, radius * 0.32f, glyph)
        }
    }

    private fun fillPaint(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

    private fun strokePaint(
        color: Int,
        width: Float,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
}
