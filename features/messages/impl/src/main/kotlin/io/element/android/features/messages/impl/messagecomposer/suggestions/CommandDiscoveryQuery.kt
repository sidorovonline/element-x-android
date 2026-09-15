/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

/** The only composer data allowed across any command discovery boundary. */
internal data class CommandDiscoveryQuery(val name: String, val completeArguments: Boolean) {
    companion object {
        fun fromDraft(draft: String): CommandDiscoveryQuery? {
            if (draft.length > 640 || draft.any { it.isISOControl() && it != '\t' }) return null
            val end = draft.indexOfFirst { it.isWhitespace() }.let { if (it < 0) draft.length else it }
            val name = draft.substring(0, end).lowercase()
            if (!name.matches(Regex("(?:[a-z][a-z0-9_-]{0,63})?"))) return null
            return CommandDiscoveryQuery(name, end < draft.length && name.isNotEmpty())
        }
    }

    // Filtering arguments is strictly local. The original composer text is
    // neither changed for sending nor retained by either discovery consumer.
    fun matches(command: String, draft: String): Boolean {
        val query = if (completeArguments) name + " " + draft.substring(name.length).trimStart() else name
        val candidate = command.removePrefix("/")
        return candidate.startsWith(query, ignoreCase = true) || ' ' !in candidate
    }
}
