package dev.remotedictation.action.typetext

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import dev.remotedictation.core.TranscriptAction

/** Appends the transcript to the text field that has input focus, in whatever app is open. */
class TypeIntoFocusedField(private val service: AccessibilityService) : TranscriptAction {

    override fun perform(text: String): Boolean {
        val field = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!field.isEditable) return false

        val current = if (field.isShowingHintText) "" else field.text?.toString().orEmpty()
        val updated = when {
            current.isEmpty() || current.endsWith(' ') -> current + text
            else -> "$current $text"
        }
        val typed = field.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updated) },
        )
        if (typed) {
            field.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, updated.length)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, updated.length)
                },
            )
        }
        return typed
    }
}
