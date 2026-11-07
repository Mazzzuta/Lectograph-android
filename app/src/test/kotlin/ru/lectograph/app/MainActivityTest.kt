package ru.lectograph.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import ru.lectograph.app.database.LectureStore
import ru.lectograph.app.models.TranscriptSegment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private var id = -1L
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() { seed() }
    }).around(compose)
    private fun seed() {
        LectureStore(RuntimeEnvironment.getApplication()).use {
            it.all().forEach { lecture -> it.delete(lecture.id) }
            id = it.create("Проверочная лекция", "", "test.m4a"); it.finish(id, 15000, "recorded")
        }
    }
    private fun open(tab: String) {
        compose.onNodeWithText("Черновики").performClick()
        compose.waitUntil(5000) {
            runCatching { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Проверочная лекция")) }.isSuccess
        }
        compose.onNodeWithText("Проверочная лекция").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        compose.onNodeWithText(tab).performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(5)
    }
    @Test fun notesPersistWhenReturningToLibrary() {
        open("Заметки")
        compose.onNodeWithText("Мои заметки").performScrollTo().performTextInput("Проверить определение")
        compose.onNodeWithText("Библиотека").performClick()
        compose.waitUntil(5000) { LectureStore(compose.activity).use { it.get(id)?.notes == "Проверить определение" } }
    }
    @Test fun editingDraftPreservesLatestNotes() {
        open("Заметки")
        compose.onNodeWithText("Мои заметки").performScrollTo().performTextInput("Моя заметка")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Изменить название и предмет"))
        compose.onNodeWithText("Изменить название и предмет").performClick()
        compose.onNodeWithText("Название лекции").performTextClearance()
        compose.onNodeWithText("Название лекции").performTextInput("Законы Ньютона")
        compose.onNodeWithText("Предмет (пусто — черновик)").performTextInput("Физика")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5000) { LectureStore(compose.activity).use { it.get(id)?.subject == "Физика" } }
        LectureStore(compose.activity).use { assertEquals("Законы Ньютона", it.get(id)?.title); assertEquals("Моя заметка", it.get(id)?.notes) }
    }
    @Test fun savedTestWorksWithoutGemini() {
        LectureStore(compose.activity).use {
            it.saveTranscript(id, listOf(TranscriptSegment(0, 1000, "Сила измеряется в ньютонах.")))
            it.saveMaterial(id, "test", "{\"questions\":[{\"question\":\"В чём измеряется сила?\",\"options\":[\"Ньютоны\",\"Метры\",\"Секунды\",\"Кельвины\"],\"correctIndex\":0,\"explanation\":\"Единица силы — ньютон.\",\"sourceQuote\":\"Сила измеряется в ньютонах.\"}]}", it.transcriptText(id))
        }
        open("Тест")
        compose.waitUntil(5000) { compose.onAllNodesWithText("Ньютоны").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ньютоны").performScrollTo().performClick()
        compose.onNodeWithText("Проверить ответы").performScrollTo().performClick()
        compose.onNodeWithText("Результат: 1 из 1").performScrollTo().assertIsDisplayed()
    }
}
