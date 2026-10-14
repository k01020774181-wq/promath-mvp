package com.promath.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.promath.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PlatformState(
    val session: AuthSession? = null, val screen: String = "auth", val busy: Boolean = false,
    val error: String? = null, val courses: List<Course> = emptyList(), val course: Course? = null,
    val lessons: List<Lesson> = emptyList(), val lesson: Lesson? = null, val uploadProgress: Float? = null,
    val endpoint: String = ""
)
class PlatformViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SessionStore(application)
    val api = PlatformApi(application, store)
    private val mutable = MutableStateFlow(PlatformState(endpoint = store.endpoint))
    val state = mutable.asStateFlow()
    private val s get() = mutable.value
    init {
        store.load()?.let { session ->
            mutable.update { it.copy(session = session, screen = "home") }
            action {
                val user = parseAccount(JSONObject(api.request("/me", token = session.token)))
                val verified = session.copy(user = user); store.save(verified)
                mutable.update { it.copy(session = verified) }; loadCourses()
            }
        }
    }
    private fun action(block: suspend () -> Unit) {
        if (s.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) {
                if (e is ApiFailure && e.status == 401 && s.session != null) { store.save(null); mutable.update { PlatformState(endpoint = store.endpoint) } }
                val message = when (e) { is ApiFailure, is IllegalArgumentException -> e.message; else -> "서비스에 연결할 수 없습니다. 연결 상태를 확인해 주세요." }
                mutable.update { it.copy(error = message) }
            } finally { mutable.update { it.copy(busy = false, uploadProgress = null) } }
        }
    }
    private fun token() = s.session?.token ?: error("로그인이 필요합니다.")
    private fun loadCourses() { val rows = api.courses(token()); mutable.update { it.copy(courses = rows, course = it.course?.let { old -> rows.firstOrNull { row -> row.id == old.id } }) } }
    private fun loadLessons() {
        val id = s.course!!.id
        val rows = api.lessons(id, token())
        mutable.update { it.copy(lessons = rows, lesson = it.lesson?.let { old -> rows.firstOrNull { row -> row.id == old.id } }) }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun configure(value: String) = action {
        val endpoint = api.validateEndpoint(value)
        val previous = store.endpoint
        store.endpoint = endpoint
        try { api.request("/health") } catch (e: Exception) { store.endpoint = previous; throw e }
        store.save(null)
        mutable.update { PlatformState(endpoint = endpoint) }
    }
    fun authenticate(username: String, password: String, name: String? = null) = action {
        val body = JSONObject().put("username", username.trim()).put("password", password)
        if (name != null) body.put("name", name.trim())
        val session = parseAuth(JSONObject(api.request(if (name == null) "/auth/login" else "/auth/signup", "POST", body = body)))
        store.save(session); mutable.update { it.copy(session = session, screen = "home") }; loadCourses()
    }
    fun refresh() = action { loadCourses(); if (s.course != null) loadLessons() }
    fun logout() = action {
        try { api.request("/auth/logout", "POST", token()) }
        finally { store.save(null); mutable.update { PlatformState(endpoint = store.endpoint) } }
    }
    fun openCourse(course: Course) = action {
        mutable.update { it.copy(course = course, lessons = emptyList(), lesson = null) }
        loadLessons(); mutable.update { it.copy(screen = "course") }
    }
    fun openLesson(lesson: Lesson) { if (!s.busy) mutable.update { it.copy(lesson = lesson, screen = "lesson", error = null) } }
    fun back() { if (!s.busy) mutable.update { it.copy(screen = when (it.screen) { "player" -> "lesson"; "lesson" -> "course"; else -> "home" }, error = null) } }
    fun createCourse(title: String, description: String) = action {
        val row = parseCourse(JSONObject(api.request("/courses", "POST", token(), JSONObject().put("title", title.trim()).put("description", description.trim()))))
        loadCourses(); mutable.update { it.copy(course = it.courses.first { c -> c.id == row.id }, lessons = emptyList(), screen = "course") }
    }
    fun createLesson(title: String, stage: Int, position: Int) = action {
        api.request("/courses/${s.course!!.id}/lessons", "POST", token(), JSONObject().put("title", title.trim()).put("stage", stage).put("position", position))
        loadCourses(); loadLessons()
    }
    fun publishCourse(value: Boolean) = action { api.request("/courses/${s.course!!.id}", "PUT", token(), JSONObject().put("published", value)); loadCourses() }
    fun publishLesson(value: Boolean) = action { api.request("/lessons/${s.lesson!!.id}", "PUT", token(), JSONObject().put("published", value)); loadCourses(); loadLessons() }
    fun upload(uri: Uri) = action {
        val id = s.lesson!!.id
        mutable.update { it.copy(uploadProgress = 0f) }
        api.upload(id, uri, token()) { progress -> mutable.update { it.copy(uploadProgress = progress) } }
        loadLessons()
    }
    fun viewFile() = action {
        val lesson = s.lesson ?: return@action
        if (lesson.mime == "application/pdf") {
            val file = api.download(lesson, token())
            val context = getApplication<Application>()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) {
                try { context.startActivity(intent) } catch (e: android.content.ActivityNotFoundException) { mutable.update { it.copy(error = "PDF를 열 수 있는 앱을 설치해 주세요.") } }
            }
        } else mutable.update { it.copy(screen = "player") }
    }
}
