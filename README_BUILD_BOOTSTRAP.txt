ARMYRIST BUILD BOOTSTRAP
========================

1. ZIP의 `.github` 폴더를 Armyrist 저장소 최상위에 그대로 넣으세요.
2. GitHub에 commit/push 하세요.
3. Push되면 GitHub Actions가 자동으로 실행됩니다.

이 Workflow가 하는 일:
- Java 17 설정
- 공식 Gradle 8.13 설치
- Android SDK 36 설치
- `gradle wrapper --gradle-version 8.13` 실행
- 정식 `gradle-wrapper.jar` 생성
- 생성된 Wrapper 파일을 저장소에 자동 commit/push
- Unit Test 실행
- Debug APK 빌드
- APK를 GitHub Actions Artifact로 업로드

빌드 성공 후:
GitHub 저장소 → Actions → 가장 최근 Android Build and Wrapper Bootstrap 실행 →
Artifacts → Armyrist-debug-apk

여기서 APK를 받을 수 있습니다.

주의:
Workflow는 Wrapper 생성 커밋에 `[skip ci]`를 붙여 반복 실행을 막습니다.
