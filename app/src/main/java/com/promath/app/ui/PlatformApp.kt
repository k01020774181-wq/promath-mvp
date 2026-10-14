package com.promath.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
@Composable fun PlatformApp(vm: PlatformViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.activity()
    var config by rememberSaveable { mutableStateOf(false) }
    var createCourse by rememberSaveable { mutableStateOf(false) }
    var createLesson by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.upload(uri) }
    LaunchedEffect(s.screen) { activity?.requestedOrientation = if (s.screen == "player") ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    BackHandler(enabled = s.screen !in listOf("auth", "home")) { vm.back() }
    if (config) {
        var address by remember { mutableStateOf(s.endpoint) }
        AlertDialog(onDismissRequest = { if (!s.busy) config = false }, title = { Text("서비스 연결") },
            text = { OutlinedTextField(address, { address = it }, label = { Text("서비스 주소") }, placeholder = { Text("https://learn.example.com") }, singleLine = true) },
            confirmButton = { TextButton(enabled = !s.busy, onClick = { vm.configure(address); config = false }) { Text("연결") } },
            dismissButton = { TextButton(onClick = { config = false }) { Text("취소") } })
    }
    if (createCourse) EditorDialog("강좌 만들기", lesson = false, save = { title, desc, _, _ -> vm.createCourse(title, desc); createCourse = false }, cancel = { createCourse = false })
    if (createLesson) EditorDialog("차시 추가", lesson = true, save = { title, _, stage, position -> vm.createLesson(title, stage, position); createLesson = false }, cancel = { createLesson = false })
    if (s.session == null) {
        AuthScreen(s.busy, s.error, vm::authenticate, { config = true })
        return
    }
    if (s.screen == "player") {
        Player(s, vm, { vm.back() })
        return
    }
    val user = s.session!!.user
    val owned = s.course?.owner == user.id && user.role == "instructor"
    PlatformPage(if (s.screen == "home") "PROMATH" else s.course?.title.orEmpty(), if (s.screen == "home") null else vm::back) {
        if (s.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (s.error != null) { Text(s.error!!, color = Muted); TextButton(onClick = vm::clearError) { Text("닫기") } }
        when (s.screen) {
            "home" -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (user.role == "instructor") "강의 관리" else "강좌", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text(if (user.role == "instructor") "강사 · ${user.name}" else user.name, color = Muted, fontSize = 13.sp)
                }
                if (user.role == "instructor") Primary("강좌 만들기", !s.busy) { createCourse = true }
                if (s.courses.isEmpty() && !s.busy) Text(if (user.role == "instructor") "등록된 강좌가 없습니다." else "공개된 강좌가 없습니다.", color = Muted)
                s.courses.forEach { course ->
                    Row(Modifier.fillMaxWidth().mockCard { if (!s.busy) vm.openCourse(course) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MonoIcon(Glyph.BOOK, 35.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(course.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("${course.count}차시${if (user.role == "instructor" && course.owner == user.id) if (course.published) " · 공개" else " · 초안" else ""}", fontSize = 13.sp, color = Muted)
                        }
                        MonoIcon(Glyph.ARROW, 21.dp)
                    }
                }
                HorizontalDivider(color = Line)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TextButton(enabled = !s.busy, onClick = vm::refresh) { Text("새로고침") }
                    TextButton(enabled = !s.busy, onClick = vm::logout) { Text("로그아웃") }
                }
            }
            "course" -> {
                Text(s.course!!.title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                if (s.course!!.description.isNotBlank()) Text(s.course!!.description, color = Muted)
                if (owned) {
                    PublicationRow("강좌 공개", s.course!!.published, !s.busy, vm::publishCourse)
                    Primary("차시 추가", !s.busy) { createLesson = true }
                }
                if (s.lessons.isEmpty() && !s.busy) Text("등록된 차시가 없습니다.", color = Muted)
                s.lessons.groupBy { it.stage }.forEach { (stage, lessons) ->
                    Text("$stage 단계", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    lessons.forEach { lesson ->
                        Row(Modifier.fillMaxWidth().mockCard { vm.openLesson(lesson) }.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                            Text("${lesson.stage}-${lesson.position}", color = Muted, fontSize = 13.sp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(lesson.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                if (owned) Text(if (lesson.published) "공개" else if (lesson.assetId == null) "파일 미등록" else "초안", fontSize = 12.sp, color = Muted)
                            }
                            MonoIcon(Glyph.ARROW, 19.dp)
                        }
                    }
                }
            }
            "lesson" -> {
                val lesson = s.lesson ?: return@PlatformPage
                Text("${lesson.stage}-${lesson.position}", color = Muted)
                Text(lesson.title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().mockCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    MonoIcon(if (lesson.mime == "application/pdf") Glyph.DOCUMENT else Glyph.PLAY, 44.dp)
                    Text(if (lesson.assetId == null) "강의 파일 미등록" else lesson.filename, color = Muted)
                }
                if (owned) {
                    Primary(if (lesson.assetId == null) "강의 파일 업로드" else "파일 교체", !s.busy) { picker.launch(arrayOf("*/*")) }
                    Text("MP4 · WEBM · PDF / 최대 512MB", fontSize = 12.sp, color = Muted)
                    if (s.uploadProgress != null) { Text("업로드 ${(s.uploadProgress!! * 100).toInt()}%", color = Muted); LinearProgressIndicator(progress = { s.uploadProgress ?: 0f }, modifier = Modifier.fillMaxWidth()) }
                    PublicationRow("차시 공개", lesson.published, !s.busy && lesson.assetId != null, vm::publishLesson)
                    if (lesson.assetId == null) Text("파일 등록 후 공개할 수 있습니다.", color = Muted, fontSize = 13.sp)
                }
                Primary(if (lesson.mime == "application/pdf") "자료 열기" else "강의 보기", !s.busy && lesson.assetId != null, vm::viewFile)
            }
        }
    }
}

@Composable private fun PlatformPage(title: String, back: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (back != null) TextButton(onClick = back, contentPadding = PaddingValues(4.dp)) { MonoIcon(Glyph.BACK, 20.dp) } else Spacer(Modifier.width(44.dp))
            Text(title, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.width(44.dp))
        }
        HorizontalDivider(color = Line)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}
@Composable private fun Primary(title: String, enabled: Boolean = true, click: () -> Unit) {
    Button(onClick = click, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(6.dp)) { Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}
@Composable private fun PublicationRow(title: String, published: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title); Switch(checked = published, enabled = enabled, onCheckedChange = change) }
}
@Composable private fun AuthScreen(busy: Boolean, error: String?, authenticate: (String, String, String?) -> Unit, config: () -> Unit) {
    var signup by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var validation by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(70.dp))
        Text("PROMATH", fontSize = 35.sp, fontWeight = FontWeight.ExtraBold)
        Text(if (signup) "학생 회원가입" else "로그인", color = Muted)
        Spacer(Modifier.height(15.dp))
        if (signup) OutlinedTextField(name, { name = it }, label = { Text("이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(username, { username = it }, label = { Text("아이디") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
        OutlinedTextField(password, { password = it }, label = { Text("비밀번호") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
        if (signup) {
            OutlinedTextField(confirm, { confirm = it }, label = { Text("비밀번호 확인") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Text("아이디: 영문·숫자·_·- 3~32자\n비밀번호: 10자 이상", color = Muted, fontSize = 12.sp)
        }
        if (error != null || validation != null) Text(validation ?: error.orEmpty(), color = Muted)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Primary(if (signup) "회원가입" else "로그인", !busy) {
            validation = when {
                !username.matches(Regex("[A-Za-z0-9_-]{3,32}")) -> "아이디를 확인해 주세요."
                password.length !in 10..128 -> "비밀번호는 10~128자로 입력해 주세요."
                signup && (name.isBlank() || name.length > 60) -> "이름을 입력해 주세요."
                signup && password != confirm -> "비밀번호 확인 값이 일치하지 않습니다."
                else -> null
            }
            if (validation == null) authenticate(username, password, if (signup) name else null)
        }
        TextButton(enabled = !busy, onClick = { signup = !signup; validation = null; password = ""; confirm = "" }) { Text(if (signup) "로그인으로 돌아가기" else "학생 회원가입") }
        Spacer(Modifier.height(12.dp))
        TextButton(enabled = !busy, onClick = config) { Text("연결 설정", color = Muted, fontSize = 12.sp) }
    }
}
@Composable private fun EditorDialog(title: String, lesson: Boolean, save: (String, String, Int, Int) -> Unit, cancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("1") }
    var position by remember { mutableStateOf("1") }
    AlertDialog(onDismissRequest = cancel, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text(if (lesson) "차시명" else "강좌명") })
            if (!lesson) OutlinedTextField(description, { description = it }, label = { Text("설명 (선택)") })
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(stage, { stage = it }, Modifier.weight(1f), label = { Text("단계 (1~8)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(position, { position = it }, Modifier.weight(1f), label = { Text("차시 번호") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && name.length <= 120 && (!lesson || ((stage.toIntOrNull() ?: 0) in 1..8 && (position.toIntOrNull() ?: 0) in 1..999)), onClick = { save(name, description, stage.toIntOrNull() ?: 1, position.toIntOrNull() ?: 1) }) { Text("저장") } }, dismissButton = { TextButton(onClick = cancel) { Text("취소") } })
}
@Composable private fun Player(s: PlatformState, vm: PlatformViewModel, back: () -> Unit) {
    var error by remember { mutableStateOf(false) }
    val lesson = s.lesson!!
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = back) { MonoIcon(Glyph.BACK, 22.dp) }; Text(lesson.title, fontWeight = FontWeight.Bold) }
        if (error) Text("영상을 재생할 수 없습니다. 파일 형식과 연결을 확인해 주세요.", Modifier.padding(20.dp), color = Muted)
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
            VideoView(context).apply {
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnErrorListener { _, _, _ -> error = true; true }
                setVideoURI(Uri.parse(vm.api.fileUrl(lesson.id)), mapOf("Authorization" to "Bearer ${s.session!!.token}", "android-allow-cross-domain-redirect" to "0"))
                setOnPreparedListener { start() }
            }
        }, onRelease = { it.stopPlayback() })
    }
}
