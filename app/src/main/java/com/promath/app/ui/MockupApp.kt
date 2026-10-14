package com.promath.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*

private val CourseTitles = listOf("고등학생을 위한\n초중등 복습", "수 Ⅰ 개념 완성", "수 Ⅱ 심화 학습", "미적분 기초", "확률과 통계")
private val CourseSubtitles = listOf("단단한 기초로 다시 시작", "핵심 개념 정리와 문제 풀이", "실력 향상을 위한 심화 과정", "대학을 위한 필수 개념", "실생활과 연결된 수학")
private val CourseGlyphs = listOf(Glyph.PLAY, Glyph.BOOK, Glyph.SCHOOL, Glyph.PENCIL, Glyph.CHART)
private val StageTitles = listOf("수와 연산", "문자와 식", "방정식과 부등식", "함수", "도형", "경우의 수", "확률과 통계", "종합 문제")
private val LessonTitles = listOf("일차방정식의 개념", "일차방정식의 풀이", "일차방정식의 활용", "연립방정식", "부등식의 개념", "일차부등식의 풀이", "연립부등식", "종합 문제")

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable fun MockupApp() {
    val nav = rememberNavController()
    var selectedLesson by rememberSaveable { mutableIntStateOf(2) }
    var selectedCourse by rememberSaveable { mutableIntStateOf(0) }
    var popup by rememberSaveable { mutableStateOf<String?>(null) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val activity = LocalContext.current.activity()
    LaunchedEffect(route) {
        activity?.requestedOrientation = if (route == "lecture" || route == "test") ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    if (popup != null) AlertDialog(onDismissRequest = { popup = null }, title = { Text(popup!!) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (popup == "공지사항") { Text("• 3월 업데이트 안내"); Text("• 새로운 강좌가 추가되었습니다."); Text("• 학습 이벤트 진행 중!") }
            else repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) .6f else 1f).height(14.dp).background(Color(0xFF303030), RoundedCornerShape(4.dp))) }
        } }, confirmButton = { TextButton(onClick = { popup = null }) { Text("닫기") } })
    NavHost(nav, "login") {
        composable("login") { Login({ nav.navigate("home") { popUpTo("login") { inclusive = true } } }, { popup = it }) }
        composable("home") { Home({ nav.navigate("courses") }, { popup = it }) }
        composable("courses") { Courses({ index -> selectedCourse = index; nav.navigate("stages") }, { nav.popBackStack() }, { popup = "메뉴" }) }
        composable("stages") { Stages(selectedCourse, { nav.navigate("lessons") }, { nav.popBackStack() }) }
        composable("lessons") { Lessons({ selectedLesson = it; nav.navigate("detail") }, { nav.popBackStack() }) }
        composable("detail") { Detail(selectedLesson, { nav.navigate("lecture") }, { nav.navigate("test") }, { nav.popBackStack() }) }
        composable("lecture") { Lecture({ nav.navigate("test") }, { nav.popBackStack() }, { popup = "메뉴" }) }
        composable("test") { Test({ nav.navigate("result") }, { nav.popBackStack() }, { popup = "메뉴" }) }
        composable("result") { Results({ selectedLesson = (selectedLesson + 1).coerceAtMost(7); nav.navigate("detail") { popUpTo("lessons") } }, { nav.navigate("lecture") }, { nav.navigate("test") }, { nav.popBackStack() }) }
    }
}

@Composable private fun GlyphButton(kind: Glyph, click: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = click), contentAlignment = Alignment.Center) { MonoIcon(kind, 25.dp) }
}

@Composable private fun Header(title: String, back: (() -> Unit)? = null, menu: (() -> Unit)? = null, user: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) GlyphButton(Glyph.BACK, back) else GlyphButton(Glyph.MENU, menu ?: {})
        Text(title, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (user != null) Box(Modifier.padding(9.dp).size(26.dp).background(Color(0xFF383838), CircleShape).clickable(onClick = user), contentAlignment = Alignment.Center) { MonoIcon(Glyph.USER, 19.dp) }
        else if (menu != null && back != null) GlyphButton(Glyph.MENU, menu)
        else Spacer(Modifier.size(44.dp))
    }
    HorizontalDivider(color = Color(0xFF292929))
}

