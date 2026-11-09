package ru.lectograph.app.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.lectograph.app.ai.StudyService
import ru.lectograph.app.database.LectureStore
import ru.lectograph.app.models.Lecture
import ru.lectograph.app.models.TranscriptSegment
import ru.lectograph.app.recording.RecordingService
import ru.lectograph.app.transcription.TranscriptionService
import java.io.File

data class LibraryState(
    val lectures: List<Lecture> = emptyList(), val subjects: List<String> = emptyList(),
    val transcript: List<TranscriptSegment> = emptyList(), val materials: Map<String, String> = emptyMap(),
    val job: LectureStore.Job = LectureStore.Job("idle", 0, ""), val error: String? = null
)

class LecturesViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val store = LectureStore(application)
    private val writes = Mutex()
    private val preferences = application.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
    private val mutableLibrary = MutableStateFlow(LibraryState())
    val library = mutableLibrary.asStateFlow()
    val section = saved.getStateFlow("section", "record")
    val subject = saved.getStateFlow("subject", "")
    val selected = saved.getStateFlow("lecture", -1L)
    val tab = saved.getStateFlow("tab", "text")
    private val mutableTheme = MutableStateFlow(preferences.getString("theme", "dark") ?: "dark")
    val theme = mutableTheme.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (RecordingService.activeId == -1L) store.recoverInterrupted()
            if (!TranscriptionService.running) store.recoverTranscriptions()
            reload()
        }
        viewModelScope.launch { selected.collect { reload() } }
        viewModelScope.launch {
            var previous = RecordingService.activeId
            RecordingService.state.map { it.id }.distinctUntilChanged().collect { id ->
                if (previous != -1L && id == -1L) openLecture(previous)
                previous = id; reload()
            }
        }
        viewModelScope.launch { TranscriptionService.state.map { it.running }.distinctUntilChanged().collect { reload() } }
        viewModelScope.launch { StudyService.state.map { it.running }.distinctUntilChanged().collect { reload() } }
    }
    private suspend fun reload() = withContext(Dispatchers.IO) {
        try {
            val id = selected.value
            val data = LibraryState(store.all(), store.subjects(), store.transcript(id),
                listOf("study", "test", "question").associateWith { store.material(id, it) }, store.job(id))
            if (id == selected.value) mutableLibrary.value = data
        } catch (e: Exception) { mutableLibrary.update { it.copy(error = e.message ?: "Не удалось прочитать библиотеку") } }
    }
    fun refresh() { viewModelScope.launch { reload() } }
    fun navigate(destination: String) { saved["section"] = destination; saved["subject"] = ""; saved["lecture"] = -1L }
    fun openSubject(name: String) { saved["section"] = "library"; saved["subject"] = name; saved["lecture"] = -1L }
    fun openLecture(id: Long) {
        if (selected.value != id) saved["tab"] = "text"
        saved["lecture"] = id
        library.value.lectures.firstOrNull { it.id == id }?.let { saved["section"] = if (it.subject.isEmpty()) "drafts" else "library" }
        refresh()
    }
    fun closeLecture() { saved["lecture"] = -1L }
    fun selectTab(value: String) { saved["tab"] = value }
    fun theme(value: String) { preferences.edit().putString("theme", value).apply(); mutableTheme.value = value }
    private fun write(block: () -> Unit) { viewModelScope.launch {
        try { writes.withLock { withContext(Dispatchers.IO) { block() }; reload() } }
        catch (e: Exception) { mutableLibrary.update { it.copy(error = e.message ?: "Не удалось сохранить изменения") } }
    } }
    suspend fun subjectTests(subject: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        store.all().filter { it.subject == subject }.map { it.title to store.material(it.id, "test") }
    }
    fun addSubject(name: String) = write { store.addSubject(name) }
    fun renameSubject(old: String, name: String) = write {
        store.renameSubject(old, name)
        viewModelScope.launch(Dispatchers.Main) { if (subject.value == old) saved["subject"] = name.trim() }
    }
    fun deleteSubject(name: String) = write {
        store.deleteSubject(name)
        viewModelScope.launch(Dispatchers.Main) { if (subject.value == name) saved["subject"] = "" }
    }
    fun edit(id: Long, title: String, subject: String) = write {
        val current = store.get(id) ?: return@write
        store.edit(id, title, subject, current.notes)
        viewModelScope.launch(Dispatchers.Main) { if (selected.value == id) saved["section"] = if (subject.isBlank()) "drafts" else "library" }
    }
    fun notes(id: Long, notes: String) = write {
        val current = store.get(id) ?: return@write
        store.edit(id, current.title, current.subject, notes)
    }
    fun delete(lecture: Lecture) = write {
        check(!(TranscriptionService.running && TranscriptionService.lectureId == lecture.id) && !(StudyService.running && StudyService.lectureId == lecture.id)) { "Сначала дождитесь окончания обработки" }
        val file = File(File(getApplication<Application>().filesDir, "audio"), lecture.audio)
        check(lecture.audio.isEmpty() || !file.exists() || file.delete()) { "Не удалось удалить аудиофайл" }
        store.delete(lecture.id)
        viewModelScope.launch(Dispatchers.Main) { if (selected.value == lecture.id) closeLecture() }
    }
    override fun onCleared() { store.close(); super.onCleared() }
}
