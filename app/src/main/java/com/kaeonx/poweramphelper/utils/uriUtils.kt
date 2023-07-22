package com.kaeonx.poweramphelper.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "uriUtils"

internal fun mapToIntListString(encodedUri: String) =
    encodedUri.map { c -> c.code }.joinToString(",")

internal fun mapFromIntLintString(intListString: String) =
    intListString.split(",").map { intString -> intString.toInt().toChar() }.joinToString("")

internal fun resolveUri(uri: Uri, appendUnencodedPath: String): Uri {
    return Uri.parse(
        uri.toString() + Uri.encode("/$appendUnencodedPath")
    )
}

internal fun openAudioPlayer(context: Context, uri: Uri) {
    // Mechanism documented @ https://developer.android.com/training/basics/intents/sending
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "audio/mpeg")
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Define what your app should do if no activity can handle the intent.
        Log.e(TAG, "No music players exist on the device?")
    }
}