@Composable private fun Frame(title: String, back: (() -> Unit)? = null, menu: (() -> Unit)? = null, user: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title, back, menu, user)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable private fun WhiteButton(text: String, click: () -> Unit) {
    Button(onClick = click, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink)) { Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}

@Composable private fun IconTile(kind: Glyph, dimension: Int = 62) {
    Box(Modifier.size(dimension.dp).background(CardFill, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { MonoIcon(kind, (dimension * .62f).dp) }
}

@Composable private fun Login(login: () -> Unit, popup: (String) -> Unit) {
    var id by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 30.dp).padding(bottom = 140.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PROMATH", fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .6.sp)
            Spacer(Modifier.height(55.dp))
            val colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFF686868), focusedBorderColor = Color.White, unfocusedContainerColor = Color(0xFF111111), focusedContainerColor = Color(0xFF111111))
            OutlinedTextField(id, { id = it }, Modifier.fillMaxWidth(), placeholder = { Text("아이디", color = Muted) }, leadingIcon = { MonoIcon(Glyph.USER, 22.dp) }, singleLine = true, shape = RoundedCornerShape(7.dp), colors = colors)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), placeholder = { Text("비밀번호", color = Muted) }, leadingIcon = { MonoIcon(Glyph.LOCK, 22.dp) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(7.dp), colors = colors)
            Spacer(Modifier.height(24.dp))
            WhiteButton("로그인", login)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text("회원가입", Modifier.clickable { popup("회원가입") }, fontSize = 12.sp)
                Text("   |   ", fontSize = 12.sp, color = Muted)
                Text("아이디/비밀번호 찾기", Modifier.clickable { popup("아이디/비밀번호 찾기") }, fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun Home(courses: () -> Unit, popup: (String) -> Unit) {
    Frame("PROMATH", menu = { popup("메뉴") }, user = { popup("MY") }) {
        Box(Modifier.fillMaxWidth().height(148.dp)) {
            Text("오늘도,\n수학과 더 가까워지세요!", Modifier.padding(top = 12.dp), fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) { MonoIcon(Glyph.CHART, 48.dp) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTile("강좌목록", "모든 강좌 보기", Glyph.BOOK, Modifier.weight(1f), courses)
            HomeTile("학습 현황", "내 progress", Glyph.CHART, Modifier.weight(1f)) { popup("학습 현황") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeTile("오답노트", "틀린 문제 모아보기", Glyph.DOCUMENT, Modifier.weight(1f)) { popup("오답노트") }
            HomeTile("MY", "내 정보 설정", Glyph.USER, Modifier.weight(1f)) { popup("MY") }
        }
        Spacer(Modifier.height(5.dp))
        HorizontalDivider(color = Color(0xFF343434))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("공지사항", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.clickable { popup("공지사항") }, verticalAlignment = Alignment.CenterVertically) { Text("더보기", fontSize = 13.sp, color = Muted); MonoIcon(Glyph.ARROW, 16.dp, Muted) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("•  3월 업데이트 안내", fontSize = 14.sp)
            Text("•  새로운 강좌가 추가되었습니다.", fontSize = 14.sp)
            Text("•  학습 이벤트 진행 중!", fontSize = 14.sp)
        }
    }
}

@Composable private fun HomeTile(title: String, subtitle: String, kind: Glyph, modifier: Modifier, click: () -> Unit) {
    Column(modifier.aspectRatio(1f).mockCard(click).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonoIcon(kind, 46.dp)
        Text(title, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 13.sp, lineHeight = 18.sp, color = Muted)
    }
}

@Composable private fun Courses(open: (Int) -> Unit, back: () -> Unit, menu: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Frame("PROMATH", menu = menu, user = menu) {
        Text("강좌 목록", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth()) {
            listOf("전체", "고등", "중등", "기초").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).clickable { tab = index }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, Modifier.padding(vertical = 10.dp), color = if (tab == index) Color.White else Muted, fontSize = 15.sp, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.fillMaxWidth().height(if (tab == index) 3.dp else 1.dp).background(if (tab == index) Color.White else Color(0xFF303030)))
                }
            }
        }
        val rows = when (tab) { 1 -> listOf(1, 2, 3, 4); 2, 3 -> listOf(0); else -> (0..4).toList() }
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            rows.forEach { index ->
                Row(Modifier.fillMaxWidth().heightIn(min = 94.dp).mockCard { open(index) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(CourseGlyphs[index])
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(CourseTitles[index], fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
                        Text(CourseSubtitles[index], fontSize = 12.sp, color = Muted)
                    }
                    MonoIcon(Glyph.ARROW, 20.dp)
                }
            }
        }
    }
}

