/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import io.element.android.libraries.matrix.api.room.RoomStateEvent
import io.element.android.libraries.slashcommands.api.SlashCommandSuggestion
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class MyClawCommandStateEvent(
    val stateKey: String,
    val commands: List<SlashCommandSuggestion>,
)

class MyClawCommandStateEventParser {
    fun parse(stateEvent: RoomStateEvent): MyClawCommandStateEvent? {
        if (stateEvent.type != MYCLAW_COMMANDS_EVENT_TYPE) return null
        val content = runCatching { Json.decodeFromString<JsonElement>(stateEvent.content).jsonObject }.getOrNull() ?: return null
        if (content.intField("version") != SUPPORTED_VERSION) return null

        val commands = runCatching { content["commands"]?.jsonArray }
            .getOrNull()
            ?.mapNotNull { it.jsonObjectOrNull()?.toSlashCommandSuggestion() }
            .orEmpty()

        if (commands.isEmpty()) return null
        return MyClawCommandStateEvent(
            stateKey = stateEvent.stateKey,
            commands = commands,
        )
    }

    fun parseAndMerge(stateEvents: List<RoomStateEvent>): List<SlashCommandSuggestion> {
        val commandByName = LinkedHashMap<String, SlashCommandSuggestion>()
        stateEvents
            .mapNotNull(::parse)
            .sortedBy { it.stateKey }
            .flatMap { it.commands }
            .forEach { suggestion ->
                commandByName.putIfAbsent(suggestion.command.removePrefix("/").lowercase(), suggestion)
            }
        return commandByName.values.toList()
    }

    private fun JsonObject.toSlashCommandSuggestion(): SlashCommandSuggestion? {
        val name = stringField("name")
            ?.trim()
            ?.dropWhile { it == '/' }
            ?.takeIf { it.isNotEmpty() && it.none(Char::isWhitespace) }
            ?: return null
        val description = stringField("description")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val argumentHint = stringField("argument_hint")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return SlashCommandSuggestion(
            command = "/$name",
            parameters = argumentHint,
            description = description,
        )
    }

    private fun JsonObject.stringField(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.intField(name: String): Int? = (get(name) as? JsonPrimitive)?.intOrNull

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

    companion object {
        const val MYCLAW_COMMANDS_EVENT_TYPE = "icu.victor.myclaw.commands"
        private const val SUPPORTED_VERSION = 1
    }
}
