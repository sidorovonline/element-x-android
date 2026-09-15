/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.suggestions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CommandDiscoveryQueryTest {
    @Test
    fun `all whitespace and controls keep arguments outside discovery`() {
        val separators = (0..65535).map(Int::toChar).filter { it.isWhitespace() || it.isISOControl() }
        for (separator in separators) {
            val draft = "model" + separator + "owned-unsent-sentinel"
            val query = CommandDiscoveryQuery.fromDraft(draft)
            if (query != null) {
                assertThat(query.name).isEqualTo("model")
                assertThat(query.completeArguments).isTrue()
            }
        }
    }

    @Test
    fun `malformed long multiline and non command tokens never become discovery queries`() {
        for (draft in listOf("mo\u0000del secret", "model\nsecret", "model\rsecret", "model/secret", "model\u200Bsecret",
            "model".repeat(200), "model " + "x".repeat(640), "!model secret", "模型 secret", "model\uD800secret")) {
            assertThat(CommandDiscoveryQuery.fromDraft(draft)).isNull()
        }
        assertThat(CommandDiscoveryQuery.fromDraft("")?.name).isEmpty()
        assertThat(CommandDiscoveryQuery.fromDraft("MoDeL")?.name).isEqualTo("model")
    }

    @Test
    fun `argument matching remains local without changing literal composer text`() {
        val draft = "model\towned/se"
        val query = requireNotNull(CommandDiscoveryQuery.fromDraft(draft))
        assertThat(query.matches("/model owned/second", draft)).isTrue()
        assertThat(query.matches("/model owned/first", draft)).isFalse()
        assertThat(query.matches("/model", draft)).isTrue()
        assertThat(query.name).doesNotContain("owned")
    }
}
