package io.github.aedev.flow.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** A content URI other apps can read for [file], which must sit in a cache folder the provider lists. */
fun sharedFileUri(
    context: Context,
    file: File,
): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
