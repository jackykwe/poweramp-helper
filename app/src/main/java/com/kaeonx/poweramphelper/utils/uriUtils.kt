package com.kaeonx.poweramphelper.utils

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

private const val TAG = "uriUtils"

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

@Throws(IOException::class)
internal fun readTextFromUri(contentResolver: ContentResolver, uri: Uri): String {
    val stringBuilder = StringBuilder()
    contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                stringBuilder.append(line)
                line = reader.readLine()
            }
        }
    }
    return stringBuilder.toString()
}