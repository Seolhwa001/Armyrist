Armyrist 저장소 루트를 기준으로 ZIP 구조 그대로 덮어쓰세요.

/build.gradle.kts = 루트용 (플러그인 버전 포함)
/app/build.gradle.kts = 앱 모듈용 (플러그인 버전 없음)

두 파일 이름이 같으므로 위치를 바꾸면 빌드가 실패합니다.
