package com.wenhao.record.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory

object MapMarkerIconFactory {
    fun fromDrawableResource(context: Context, @DrawableRes drawableRes: Int): BitmapDescriptor {
        val drawable = requireNotNull(AppCompatResources.getDrawable(context, drawableRes)) {
            "Drawable resource not found: $drawableRes"
        }.mutate()

        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
