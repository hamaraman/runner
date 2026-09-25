# 러너 (Runner)

러너를 위한 안드로이드 앱 MVP. Kotlin + Jetpack Compose.

## 기능

| 탭 | 기능 |
|---|---|
| 러닝 | GPS 러닝 기록(화면 꺼져도 계속 기록), 거리·시간·페이스, 일시정지/재개, 네이버 지도 위 경로 표시, 경로 GPX 저장, km 구간 기록, 주간 요약, 기록 공유 |
| 훈련 | 목표 대회(5K/10K/하프/풀)·날짜·현재 주간 거리로 주간 훈련표 자동 생성, 최근 기록으로 페이스 존 추정, 완료 체크(러닝 저장 시 자동 체크) |
| 크루 | 크루 생성, 번개/정기런 모임 생성·참석, 카카오톡 등으로 초대 메시지 공유 |
| 대회 | 참가할 대회 등록, D-day, 신청 여부, 대회에 맞춘 훈련 계획 바로 만들기, 전국 대회 일정 사이트 바로가기 |

> MVP는 서버 없이 **기기 내부(JSON 파일)** 에만 저장합니다. 크루 공유 기능은 다음 단계에서 서버(예: Firebase)와 연동할 예정입니다.

## 구조

```
core/   순수 Kotlin 로직 (JVM, 단위 테스트 포함)
        - Geo: 거리 계산, GPS 튐 필터, km 스플릿
        - Pace: 페이스 포맷, Riegel 기록 예측, 훈련 페이스 존
        - TrainingPlanGenerator: 주간 훈련표 생성
app/    안드로이드 앱 (Compose UI, 포그라운드 위치 서비스, 로컬 저장소)
```

## 실행 방법

1. Android Studio(최신 버전)에서 이 폴더를 열고 Gradle Sync
2. 기기(안드로이드 8.0 이상)를 연결하고 ▶ Run
3. 또는 커맨드라인:
   ```bash
   ./gradlew :core:test          # 핵심 로직 테스트
   ./gradlew :app:assembleDebug  # APK 빌드 → app/build/outputs/apk/debug/
   ```

GitHub Actions가 푸시마다 테스트와 APK 빌드를 수행하고, `runner-debug-apk` 아티팩트로 APK를 올립니다.

## 권한

- 위치(정확한 위치): 러닝 경로 기록
- 알림(안드로이드 13+): 러닝 중 상태 표시

## 네이버 지도 설정

1. [네이버 클라우드 플랫폼](https://console.ncloud.com) → Maps → Application 등록 (Dynamic Map, Android 패키지명 `com.runner.app`)
2. 발급받은 Client ID를 `local.properties`에 추가 (커밋되지 않음):
   ```
   naverMapClientId=발급받은_ID
   ```
