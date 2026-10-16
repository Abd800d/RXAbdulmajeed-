package com.almakhedhi.rxreader.feature.search

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** "Search in Google" via a Custom Tab, with a plain VIEW fallback. */
object GoogleSearch {

    fun queryUrl(query: String): String =
        "https://www.google.com/search?q=" + Uri.encode(query.trim())

    fun launchQuery(context: Context, query: String) {
        launchUrl(context, queryUrl(query))
    }

    fun launchUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        try {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        } catch (_: Exception) {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
