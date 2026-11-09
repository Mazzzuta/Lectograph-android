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
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
    }
    @Test fun addedSubjectRoutesNamedRecordingToThatSubject() {
        compose.onNodeWithText("Запись сохранится в черновиках").assertExists()
        compose.onNode(hasText("Название") and hasSetTextAction()).performTextInput("Первая лекция")
        compose.onNode(hasText("Предмет") and hasSetTextAction()).performTextInput("Биология")
        compose.onNodeWithText("Запись сохранится в черновиках").assertExists()
        compose.onNodeWithContentDescription("Добавить предмет").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Запись сохранится в предмете «Биология»").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Добавить предмет").assertIsNotEnabled()
        LectureStore(compose.activity).use { assertTrue("Биология" in it.subjects()) }
    }
    @Test fun swipesMoveBetweenMainTabsInBothDirections() {
        compose.onNode(hasText("Запись") and isSelected()).assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.onNode(hasText("Предметы") and isSelected()).assertExists()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.onNode(hasText("Запись") and isSelected()).assertExists()
    }
    @Test fun subjectsHaveOwnNavigationAndRecordingControlsAreSeparate() {
        compose.onNodeWithContentDescription("Начать запись").assertIsDisplayed()
        compose.onNodeWithText("Предметы").performClick()
        compose.onNodeWithContentDescription("Начать запись").assertDoesNotExist()
        compose.onNodeWithContentDescription("Добавить предмет").performClick()
        compose.onNodeWithText("Название предмета").performTextInput("Астрономия")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Астрономия").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Астрономия").performClick()
        compose.onNodeWithContentDescription("Редактировать предмет").performClick()
        compose.onNodeWithText("Название предмета").performTextClearance()
        compose.onNodeWithText("Название предмета").performTextInput("Космос")
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Космос").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Космос").performTouchInput { longClick() }
        compose.onNodeWithText("Удалить предмет").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Космос").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Запись", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Начать запись").assertExists()
    }
    @Test fun notesPersistWhenReturningToLibrary() {
        open("Заметки")
        compose.onNodeWithText("Мои заметки").performScrollTo().performTextInput("Проверить определение")
        compose.onNodeWithText("Предметы").performClick()
        compose.waitUntil(5000) { LectureStore(compose.activity).use { it.get(id)?.notes == "Проверить определение" } }
    }
    @Test fun transcriptIsContinuousByDefaultAndTimedFragmentsRemainAvailable() {
        LectureStore(compose.activity).use {
            it.saveTranscript(id, listOf(TranscriptSegment(0, 1500, "Первая фраза."), TranscriptSegment(2000, 4000, "Вторая фраза.")))
        }
        open("Текст")
        compose.waitUntil(5000) {
            runCatching { compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Первая фраза. Вторая фраза.")) }.isSuccess
        }
        compose.onNodeWithText("Первая фраза. Вторая фраза.").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Показать фрагменты с таймингами"))
        compose.onNodeWithText("Показать фрагменты с таймингами").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("00:00:02"))
        compose.onNodeWithText("00:00:02").assertHasClickAction()
        compose.onNodeWithText("Вторая фраза.").assertExists()
    }
    @Test fun editingDraftPreservesLatestNotes() {
        open("Заметки")
        compose.onNodeWithText("Мои заметки").performScrollTo().performTextInput("Моя заметка")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Изменить название и предмет"))
        compose.onNodeWithContentDescription("Изменить название и предмет").performClick()
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