@Composable private fun Stages(course: Int, open: () -> Unit, back: () -> Unit) {
    Frame("강좌 상세", back) {
        Row(Modifier.fillMaxWidth().mockCard().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            IconTile(CourseGlyphs[course])
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(CourseTitles[course], fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp)
                Text("기초를 다시, 더 높게!", fontSize = 13.sp, color = Muted)
            }
        }
        Column {
            StageTitles.forEachIndexed { index, title ->
                val percentage = listOf(100, 70, 40, 0, 0, 0, 0, 0)[index]
                Row(Modifier.fillMaxWidth().height(64.dp).clickable(enabled = index < 3, onClick = open), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("#${index + 1}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StripeProgress(percentage / 100f, Modifier.weight(1f))
                            Text("$percentage%", fontSize = 12.sp, color = Muted, modifier = Modifier.width(36.dp))
                        }
                    }
                    when { index < 2 -> MonoIcon(Glyph.CHECK, 25.dp); index == 2 -> MonoIcon(Glyph.ARROW, 27.dp); else -> MonoIcon(Glyph.LOCK, 24.dp) }
                }
                if (index < 7) HorizontalDivider(color = Color(0xFF272727))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StripeProgress(1f, Modifier.width(32.dp))
            Text("빗금 = 완료율(학습 진행도)", fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable private fun Lessons(open: (Int) -> Unit, back: () -> Unit) {
    Frame("", back) {
        Text("#3  방정식과 부등식", Modifier.padding(vertical = 20.dp), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxWidth().clip(CardShape).border(1.dp, Line, CardShape)) {
            LessonTitles.forEachIndexed { index, title ->
                Row(Modifier.fillMaxWidth().height(66.dp).background(if (index == 2) Color(0xFF303030) else Color(0xFF151515)).clickable(enabled = index < 4) { open(index) }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("3-${index + 1}", fontSize = 14.sp, color = if (index < 4) Color.White else Muted)
                    Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = if (index == 2) FontWeight.Bold else FontWeight.Normal)
                    when { index < 2 -> MonoIcon(Glyph.CHECK, 23.dp); index == 2 -> Row { MonoIcon(Glyph.ARROW, 16.dp); MonoIcon(Glyph.ARROW, 16.dp) }; index == 3 -> Box(Modifier.size(22.dp).border(1.5.dp, Color.White, CircleShape)); else -> MonoIcon(Glyph.LOCK, 21.dp) }
                }
                if (index < 7) HorizontalDivider(color = Line)
            }
        }
    }
}

@Composable private fun Detail(lesson: Int, lecture: () -> Unit, test: () -> Unit, back: () -> Unit) {
    Frame("3-${lesson + 1} ${LessonTitles[lesson]}", back) {
        Column(Modifier.fillMaxWidth().padding(top = 44.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            IconTile(Glyph.DOCUMENT, 96)
            Spacer(Modifier.height(12.dp))
            Text(LessonTitles[lesson], fontSize = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(if (lesson == 2) "실생활 문제를 통해\n일차방정식을 활용하는 방법을 배웁니다." else "기초부터 차근차근,\n개념을 이해하고 문제를 풀어보세요.", fontSize = 15.sp, lineHeight = 25.sp, textAlign = TextAlign.Center, color = Muted)
            Spacer(Modifier.height(12.dp))
            ActionCard(Glyph.PLAY, "강의 시작", "(개념 학습하기)", lecture)
            ActionCard(Glyph.DOCUMENT, "바로 테스트", "(바로 문제 풀기)", test)
        }
    }
}

@Composable private fun ActionCard(kind: Glyph, title: String, subtitle: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(8.dp)).clickable(onClick = click).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        MonoIcon(kind, 35.dp)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.width(30.dp))
    }
}

@Composable private fun LearningFrame(title: String, back: () -> Unit, menu: () -> Unit, left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title, back, menu)
        Row(Modifier.weight(1f).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.weight(1.25f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = left)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = right)
        }
    }
}

