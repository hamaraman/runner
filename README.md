# 러너 (Runner)

러닝 기록과 마라톤 대회 준비를 한 앱에서. Kotlin + Jetpack Compose. (v0.6.1)

## 기능

| 탭 | 기능 |
|---|---|
| 러닝 | GPS 러닝 기록(화면 꺼져도 계속 기록), 거리·시간·페이스, 자동 일시정지, 네이버 지도 위 경로·전체 경로 지도, km 구간 기록, GPX 저장·공유, 개인 최고 기록, 주간 목표·연속 주, 음성 코칭(1km 안내·목표 페이스 알림), 초보 인터벌 프로그램(음성·진동 신호) |
| 훈련 | 가장 가까운 대회에 맞춘 주간 훈련표 자동 생성(직접 목표·주간 거리·주 횟수 지정 가능), 이지런·인터벌·템포런·장거리, 회복 주·테이퍼, 러닝 저장 시 자동 완료 체크 |
| 러닝화 | 신발별 누적 거리와 교체 시기 알림, 러닝 후 오늘 신은 러닝화 확인 |
| 대회 | 전국 마라톤 대회 일정 검색(저스트런), 종목별 필터, 참가 대회 D-day·신청 여부 관리 |

그 밖에 Health Connect로 러닝 기록 내보내기, 전체 백업/복원, 중단된 러닝 복구, 크래시 리포트, 새 버전 알림(하루 미루기), AdMob 네이티브 광고(UMP 동의).

> 서버 없이 **기기 내부(JSON 파일)** 에만 저장하며, 회원가입이 필요 없습니다.

## 구조

```
core/   순수 Kotlin 로직 (JVM, 단위 테스트 포함)
        - Geo: 거리 계산, GPS 튐 필터, km 스플릿
        - Pace: 페이스 포맷, Riegel 기록 예측, 훈련 페이스 존
        - TrainingPlanGenerator, Interval: 훈련표·인터벌 생성
        - Records, Coach, Gpx, RaceCalendar, Version
app/    안드로이드 앱 (Compose UI, 포그라운드 위치 서비스, 로컬 저장소)
docs/   랜딩 페이지·개인정보처리방침(GitHub Pages), Play Console 등록 가이드
store/  스토어 아이콘·그래픽·스크린샷
```

## 실행 방법

1. Android Studio(최신 버전)에서 이 폴더를 열고 Gradle Sync
2. 기기(안드로이드 8.0 이상)를 연결하고 ▶ Run
3. 또는 커맨드라인:
   ```bash
   ./gradlew :core:test          # 핵심 로직 테스트
   ./gradlew :app:assembleDebug  # APK 빌드 → app/build/outputs/apk/debug/
   ```

GitHub Actions가 푸시마다 테스트와 디버그 APK 빌드를 수행합니다(`runner-debug-apk` 아티팩트). `v*` 태그를 올리면 서명된 `runner-<버전>.apk`/`.aab`로 GitHub 릴리스를 만듭니다. 스토어 등록 절차는 [docs/play-store.md](docs/play-store.md) 참고.

## 권한

- 위치(정확한 위치): 러닝 경로 기록
- 알림(안드로이드 13+): 러닝 중 상태 표시
- 포그라운드 서비스(위치): 화면이 꺼져도 기록
- Health Connect(운동·경로·거리 쓰기): 기록 내보내기
- 인터넷: 지도, 대회 일정, 광고
- 진동: 인터벌 신호

## 네이버 지도 설정

1. [네이버 클라우드 플랫폼](https://console.ncloud.com) → Maps → Application 등록 (Dynamic Map, Android 패키지명 `io.github.hamaraman.runner`)
2. 발급받은 Client ID를 `local.properties`에 추가 (커밋되지 않음):
   ```
   naverMapClientId=발급받은_ID
   ```
