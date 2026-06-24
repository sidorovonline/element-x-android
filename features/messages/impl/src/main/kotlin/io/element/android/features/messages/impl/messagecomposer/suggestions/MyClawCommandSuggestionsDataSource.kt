/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.slashcommands.api.SlashCommandSuggestion

class MyClawCommandSuggestionsDataSource(
    private val room: JoinedRoom,
    private val parser: MyClawCommandStateEventParser = MyClawCommandStateEventParser(),
) {
    suspend fun getSuggestions(): Result<List<SlashCommandSuggestion>> {
        return room.getCurrentStateEvents(MyClawCommandStateEventParser.MYCLAW_COMMANDS_EVENT_TYPE)
            .mapCatching(parser::parseAndMerge)
    }
}
