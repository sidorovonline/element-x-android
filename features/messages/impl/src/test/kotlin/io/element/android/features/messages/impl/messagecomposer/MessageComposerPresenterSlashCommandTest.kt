/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.features.messages.impl.messagecomposer

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.DeviceId
import io.element.android.libraries.matrix.api.room.RoomMembersState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import android.net.Uri
import app.cash.turbine.ReceiveTurbine
import com.google.common.truth.Truth.assertThat
import io.element.android.features.location.api.LocationService
import io.element.android.features.location.test.FakeLocationService
import io.element.android.features.messages.impl.FakeMessagesNavigator
import io.element.android.features.messages.impl.MessagesNavigator
import io.element.android.features.messages.impl.draft.ComposerDraftService
import io.element.android.features.messages.impl.draft.FakeComposerDraftService
import io.element.android.features.messages.impl.messagecomposer.suggestions.DshCommandSuggestionsDataSource
import io.element.android.features.messages.impl.messagecomposer.suggestions.MyClawCommandSuggestionsDataSource
import io.element.android.features.messages.impl.messagecomposer.suggestions.SuggestionsProcessor
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.features.messages.impl.utils.FakeMentionSpanFormatter
import io.element.android.features.messages.impl.utils.FakeTextPillificationHelper
import io.element.android.features.messages.impl.utils.TextPillificationHelper
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.to_device.CustomToDeviceEvent
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_FAILURE_REASON
import io.element.android.libraries.matrix.test.A_MESSAGE
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.permalink.FakePermalinkBuilder
import io.element.android.libraries.matrix.test.permalink.FakePermalinkParser
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.mediapickers.api.PickerProvider
import io.element.android.libraries.mediapickers.test.FakePickerProvider
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaPreProcessor
import io.element.android.libraries.mediaupload.api.MediaSenderFactory
import io.element.android.libraries.mediaupload.impl.DefaultMediaSender
import io.element.android.libraries.mediaupload.test.FakeMediaOptimizationConfigProvider
import io.element.android.libraries.mediaupload.test.FakeMediaPreProcessor
import io.element.android.libraries.mediaviewer.test.FakeLocalMediaFactory
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.permissions.test.FakePermissionsPresenter
import io.element.android.libraries.permissions.test.FakePermissionsPresenterFactory
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.libraries.preferences.test.InMemorySessionPreferencesStore
import io.element.android.libraries.push.test.notifications.conversations.FakeNotificationConversationService
import io.element.android.libraries.slashcommands.api.SlashCommand
import io.element.android.libraries.slashcommands.api.SlashCommandService
import io.element.android.libraries.slashcommands.api.SlashCommandSuggestion
import io.element.android.libraries.slashcommands.test.FakeSlashCommandService
import io.element.android.libraries.textcomposer.mentions.MentionSpanProvider
import io.element.android.libraries.textcomposer.mentions.MentionSpanTheme
import io.element.android.libraries.textcomposer.mentions.ResolvedSuggestion
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.Suggestion
import io.element.android.libraries.textcomposer.model.SuggestionType
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test

class MessageComposerPresenterSlashCommandTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val pickerProvider = FakePickerProvider().apply {
        givenResult(mockk()) // Uri is not available in JVM, so the only way to have a non-null Uri is using Mockk
    }
    private val mediaPreProcessor = FakeMediaPreProcessor()
    private val snackbarDispatcher = SnackbarDispatcher()
    private val mockMediaUrl: Uri = mockk("localMediaUri")
    private val localMediaFactory = FakeLocalMediaFactory(mockMediaUrl)
    private val analyticsService = FakeAnalyticsService()
    private val notificationConversationService = FakeNotificationConversationService()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            val initialState = awaitFirstItem()
            assertThat(initialState.isFullScreen).isFalse()
            assertThat(initialState.textEditorState.messageHtml()).isEmpty()
            assertThat(initialState.mode).isEqualTo(MessageComposerMode.Normal)
            assertThat(initialState.showAttachmentSourcePicker).isFalse()
            assertThat(initialState.canShareLocation).isTrue()
        }
    }

    @Test
    fun `present - slash command error sets failure`() = runTest {
        val presenter = createPresenter(
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.ErrorUnknownSlashCommand(A_FAILURE_REASON) }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            val errorState = awaitItem()
            assertThat(errorState.slashCommandAction.isFailure()).isTrue()
            assertThat(errorState.slashCommandAction.errorOrNull()?.message).isEqualTo(A_FAILURE_REASON)
            // Composer should not be reset when command is an error
            assertThat(errorState.textEditorState.messageHtml()).isEqualTo(A_MESSAGE)
            // Close the error
            errorState.eventSink(MessageComposerEvent.ClearSlashError)
            val finalState = awaitItem()
            assertThat(finalState.slashCommandAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - slash command navigation ShowUser navigates to member and resets composer`() = runTest {
        val navigateToMember = lambdaRecorder<UserId, Unit> {}
        val navigator = FakeMessagesNavigator(navigateToMemberLambda = navigateToMember)
        val presenter = createPresenter(
            navigator = navigator,
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.ShowUser(A_USER_ID) }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            advanceUntilIdle()
            // navigation should be invoked and composer reset
            navigateToMember.assertions().isCalledOnce().with(value(A_USER_ID))
            assertThat(initialState.textEditorState.messageHtml()).isEmpty()
        }
    }

    @Test
    fun `present - slash command navigation DevTools navigates to developer settings and resets composer`() = runTest {
        val navigateToDev = lambdaRecorder<Unit> { }
        val navigator = FakeMessagesNavigator(navigateToDeveloperSettingsLambda = navigateToDev)
        val presenter = createPresenter(
            navigator = navigator,
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.DevTools }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            advanceUntilIdle()
            navigateToDev.assertions().isCalledOnce()
            assertThat(initialState.textEditorState.messageHtml()).isEmpty()
        }
    }

    @Test
    fun `present - slash command send message proceeds and resets composer`() = runTest {
        val presenter = createPresenter(
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.SendPlainText(A_MESSAGE) },
                proceedSendMessageResult = { _, _ -> Result.success(Unit) }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            advanceUntilIdle()
            // Composer reset after successful slash send
            assertThat(initialState.textEditorState.messageHtml()).isEmpty()
            // Ensure no failure
            assertThat(initialState.slashCommandAction.isFailure()).isFalse()
        }
    }

    @Test
    fun `present - slash command send message failure sets failure state`() = runTest {
        val presenter = createPresenter(
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.SendPlainText("A_MESSAGE") },
                proceedSendMessageResult = { _, _ -> Result.failure(Exception(A_FAILURE_REASON)) }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            val failureState = awaitItem()
            assertThat(failureState.slashCommandAction.isFailure()).isTrue()
            assertThat(failureState.slashCommandAction.errorOrNull()?.message).isEqualTo(A_FAILURE_REASON)
            // Clear the error
            failureState.eventSink(MessageComposerEvent.ClearSlashError)
            val finalState = awaitItem()
            assertThat(finalState.slashCommandAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - slash command admin proceeds and resets state on success`() = runTest {
        val presenter = createPresenter(
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.BanUser(A_USER_ID, null) },
                proceedAdminResult = { _ -> Result.success(Unit) }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            val loadingState = awaitItem()
            assertThat(loadingState.slashCommandAction.isLoading()).isTrue()
            val successState = awaitItem()
            // After success, state should be Uninitialized
            assertThat(successState.slashCommandAction.isUninitialized()).isTrue()
            assertThat(successState.textEditorState.messageHtml()).isEmpty()
        }
    }

    @Test
    fun `present - slash command admin proceeds and emit failure on error`() = runTest {
        val presenter = createPresenter(
            slashCommandService = FakeSlashCommandService(
                parseResult = { _, _, _ -> SlashCommand.BanUser(A_USER_ID, null) },
                proceedAdminResult = { _ -> Result.failure(Exception(A_FAILURE_REASON)) }
            )
        )
        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml(A_MESSAGE)
            initialState.eventSink(MessageComposerEvent.SendMessage)
            val loadingState = awaitItem()
            assertThat(loadingState.slashCommandAction.isLoading()).isTrue()
            val failureState = awaitItem()
            assertThat(failureState.slashCommandAction.isFailure()).isTrue()
            assertThat(failureState.slashCommandAction.errorOrNull()?.message).isEqualTo(A_FAILURE_REASON)
            // Clear error
            failureState.eventSink(MessageComposerEvent.ClearSlashError)
            val finalState = awaitItem()
            assertThat(finalState.slashCommandAction.isUninitialized()).isTrue()
        }
    }

    @Test
    fun `present - selected discovered slash command is sent as a normal message`() = runTest {
        val matrixClient = FakeMatrixClient(
            sessionId = A_SESSION_ID,
            deviceId = A_DEVICE_ID,
        )
        val parsedDiscoveredCommandNames = mutableListOf<Set<String>>()
        val sentMessages = mutableListOf<String>()
        val timeline = FakeTimeline().apply {
            sendMessageLambda = { body, _, _, _, _ ->
                sentMessages += body
                Result.success(Unit)
            }
        }
        val slashCommandService = FakeSlashCommandService(
            getSuggestionsWithDiscoveredResult = { _, _, discoveredCommands ->
                discoveredCommands
            },
            parseWithDiscoveredResult = { _, _, _, discoveredCommandNames ->
                parsedDiscoveredCommandNames += discoveredCommandNames
                SlashCommand.NotACommand
            },
        )
        val presenter = createPresenter(
            room = aDmRoom(timeline),
            slashCommandService = slashCommandService,
            myClawCommandSuggestionsDataSource = MyClawCommandSuggestionsDataSource(matrixClient),
        )

        presenter.test {
            val initialState = awaitFirstItem()
            initialState.textEditorState.setHtml("/sta")
            initialState.eventSink(MessageComposerEvent.SuggestionReceived(Suggestion(0, 4, SuggestionType.Command, "sta")))
            advanceTimeBy(201)
            runCurrent()

            val sent = matrixClient.sentCustomToDeviceEvents.single()
            val sentContent = Json.parseToJsonElement(sent.content).jsonObject
            val txnId = sentContent["txn_id"]!!.jsonPrimitive.contentOrNull!!
            matrixClient.emitCustomToDeviceEvent(
                CustomToDeviceEvent(
                    eventType = MyClawCommandSuggestionsDataSource.RESPONSE_TYPE,
                    sender = A_USER_ID_2,
                    content = """
                        {
                          "version": 1,
                          "txn_id": "$txnId",
                          "room_id": "${A_ROOM_ID.value}",
                          "query": "sta",
                          "commands": [
                            { "name": "status", "description": "Show MyClaw state", "argument_hint": null }
                          ]
                        }
                    """.trimIndent(),
                    encrypted = false,
                )
            )
            runCurrent()

            var suggestionsState = awaitItem()
            while (suggestionsState.suggestions.isEmpty()) {
                suggestionsState = awaitItem()
            }
            assertThat(suggestionsState.suggestions)
                .containsExactly(ResolvedSuggestion.Command(SlashCommandSuggestion("/status", null, "Show MyClaw state")))

            suggestionsState.eventSink(MessageComposerEvent.InsertSuggestion(suggestionsState.suggestions.single()))
            runCurrent()
            assertThat(suggestionsState.textEditorState.messageHtml()).isEqualTo("/status ")

            suggestionsState.eventSink(MessageComposerEvent.SendMessage)
            advanceUntilIdle()

            assertThat(parsedDiscoveredCommandNames).containsExactly(setOf("status"))
            assertThat(sentMessages).containsExactly("/status ")
            val resetState = expectMostRecentItem()
            assertThat(resetState.textEditorState.messageHtml()).isEmpty()
            assertThat(resetState.slashCommandAction.isFailure()).isFalse()
        }
    }

    @Test
    fun `signed DSH discovery without room state populates composer and selection stays unsent until submission`() = runTest {
        val transport = FakeMatrixClient(sessionId = A_SESSION_ID, deviceId = A_DEVICE_ID)
        val bot = UserId("@dsh:${A_SESSION_ID.value.substringAfter(':')}")
        val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val client = object : MatrixClient by transport {
            override suspend fun verifyDeviceSignature(userId: UserId, deviceId: DeviceId, message: String, signature: String): Boolean =
                userId == bot && deviceId.value == "DSH" && Signature.getInstance("Ed25519").run {
                    initVerify(key.public)
                    update(message.toByteArray())
                    verify(Base64.getDecoder().decode(signature))
                }
        }
        val sent = mutableListOf<String>()
        val timeline = FakeTimeline().apply {
            sendMessageLambda = { body, _, _, _, _ -> sent += body; Result.success(Unit) }
        }
        val room = aDmRoom(timeline).apply {
            baseRoom.membersStateFlow.value = RoomMembersState.Ready(persistentListOf(aRoomMember(bot)))
        }
        val service = FakeSlashCommandService(
            getSuggestionsWithDiscoveredResult = { _, _, discovered -> discovered },
            parseWithDiscoveredResult = { _, _, _, names ->
                assertThat(names).contains("visibility")
                SlashCommand.NotACommand
            },
        )
        createPresenter(room = room, slashCommandService = service, dshCommandSuggestionsDataSource = DshCommandSuggestionsDataSource(client)).test {
            val initial = awaitFirstItem()
            initial.textEditorState.setHtml("/visibility t")
            initial.eventSink(MessageComposerEvent.SuggestionReceived(Suggestion(0, 13, SuggestionType.Command, "visibility t")))
            advanceTimeBy(201)
            runCurrent()
            val request = Json.parseToJsonElement(transport.sentCustomToDeviceEvents.single().content).jsonObject
            assertThat(request).doesNotContainKey("command")
            assertThat(request).doesNotContainKey("query")
            val payload = JsonObject(request + mapOf(
                "type" to JsonPrimitive(DshCommandSuggestionsDataSource.RESPONSE_TYPE),
                "sender" to JsonPrimitive(bot.value), "device_id" to JsonPrimitive("DSH"),
                "recipient" to JsonPrimitive(A_SESSION_ID.value), "request_device" to JsonPrimitive(A_DEVICE_ID.value),
                "issued_at" to JsonPrimitive(System.currentTimeMillis()), "expires_at" to JsonPrimitive(System.currentTimeMillis() + 15000),
                "definitions" to Json.parseToJsonElement("[]"),
                "commands" to Json.parseToJsonElement("""[{"name":"visibility tools","description":"Show tool details"},
                    {"name":"visibility final","description":"Show final answers"}]"""),
            )).toString()
            val signature = Signature.getInstance("Ed25519").run {
                initSign(key.private)
                update(payload.toByteArray())
                Base64.getEncoder().withoutPadding().encodeToString(sign())
            }
            transport.emitCustomToDeviceEvent(CustomToDeviceEvent(DshCommandSuggestionsDataSource.RESPONSE_TYPE, bot, JsonObject(mapOf(
                "version" to JsonPrimitive(3), "device_id" to JsonPrimitive("DSH"),
                "payload" to JsonPrimitive(payload), "signature" to JsonPrimitive(signature),
            )).toString(), false))
            advanceTimeBy(2001)
            runCurrent()
            val menu = expectMostRecentItem()
            assertThat(menu.suggestions).containsExactly(ResolvedSuggestion.Command(SlashCommandSuggestion("/visibility tools", null, "Show tool details")))
            menu.eventSink(MessageComposerEvent.InsertSuggestion(menu.suggestions.single()))
            runCurrent()
            assertThat(menu.textEditorState.messageHtml()).isEqualTo("/visibility tools ")
            assertThat(sent).isEmpty()
            menu.eventSink(MessageComposerEvent.SendMessage)
            advanceUntilIdle()
            assertThat(sent).containsExactly("/visibility tools ")
            val reset = expectMostRecentItem()
            assertThat(reset.textEditorState.messageHtml()).isEmpty()
        }
    }

    private fun TestScope.createPresenter(
        room: JoinedRoom = FakeJoinedRoom(
            typingNoticeResult = { Result.success(Unit) }
        ),
        timeline: Timeline = room.liveTimeline,
        navigator: MessagesNavigator = FakeMessagesNavigator(),
        pickerProvider: PickerProvider = this@MessageComposerPresenterSlashCommandTest.pickerProvider,
        locationService: LocationService = FakeLocationService(true),
        sessionPreferencesStore: SessionPreferencesStore = InMemorySessionPreferencesStore(),
        mediaPreProcessor: MediaPreProcessor = this@MessageComposerPresenterSlashCommandTest.mediaPreProcessor,
        snackbarDispatcher: SnackbarDispatcher = this@MessageComposerPresenterSlashCommandTest.snackbarDispatcher,
        permissionPresenter: PermissionsPresenter = FakePermissionsPresenter(),
        permalinkBuilder: PermalinkBuilder = FakePermalinkBuilder(),
        permalinkParser: PermalinkParser = FakePermalinkParser(),
        mentionSpanProvider: MentionSpanProvider = MentionSpanProvider(
            permalinkParser = permalinkParser,
            mentionSpanFormatter = FakeMentionSpanFormatter(),
            mentionSpanTheme = MentionSpanTheme(A_USER_ID)
        ),
        textPillificationHelper: TextPillificationHelper = FakeTextPillificationHelper(),
        isRichTextEditorEnabled: Boolean = true,
        draftService: ComposerDraftService = FakeComposerDraftService(),
        mediaOptimizationConfigProvider: FakeMediaOptimizationConfigProvider = FakeMediaOptimizationConfigProvider(),
        threadRoot: ThreadId? = null,
        slashCommandService: SlashCommandService = FakeSlashCommandService(),
        myClawCommandSuggestionsDataSource: MyClawCommandSuggestionsDataSource = MyClawCommandSuggestionsDataSource(FakeMatrixClient()),
        dshCommandSuggestionsDataSource: DshCommandSuggestionsDataSource = DshCommandSuggestionsDataSource(FakeMatrixClient()),
    ) = MessageComposerPresenter(
        navigator = navigator,
        sessionCoroutineScope = this,
        threadRoot = threadRoot,
        room = room,
        mediaPickerProvider = pickerProvider,
        sessionPreferencesStore = sessionPreferencesStore,
        localMediaFactory = localMediaFactory,
        mediaSenderFactory = MediaSenderFactory { timelineMode ->
            DefaultMediaSender(
                preProcessor = mediaPreProcessor,
                room = room,
                timelineMode = timelineMode,
                mediaOptimizationConfigProvider = {
                    MediaOptimizationConfig(
                        compressImages = true,
                        videoCompressionPreset = VideoCompressionPreset.STANDARD
                    )
                }
            )
        },
        snackbarDispatcher = snackbarDispatcher,
        analyticsService = analyticsService,
        locationService = locationService,
        messageComposerContext = DefaultMessageComposerContext(),
        richTextEditorStateFactory = TestRichTextEditorStateFactory(),
        roomAliasSuggestionsDataSource = FakeRoomAliasSuggestionsDataSource(),
        myClawCommandSuggestionsDataSource = myClawCommandSuggestionsDataSource,
        dshCommandSuggestionsDataSource = dshCommandSuggestionsDataSource,
        permissionsPresenterFactory = FakePermissionsPresenterFactory(permissionPresenter),
        permalinkParser = permalinkParser,
        permalinkBuilder = permalinkBuilder,
        timelineController = TimelineController(room, timeline),
        draftService = draftService,
        mentionSpanProvider = mentionSpanProvider,
        pillificationHelper = textPillificationHelper,
        suggestionsProcessor = SuggestionsProcessor(slashCommandService = slashCommandService),
        mediaOptimizationConfigProvider = mediaOptimizationConfigProvider,
        notificationConversationService = notificationConversationService,
        slashCommandService = slashCommandService,
    ).apply {
        isTesting = true
        showTextFormatting = isRichTextEditorEnabled
    }

    private suspend fun <T> ReceiveTurbine<T>.awaitFirstItem(): T {
        skipItems(1)
        return awaitItem()
    }

    private fun aDmRoom(timeline: Timeline = FakeTimeline()): FakeJoinedRoom {
        return FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                sessionId = A_SESSION_ID,
                roomId = A_ROOM_ID,
                updateMembersResult = {},
                getDirectRoomMemberResult = {
                    aRoomMember(userId = A_USER_ID_2)
                },
            ),
            liveTimeline = timeline,
            typingNoticeResult = { Result.success(Unit) },
        )
    }
}
