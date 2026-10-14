# PROMATH 0.4.0 — 학습 플랫폼

검정·흰색 목업을 바탕으로 학생 회원가입과 강사 강의 업로드가 실제 서버에 연결되는 Android 앱입니다. 인사·동기부여 문구와 가짜 학습 정보는 제거했습니다.

![앱 화면](docs/preview.png)

## 다운로드

[Android APK와 전체 소스](https://github.com/k01020774181-wq/promath-mvp/releases/tag/v0.4.0-platform)

Android 8.0 이상. APK는 직접 설치용 디버그 서명입니다. 서버를 실행한 뒤 로그인 화면의 **연결 설정**에 서비스 주소를 입력하세요. 공개 서버는 아직 배포되지 않았습니다. GitHub 링크는 설치 파일을 제공합니다.

## 구현된 흐름

- 학생: 회원가입 → 로그인 → 공개 강좌 → 단계별 차시 → 영상 재생 / PDF 열기
- 강사: 서버에서 강사 계정 생성 → 로그인 → 강좌 만들기 → 차시 추가 → 파일 업로드 → 차시 공개 → 강좌 공개
- 지원 파일: MP4·WEBM·PDF, 차시당 파일 1개, 최대 512MB. 파일 교체 가능.
- 강사만 자기 강좌를 관리합니다. 학생은 공개 강좌의 공개 차시만 조회하고 파일에 접근합니다. 이 규칙은 서버에서도 검사합니다.
- 학생 계정과 강좌·차시·업로드 파일은 서버에 영구 저장됩니다. 비밀번호는 scrypt 해시, 세션은 7일 만료 및 로그아웃 시 폐기됩니다. 앱의 세션은 Android Keystore로 암호화합니다.
- 기본 화면은 세로, 영상 화면은 가로입니다. 서버에 강의 콘텐츠를 미리 넣지 않았습니다.

## 구조

```text
app/src/main/java/com/promath/app/
  MainActivity.kt
  data/PlatformApi.kt       # API, 모델, 암호화 세션 저장, 스트리밍 업로드
  ui/PlatformViewModel.kt   # 인증·조회·업로드·공개 상태
  ui/PlatformApp.kt         # 학생/강사 화면과 영상 플레이어
  ui/Design.kt              # 흑백 디자인 시스템
server/
  app.py                    # FastAPI + SQLite + 파일 저장
  test_platform.py          # 권한·인증·업로드 통합 테스트
  start.ps1                 # Windows 실행 / 강사 생성 / 테스트
  Dockerfile, compose.yaml, Caddyfile  # HTTPS 자체 서버 배포 구성
docs/서버실행안내.md
```

UI는 Compose, 화면 상태는 ViewModel/StateFlow, 통신은 HttpURLConnection입니다. 서버가 계정과 콘텐츠의 원본을 관리합니다. 서버를 재시작해도 데이터가 유지됩니다.

## 실행

[서버 실행 및 인터넷 배포 안내](docs/서버실행안내.md)를 따르세요. 강사는 일반 회원가입으로 만들 수 없으며 서버 관리자가 별도로 생성합니다. 기본 강사 비밀번호는 없습니다.

Android Studio에서 이 폴더를 열어 JDK 17, Android SDK 35로 실행합니다. 기본 서비스 주소를 넣은 APK는 다음처럼 빌드할 수 있습니다.

```powershell
.\gradlew.bat :app:assembleDebug -PpromathApiUrl=https://learn.example.com
```

HTTPS 주소 사용을 기본으로 합니다. 이번 직접 설치용 APK에서는 같은 네트워크의 개발 서버에 한해 HTTP를 허용합니다. 정식 release 빌드는 HTTPS만 허용합니다. API 주소에 비밀 키를 넣지 않습니다.

## 검증

- Android APK 빌드 성공, lint 오류 0개 (의존성 업데이트 권고·화면 방향·KTX 스타일 경고 있음)
- 서버 통합 테스트 12개 통과: 학생 권한, 강사 소유권, 초안 비공개, 인증된 파일 조회, Range 요청, 비밀번호·세션·업로드 검사 및 저장 유지
- Android 11 가상 기기: 강사 로그인 → 강좌/차시 생성 → MP4 업로드·교체 → 공개 → 학생 회원가입 → 강좌 조회 → 가로 영상 재생 확인
- Docker/인터넷 HTTPS 배포는 실행 환경과 도메인이 없어 아직 검증하지 않았습니다.

이번 버전의 범위는 계정·콘텐츠 운영 기반입니다. 결제, 가입 이메일 인증, 비밀번호 재설정, 영상 변환, 시험·학습 진도 기능은 포함하지 않습니다.