@Composable private fun EmptyContent(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().height(128.dp).mockCard().padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Box(Modifier.fillMaxWidth(.55f).height(13.dp).background(Color(0xFF3A3A3A), RoundedCornerShape(3.dp)))
        Spacer(Modifier.height(8.dp))
        repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) .65f else 1f).height(9.dp).background(Color(0xFF262626), RoundedCornerShape(3.dp))) }
    }
}

@Composable private fun Lecture(test: () -> Unit, back: () -> Unit, menu: () -> Unit) {
    var examples by rememberSaveable { mutableIntStateOf(3) }
    var selected by rememberSaveable { mutableIntStateOf(1) }
    LearningFrame("강의", back, menu, left = {
        Text("1. 개념", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        EmptyContent()
        Row(Modifier.fillMaxWidth().mockCard().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("예시", Modifier.border(1.dp, Line, RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 14.sp)
            Box(Modifier.width(160.dp).height(10.dp).background(Color(0xFF333333), RoundedCornerShape(3.dp)))
        }
    }, right = {
        Text("예제 문제 (${examples}문항)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (1..examples).forEach { index ->
                Box(Modifier.size(42.dp).background(if (index == selected) Color(0xFF606060) else Color(0xFF292929), CircleShape).clickable { selected = index }, contentAlignment = Alignment.Center) { Text("$index", fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.fillMaxWidth().mockCard { if (examples == 3) examples = 4 }.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MonoIcon(Glyph.BULB, 31.dp)
            Text("힌트 사용 시\n예제 1문항이 추가됩니다.", fontSize = 15.sp, lineHeight = 23.sp)
        }
        WhiteButton("최종 테스트", test)
    })
}

@Composable private fun Test(result: () -> Unit, back: () -> Unit, menu: () -> Unit) {
    var started by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    LearningFrame("테스트", back, menu, left = {
        Column(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MonoIcon(Glyph.TIMER, 47.dp)
            Text(if (started) "${selected + 1}번 문제" else "20문항", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            if (!started) Text("제한 시간 내에 문제를 풀어보세요.", fontSize = 14.sp, color = Muted)
            else EmptyContent()
        }
    }, right = {
        (0..19).toList().chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index ->
                    Box(Modifier.weight(1f).height(36.dp).mockCard { selected = index }, contentAlignment = Alignment.Center) {
                        Text(if (index < 5 || started) "${index + 1}" else "•••", color = if (index == selected && started) Color.White else Muted, fontSize = 15.sp)
                    }
                }
            }
        }
        WhiteButton(if (started) "결과 보기" else "테스트 시작") { if (started) result() else started = true }
    })
}

@Composable private fun Results(next: () -> Unit, lecture: () -> Unit, test: () -> Unit, back: () -> Unit) {
    Frame("학습 결과", back) {
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(CardShape).border(1.dp, Color(0xFFBBBBBB), CardShape).padding(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { MonoIcon(Glyph.PENCIL, 39.dp); Text("통과!", fontSize = 29.sp, fontWeight = FontWeight.Bold) }
            Text("다음 차시가 해금되었습니다.", fontSize = 17.sp, color = Muted)
            OutlinedButton(onClick = next, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(5.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)) { Text("다음 차시로 이동", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().clip(CardShape).border(1.dp, Line, CardShape).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { MonoIcon(Glyph.RETRY, 39.dp); Text("재도전이 필요해요.", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Text("강의를 수강한 후\n다시 테스트에 도전하세요.", fontSize = 16.sp, lineHeight = 26.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = lecture, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(4.dp)) { Text("강의로 이동", fontSize = 14.sp) }
                OutlinedButton(onClick = test, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(4.dp)) { Text("다시 테스트", fontSize = 14.sp) }
            }
        }
    }
}
