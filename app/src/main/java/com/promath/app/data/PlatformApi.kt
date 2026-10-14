package com.promath.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.promath.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Account(val id: String, val username: String, val name: String, val role: String)
data class AuthSession(val token: String, val user: Account)
data class Course(val id: String, val owner: String, val title: String, val description: String, val published: Boolean, val count: Int)
data class Lesson(val id: String, val courseId: String, val title: String, val stage: Int, val position: Int, val published: Boolean, val assetId: String?, val filename: String, val mime: String)
class ApiFailure(val status: Int, message: String) : Exception(message)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("platform", Context.MODE_PRIVATE)
    var endpoint: String
        get() = prefs.getString("endpoint", BuildConfig.API_BASE_URL).orEmpty()
        set(value) { prefs.edit().putString("endpoint", value).apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("promath-session", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("promath-session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(session: AuthSession?) {
        if (session == null) { prefs.edit().remove("auth").apply(); return }
        val data = JSONObject().put("token", session.token).put("user", JSONObject().put("id", session.user.id).put("username", session.user.username).put("name", session.user.name).put("role", session.user.role)).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encoded = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("auth", encoded).apply()
    }
    fun load(): AuthSession? = runCatching {
        val parts = prefs.getString("auth", null)?.split(':') ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))) }
        parseAuth(JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)))
    }.getOrNull()
}
fun parseAccount(j: JSONObject) = Account(j.getString("id"), j.getString("username"), j.getString("name"), j.getString("role"))
fun parseAuth(j: JSONObject) = AuthSession(j.getString("token"), parseAccount(j.getJSONObject("user")))
private fun JSONObject.flag(key: String) = optBoolean(key) || optInt(key) == 1
fun parseCourse(j: JSONObject) = Course(j.getString("id"), j.getString("owner_id"), j.getString("title"), j.optString("description"), j.flag("published"), j.optInt("lesson_count"))
fun parseLesson(j: JSONObject) = Lesson(j.getString("id"), j.getString("course_id"), j.getString("title"), j.getInt("stage"), j.getInt("position"), j.flag("published"), if (j.isNull("asset_id")) null else j.getString("asset_id"), if (j.isNull("filename")) "" else j.optString("filename"), if (j.isNull("mime")) "" else j.optString("mime"))

class PlatformApi(private val context: Context, private val store: SessionStore) {
    fun validateEndpoint(value: String): String {
        val uri = runCatching { URI(value.trim().trimEnd('/')) }.getOrElse { throw Exception("서비스 주소를 확인해 주세요.") }
        val host = uri.host.orEmpty()
        val local = host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.") || (host.startsWith("172.") && (host.split('.').getOrNull(1)?.toIntOrNull() ?: -1) in 16..31)
        require(host.isNotEmpty() && uri.userInfo == null && uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) { "서비스 주소를 확인해 주세요." }
        require(uri.scheme == "https" || (BuildConfig.DEBUG && local && uri.scheme == "http")) { "HTTPS 서비스 주소를 입력해 주세요." }
        return uri.toString().trimEnd('/')
    }
    private fun connection(path: String, method: String, token: String?): HttpURLConnection {
        if (store.endpoint.isBlank()) throw IllegalArgumentException("서비스 주소를 먼저 설정해 주세요.")
        return (URL(validateEndpoint(store.endpoint) + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 15000; readTimeout = 60000; instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }
    }
    private fun response(conn: HttpURLConnection): String {
        val status = conn.responseCode
        val body = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            val detail = runCatching { JSONObject(body).opt("detail") }.getOrNull()
            throw ApiFailure(status, if (detail is String) detail else "입력 값이나 연결 상태를 확인해 주세요.")
        }
        return body
    }
    fun request(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): String {
        val conn = connection(path, method, token)
        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                conn.doOutput = true; conn.setRequestProperty("Content-Type", "application/json"); conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            return response(conn)
        } finally { conn.disconnect() }
    }
    fun courses(token: String) = JSONArray(request("/courses", token = token)).let { array -> List(array.length()) { parseCourse(array.getJSONObject(it)) } }
    fun lessons(id: String, token: String) = JSONArray(request("/courses/$id/lessons", token = token)).let { array -> List(array.length()) { parseLesson(array.getJSONObject(it)) } }
    fun upload(id: String, uri: Uri, token: String, progress: (Float) -> Unit) {
        val resolver = context.contentResolver
        var name = "lecture.mp4"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0) }
        val mime = when (name.substringAfterLast('.').lowercase()) { "pdf" -> "application/pdf"; "webm" -> "video/webm"; "mp4" -> "video/mp4"; else -> resolver.getType(uri).orEmpty() }
        require(mime in listOf("application/pdf", "video/mp4", "video/webm")) { "MP4, WEBM, PDF 파일을 선택해 주세요." }
        val spool = File.createTempFile("upload-", ".tmp", context.cacheDir)
        try {
            resolver.openInputStream(uri)!!.use { input -> spool.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024); var size = 0L
                while (true) { val count = input.read(buffer); if (count < 0) break; size += count; require(size <= 512L * 1024 * 1024) { "파일은 512MB 이하여야 합니다." }; output.write(buffer, 0, count) }
            } }
            val conn = connection("/lessons/$id/file", "POST", token)
            try {
                conn.doOutput = true; conn.readTimeout = 120000; conn.setRequestProperty("Content-Type", mime); conn.setRequestProperty("X-Filename", URLEncoder.encode(name, "UTF-8").replace("+", "%20")); conn.setFixedLengthStreamingMode(spool.length())
                spool.inputStream().use { input -> conn.outputStream.use { output ->
                    val buffer = ByteArray(128 * 1024); var sent = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count); sent += count; progress(sent.toFloat() / spool.length()) }
                } }
                response(conn)
            } finally { conn.disconnect() }
        } finally { spool.delete() }
    }
    fun download(lesson: Lesson, token: String): File {
        val dir = File(context.cacheDir, "materials").apply { mkdirs() }
        val target = File(dir, "${lesson.id}.pdf")
        val conn = connection("/lessons/${lesson.id}/file", "GET", token)
        try {
            if (conn.responseCode !in 200..299) { response(conn); error("파일을 열 수 없습니다.") }
            conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            return target
        } finally { conn.disconnect() }
    }
    fun fileUrl(id: String) = store.endpoint + "/lessons/$id/file"
}
