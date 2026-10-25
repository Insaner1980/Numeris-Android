package com.emmah.numeris

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertFalse

internal fun SemanticsNode.assertTextFits(message: String) {
    val layouts = mutableListOf<TextLayoutResult>()
    config
        .getOrNull(SemanticsActions.GetTextLayoutResult)
        ?.action
        ?.invoke(layouts)
    assertFalse("$message: no text layout was provided", layouts.isEmpty())
    layouts.forEach { layout ->
        // Compare painted line bounds; the paragraph can retain its wider layout constraints.
        val widthExcess =
            (0 until layout.lineCount).maxOfOrNull { line ->
                maxOf(layout.getLineRight(line) - layout.size.width, -layout.getLineLeft(line))
            } ?: 0f
        assertFalse(
            message,
            widthExcess > 1f ||
                layout.multiParagraph.height - layout.size.height > 1f ||
                layout.multiParagraph.didExceedMaxLines,
        )
    }
}
