package com.flowfoundation.wallet.utils

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.utils.extensions.res2color
import com.github.alexzhirkevich.customqrgenerator.QrData
import com.github.alexzhirkevich.customqrgenerator.QrErrorCorrectionLevel
import com.github.alexzhirkevich.customqrgenerator.vector.QrCodeDrawable
import com.github.alexzhirkevich.customqrgenerator.vector.createQrVectorOptions
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorColor
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorLogoPadding
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorLogoShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.QrVectorPixelShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.asBallShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.asFrameShape
import com.github.alexzhirkevich.customqrgenerator.vector.style.scale

fun String.toQRDrawable(
    withScale: Boolean = false
): Drawable {
    val data = QrData.Url(this)
    val options = createQrVectorOptions {

        padding = if (withScale) .08f else .1f

        background {
            drawable = ContextCompat.getDrawable(Env.getApp(), R.drawable.bg_qr_code)
        }

        logo {
            drawable = ContextCompat.getDrawable(Env.getApp(), R.drawable.ic_launcher_fill)
            size = .22f
            padding = QrVectorLogoPadding.Natural(.1f)
            shape = QrVectorLogoShape.Circle
        }
        colors {
            dark = QrVectorColor.Solid(R.color.black_80.res2color())
            ball = QrVectorColor.Solid(R.color.accent_green.res2color())
            frame = QrVectorColor.Solid(R.color.black_80.res2color())
        }
        shapes {
            if (withScale) {
                darkPixel = QrVectorPixelShape.Circle(.9f)
                ball = QrVectorBallShape
                    .Circle(1f).scale(1.3f).asBallShape()
                frame = QrVectorFrameShape
                    .Circle(1.2f).scale(1.2f).asFrameShape()
            } else {
                darkPixel = QrVectorPixelShape.Circle(.8f)
                ball = QrVectorBallShape
                    .Circle(1f).scale(.9f).asBallShape()
                frame = QrVectorFrameShape
                    .Circle(1.2f).scale(.9f).asFrameShape()
            }
        }
        errorCorrectionLevel = if (withScale) QrErrorCorrectionLevel.Low else
            QrErrorCorrectionLevel.MediumHigh
    }
    return QrCodeDrawable(data, options)
}
