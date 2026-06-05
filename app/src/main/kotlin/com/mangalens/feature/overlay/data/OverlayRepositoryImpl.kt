package com.mangalens.feature.overlay.data

import android.content.Context
import android.content.Intent
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.overlay.domain.OverlayRepository

class OverlayRepositoryImpl(private val context: Context) : OverlayRepository {
    private var visible = false

    override fun show(items: List<OverlayItem>) {
        visible = true
        SharedOverlayState.currentItems = items
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        }
        context.startService(intent)
    }

    override fun hide() {
        visible = false
        val intent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        }
        context.startService(intent)
    }

    override fun isVisible(): Boolean {
        return visible
    }
}
