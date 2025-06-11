package com.flowfoundation.wallet.page.wallet.view

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize


@Composable
fun FadeAnimationBackground(
    imageRes: Int,
    itemPerRow: Int = 8,
    colorInt: Int = -1,
) {

    var parentSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .onGloballyPositioned { layoutCoordinates ->
                parentSize = layoutCoordinates.size
            },
        contentAlignment = Alignment.Center
    ) {
        if (parentSize != IntSize.Zero) {
            val heightPerItem = parentSize.width / itemPerRow
            val numberOfRows = getNumberOfRows(parentSize.height, heightPerItem)
            AnimatedCanvasImageGrid(
                imageRes = imageRes,
                rows = numberOfRows,
                columns = itemPerRow,
                itemSize = heightPerItem,
                color = if (colorInt == -1) MaterialTheme.colorScheme.primary else {
                    Color(colorInt)
                },
                rotationDegrees = 10f
            )
        }
    }
}

@Composable
fun AnimatedCanvasImageGrid(
    imageRes: Int,
    rows: Int,
    columns: Int,
    itemSize: Int,
    color: Color,
    rotationDegrees: Float
) {

    val painter = painterResource(id = imageRes)

    val alphaOne by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    val alphaTwo by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )

    val alphaThree by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 3000,
                easing = FastOutLinearInEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                rotationZ = rotationDegrees,
                clip = false
            )
    ) {

        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val x = col * itemSize.toFloat() + (row % 2) * (itemSize / 3) + col * itemSize / 3
                val y = row * itemSize.toFloat() + row * itemSize / 10

                drawIntoCanvas { canvas ->
                    canvas.withSave {
                        translate(x, y){
                            with(painter) {
                                draw(
                                    size = Size(itemSize.toFloat(), itemSize.toFloat()),
                                    colorFilter = ColorFilter.tint(color),
                                    alpha = calculateAlpha(row, col, alphaOne, alphaTwo, alphaThree)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateAlpha(row: Int, col:Int, alphaOne: Float, alphaTwo: Float, alphaThree: Float): Float {
    val baseAlpha = when {
        (row + col) % 3 == 0 -> alphaOne / 0.8f
        (row + col) % 3 == 1 -> alphaTwo / 0.6f
        else -> alphaThree / 0.4f
    }
    val disturbance = 0.5f * (1f + kotlin.math.sin((row * col).toFloat()))

    return (baseAlpha * disturbance).coerceIn(0f, 1f)
}


private fun getNumberOfRows(screenHeight: Int, heightPerItem: Int): Int {
    return (screenHeight / heightPerItem) + 2
}