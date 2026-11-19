package dev.sagron.zerotrustcamera.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import dev.sagron.zerotrustcamera.R

/**
 * The ZT Camera home screen widget, in the shared Zero-Trust widget look
 * (Zero-Trust-Core/android-widget-kit): Private shot, Camera, Scan code and
 * Document. Buttons only: it never shows a photo or anything about one.
 * It shows as many buttons as its width fits, Private shot first.
 */
class CameraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, build(context, manager.getAppWidgetOptions(id))) }
    }

    /** Resized: redraw with what now fits. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) {
        manager.updateAppWidget(id, build(context, newOptions))
    }

    private fun build(context: Context, options: Bundle?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_camera)
        // 0 until the launcher has measured the widget: show everything.
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0
        val buttons = when {
            width == 0 || width >= 250 -> 4
            width >= 180 -> 3
            width >= 110 -> 2
            else -> 1
        }
        val tall = height == 0 || height >= 110
        val labels = buttons > 1 && (height == 0 || height >= 76)

        views.setViewVisibility(R.id.header, if (tall && buttons > 1) View.VISIBLE else View.GONE)
        TILES.forEachIndexed { i, tile ->
            views.setViewVisibility(tile.id, if (i < buttons) View.VISIBLE else View.GONE)
            views.setViewVisibility(tile.label, if (labels) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(tile.id, open(context, i, tile.activity))
        }
        views.setOnClickPendingIntent(R.id.header, open(context, TILES.size, CAMERA))
        return views
    }

    /** Opens one of this app's own screens (an explicit component, so nothing else can catch it). */
    private fun open(context: Context, request: Int, activity: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(context.packageName, activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private class Tile(val id: Int, val label: Int, val activity: String)

    private companion object {
        const val CAPTURE = "dev.sagron.zerotrustcamera.capture"
        const val CAMERA = "$CAPTURE.ui.activities.MainActivity"

        val TILES = listOf(
            Tile(R.id.private_shot, R.id.private_shot_label, "$CAPTURE.zt.PrivateShotActivity"),
            Tile(R.id.camera, R.id.camera_label, CAMERA),
            Tile(R.id.scan_code, R.id.scan_code_label, "$CAPTURE.ui.activities.QrTile"),
            Tile(R.id.scan_document, R.id.scan_document_label, "$CAPTURE.zt.DocScanActivity"),
        )
    }
}
