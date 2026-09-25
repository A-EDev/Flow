package io.github.aedev.flow.ui.screens.music.collection

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import io.github.aedev.flow.ui.components.shared.quickactions.QuickActionUndo

/** A snackbar line from a music page, with the Undo it offers if any. */
data class CollectionMessage(
    @param:StringRes val stringRes: Int = 0,
    @param:PluralsRes val pluralRes: Int = 0,
    val count: Int = 0,
    val args: List<Any> = emptyList(),
    val undo: QuickActionUndo? = null,
) {
    fun resolve(context: Context): String =
        when {
            pluralRes != 0 -> context.resources.getQuantityString(pluralRes, count, *args.toTypedArray())
            args.isEmpty() -> context.getString(stringRes)
            else -> context.getString(stringRes, *args.toTypedArray())
        }
}
