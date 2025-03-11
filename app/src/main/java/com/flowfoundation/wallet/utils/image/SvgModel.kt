package com.flowfoundation.wallet.utils.image

import android.graphics.drawable.PictureDrawable
import android.util.Base64
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException


class SvgModel(val svgString: String)

class SvgModelLoader : ModelLoader<SvgModel, PictureDrawable> {
    override fun handles(model: SvgModel): Boolean = true
    override fun buildLoadData(
        model: SvgModel,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<PictureDrawable> {
        return ModelLoader.LoadData(ObjectKey(model), SvgDataFetcher(model.svgString))
    }
}

// Custom DataFetcher for SVG
class SvgDataFetcher(private val svgString: String) : DataFetcher<PictureDrawable> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in PictureDrawable>) {
        try {
            val decodedBytes = Base64.decode(svgString, Base64.DEFAULT)
            val svg = SVG.getFromString(String(decodedBytes))
            val drawable = PictureDrawable(svg.renderToPicture())
            callback.onDataReady(drawable)
        } catch (e: SVGParseException) {
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {
        // no-op
    }

    override fun cancel() {
        // no-op
    }

    override fun getDataClass(): Class<PictureDrawable> {
        return PictureDrawable::class.java
    }

    override fun getDataSource(): DataSource = DataSource.LOCAL
}

// Custom ModelLoaderFactory for SVG
class SvgModelLoaderFactory : ModelLoaderFactory<SvgModel, PictureDrawable> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<SvgModel, PictureDrawable> {
        return SvgModelLoader()
    }

    override fun teardown() {
        // no-op
    }
}