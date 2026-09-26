package io.github.aedev.flow.data.video.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File

/**
 * File operations on a stored download path, which is either an absolute file path or the
 * `content://` document of a file exported into a folder picked with the system picker.
 */
object DownloadFiles {
    private const val TAG = "DownloadFiles"
    private const val COPY_BUFFER_BYTES = 1 shl 20
    private const val TREE_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun isDocument(path: String): Boolean = path.startsWith(ContentResolver.SCHEME_CONTENT + "://")

    fun exists(
        context: Context,
        path: String,
    ): Boolean {
        if (!isDocument(path)) return File(path).exists()
        return runCatching {
            context.contentResolver
                .query(Uri.parse(path), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { it.moveToFirst() } == true
        }.getOrDefault(false)
    }

    fun displayName(
        context: Context,
        path: String,
    ): String? {
        if (!isDocument(path)) return File(path).name
        return runCatching {
            context.contentResolver
                .query(Uri.parse(path), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    /** Deletes an exported document; true when it is gone afterwards. */
    fun deleteDocument(
        context: Context,
        path: String,
    ): Boolean {
        if (!exists(context, path)) return true
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(path)) }
            .onFailure { Log.w(TAG, "Could not delete $path", it) }
        return !exists(context, path)
    }

    fun hasTreeAccess(
        context: Context,
        treeUri: String,
    ): Boolean {
        val uri = Uri.parse(treeUri)
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }

    fun releaseTree(
        context: Context,
        treeUri: String,
    ) {
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), TREE_FLAGS) }
    }

    /** The file-system path of a picked folder, when the provider exposes one. */
    fun treePath(treeUri: String): String? =
        runCatching {
            documentIdToPath(DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)), primaryRoot())
        }.getOrNull()

    /** The file-system path behind an exported document, so a folder scan can recognise it. */
    fun documentPath(path: String): String? =
        runCatching {
            documentIdToPath(DocumentsContract.getDocumentId(Uri.parse(path)), primaryRoot())
        }.getOrNull()

    /** Copies [source] into the picked folder [treeUri] and returns the new document, or null. */
    fun exportToTree(
        context: Context,
        source: File,
        treeUri: String,
    ): String? {
        val resolver = context.contentResolver
        val target =
            runCatching {
                val tree = Uri.parse(treeUri)
                val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(resolver, folder, mimeTypeOf(source), source.name)
            }.onFailure { Log.w(TAG, "Could not create ${source.name} in $treeUri", it) }
                .getOrNull() ?: return null
        val copied =
            runCatching {
                resolver.openOutputStream(target, "w")?.use { output ->
                    source.inputStream().use { it.copyTo(output, COPY_BUFFER_BYTES) }
                } != null
            }.onFailure { Log.w(TAG, "Could not copy ${source.name} to $target", it) }
                .getOrDefault(false)
        if (!copied) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            return null
        }
        return target.toString()
    }

    /** Moves [source] into [directory], copying when a rename cannot cross the two folders. */
    fun moveInto(
        source: File,
        directory: File,
    ): File? {
        val target = File(directory, source.name)
        if (source.renameTo(target)) return target
        return runCatching {
            source.copyTo(target, overwrite = true)
            source.delete()
            target
        }.onFailure { Log.w(TAG, "Could not move ${source.name} to $directory", it) }
            .getOrNull()
    }

    // The provider checks the display name's extension against this type, so it must come from the
    // same table or the file gets a second extension.
    private fun mimeTypeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    @Suppress("DEPRECATION")
    private fun primaryRoot(): String = Environment.getExternalStorageDirectory().absolutePath
}
