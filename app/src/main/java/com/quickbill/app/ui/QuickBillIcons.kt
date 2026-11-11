package com.quickbill.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class BillIcon {
    Add, Subtract, Back, Print, History, Shop, Receipt, Bag,
}

@Composable
internal fun BillIcon(icon: BillIcon, size: Dp = 22.dp) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(fromX: Float, fromY: Float, toX: Float, toY: Float) {
                drawLine(color, Offset(fromX, fromY), Offset(toX, toY), stroke.width, stroke.cap)
            }
            fun rect(x: Float, y: Float, width: Float, height: Float, corner: Float = 1f) {
                drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(corner), style = stroke)
            }
            when (icon) {
                BillIcon.Add -> {
                    line(5f, 12f, 19f, 12f)
                    line(12f, 5f, 12f, 19f)
                }
                BillIcon.Subtract -> line(5f, 12f, 19f, 12f)
                BillIcon.Back -> {
                    line(20f, 12f, 4f, 12f)
                    line(4f, 12f, 11f, 5f)
                    line(4f, 12f, 11f, 19f)
                }
                BillIcon.Print -> {
                    rect(3f, 8f, 18f, 9f, 2f)
                    rect(7f, 3f, 10f, 5f)
                    rect(7f, 14f, 10f, 7f)
                    line(16f, 11f, 17f, 11f)
                }
                BillIcon.History -> {
                    drawArc(color, 192f, 306f, false, Offset(4f, 3f), Size(17f, 17f), style = stroke)
                    line(2.5f, 6f, 3.8f, 11f)
                    line(3.8f, 11f, 8.5f, 9.6f)
                    line(12.5f, 7.5f, 12.5f, 12f)
                    line(12.5f, 12f, 16f, 14f)
                }
                BillIcon.Shop -> {
                    drawPath(
                        Path().apply {
                            moveTo(3f, 10f)
                            lineTo(5f, 4f)
                            lineTo(19f, 4f)
                            lineTo(21f, 10f)
                            close()
                        },
                        color,
                        style = stroke,
                    )
                    rect(5f, 10f, 14f, 10f)
                    rect(10f, 14f, 4f, 6f)
                    line(9f, 4f, 8f, 10f)
                    line(15f, 4f, 16f, 10f)
                }
                BillIcon.Receipt -> {
                    drawPath(
                        Path().apply {
                            moveTo(5f, 3f)
                            lineTo(19f, 3f)
                            lineTo(19f, 21f)
                            lineTo(15.5f, 18.5f)
                            lineTo(12f, 21f)
                            lineTo(8.5f, 18.5f)
                            lineTo(5f, 21f)
                            close()
                        },
                        color,
                        style = stroke,
                    )
                    line(8.5f, 8f, 15.5f, 8f)
                    line(8.5f, 12f, 15.5f, 12f)
                }
                BillIcon.Bag -> {
                    rect(4f, 8f, 16f, 13f, 2f)
                    drawArc(color, 180f, 180f, false, Offset(8f, 3f), Size(8f, 10f), style = stroke)
                    line(8f, 8f, 8f, 10f)
                    line(16f, 8f, 16f, 10f)
                }
            }
        }
    }
}
