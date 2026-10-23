# 훈련 시작(가이드 러닝) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 러닝 탭 "오늘의 훈련" 카드의 "훈련 시작" 버튼으로 그날 훈련을 구간별 음성 안내하며 기록하고, 끝까지 마치면 훈련표에 완료 표시한다.

**Architecture:** 구간 정의와 진행 판정은 순수 Kotlin(`core`)에 둔다: `GuideSteps.forWorkout()`가 저장된 훈련표(운동 종류·목표 거리·페이스 존·대회 주 여부)에서 구간 목록을 **그때 계산**하고(plan JSON 변경 없음 → 옛 계획 호환), `GuideTracker`가 누적 거리·시간으로 현재 구간을 판정한다. `RunTrackingService`는 Intent extra로 훈련 날짜(epochDay)를 받아 트래커를 돌리고, 구간별로 페이스 알림 목표를 바꾼다.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, JUnit4 (`core` 모듈).

**Spec:** `docs/superpowers/specs/2026-10-23-guided-training-design.md`

## Global Constraints
- 일반 "러닝 시작" 동작, 초보 인터벌(`IntervalPrograms.beginner`/`CoachSettings.intervalSession`)은 바꾸지 않는다.
- plan JSON·임시저장(draft) 형식 변경 금지.
- 인터벌 세트: 5K/10K = 400m × 8 (조깅 200m), 하프/풀 = 1km × 5 (조깅 400m). 워밍업 2km. 템포 25분. 대회 주 템포 = 가볍게 3km → 대회 페이스 1km → 쿨다운.
- 쿨다운 = `Workout.distanceKm`까지 남은 거리. 앞 구간 거리 합이 이미 총 거리 이상이면 쿨다운 생략.
- 마지막 구간 끝 → "훈련 완료!" 음성 + 진동, 기록은 계속. 저장 시 완료 상태면 `setWorkoutDone(day, true)`.
- 훈련 모드 페이스 알림은 현재 구간의 `pace`로만. `pace == null` 구간(워밍업·조깅·쿨다운)은 알림 없음.
- 휴식일(`WorkoutType.REST`)엔 버튼 없음. 완료된 날에도 버튼 표시.
- 테스트: `./gradlew :core:test`, 빌드: `./gradlew assembleDebug`.

## Review Focus
- 일시정지 중엔 구간이 진행되지 않아야 함(트래커는 서비스가 넘기는 경과 시간·거리만 사용) → Task 2 `pausedTimeDoesNotAdvance`.
- GPS 한 번에 여러 구간을 건너뛸 만큼 거리가 튀어도 구간을 하나씩 다 지나 완료에 도달해야 함 → Task 2 `bigJumpCrossesSeveralSteps`.
- 페이스 존이 없는 계획(`zones == null`)도 구간 안내는 되고 알림만 없음 → Task 1 `noZonesStillBuildsSteps`.
- 훈련 시작 중 위치 권한 요청을 거쳐도 훈련 모드로 시작해야 함 → Task 4 수동 확인.
- 저장되지 않는 짧은 기록(1분·50m 미만)은 완료 표시도 안 함 → Task 3 구현 메모.

---

### Task 1: 구간 정의 — `GuideStep`, `GuideSteps.forWorkout`

**Files:**
- Create: `core/src/main/kotlin/com/runner/core/Guide.kt`
- Test: `core/src/test/kotlin/com/runner/core/GuideTest.kt`

**Interfaces:**
- Produces:
  - `data class GuideStep(val label: String, val distanceM: Double? = null, val sec: Int? = null, val untilTotalM: Double? = null, val pace: IntRange2? = null, val rep: Int = 0, val reps: Int = 0)` — 정확히 하나의 종료 조건(distanceM / sec / untilTotalM). `rep/reps`는 인터벌 빠른 구간에만(1부터).
  - `object GuideSteps { fun forWorkout(plan: TrainingPlan, workout: Workout): List<GuideStep> }` — REST면 빈 리스트.
  - 라벨: "워밍업", "빠르게", "조깅", "템포", "가볍게", "대회 페이스", "쿨다운", 이지/장거리/대회 단일 구간은 `workout.type.label`.

- [ ] **Step 1: 실패하는 테스트 작성** (`GuideTest`, 테스트용 plan은 `TrainingPlan(goal, raceDateEpochDay=0, raceName="", zones, weeks=listOf(TrainingWeek(0,"",0.0, workouts)))`)
  - `easyIsSingleDistanceStep`: EASY 6.0km → 1구간, `distanceM == 6000.0`, `pace == zones.easy`.
  - `intervalTenK`: goal TEN_K, INTERVAL 8.0km → 라벨 순서 워밍업, (빠르게, 조깅)×7, 빠르게, 쿨다운 = 1+15+1 = 17구간(마지막 빠르게 뒤 조깅 없음); 빠르게는 `distanceM 400.0`, `pace == zones.interval`, `rep` 1..8, `reps 8`; 조깅 `200.0`, pace null; 쿨다운 `untilTotalM 8000.0`.
  - `intervalHalf`: goal HALF → 빠르게 `1000.0` × 5, 조깅 `400.0`.
  - `tempo`: TEMPO 7.0km(같은 주에 RACE 없음) → 워밍업 2000 / 템포 `sec 1500`, pace zones.tempo / 쿨다운 untilTotalM 7000.
  - `raceWeekTempo`: 같은 주에 RACE 운동이 있는 TEMPO 5.0km → 가볍게 3000 / 대회 페이스 1000, pace `IntRange2(race-5, race+5)` / 쿨다운 untilTotalM 5000.
  - `cooldownSkippedWhenNoRoom`: INTERVAL goal TEN_K, distanceKm 7.0 (워밍업2+400×8+200×7=6.6km < 7 → 쿨다운 있음); distanceKm 6.0 → 마지막 구간이 "빠르게".
  - `noZonesStillBuildsSteps`: zones null → 구간 수는 같고 모든 pace null.
  - `restIsEmpty`.
- [ ] **Step 2:** `./gradlew :core:test --tests com.runner.core.GuideTest` → 컴파일 실패.
- [ ] **Step 3:** `Guide.kt`에 구현. 대회 주 판정 = `plan.weeks.first { workout in it.workouts }.workouts.any { it.type == RACE }`. RACE 운동 pace = `IntRange2(z.race-5, z.race+5)` (기존 `TargetPace.forWorkout`과 동일 — 재사용).
- [ ] **Step 4:** 같은 명령 → PASS.
- [ ] **Step 5:** 커밋 `Add guided workout step builder`.

### Task 2: 진행 판정 — `GuideTracker`

**Files:**
- Modify: `core/src/main/kotlin/com/runner/core/Guide.kt`
- Test: `core/src/test/kotlin/com/runner/core/GuideTest.kt`

**Interfaces:**
- Consumes: `GuideStep` (Task 1)
- Produces:
  - `class GuideTracker(val steps: List<GuideStep>)`
    - `val index: Int` (현재 구간, 끝나면 `steps.size`), `val finished: Boolean`
    - `fun update(distanceM: Double, elapsedSec: Long): List<Int>` — 이번 호출에서 **새로 시작된** 구간 인덱스들(완료 시 `steps.size` 포함). 첫 호출은 `[0]`.
    - `fun progressText(distanceM: Double, elapsedSec: Long): String` — 예 "빠르게 3/8 · 120m 남음", "템포 · 12:30 남음", "쿨다운 · 0.8km 남음", 완료 시 "훈련 완료".
  - `object VoiceText`에 `fun guideStep(step: GuideStep): String` 추가(`Coach.kt`) — 예 "빠르게 400미터, 3회차.", "템포 25분.", "쿨다운.", 이지 단일 구간은 "이지런 6킬로미터."; 완료 문구 상수 `"훈련 완료! 수고했어요. 기록은 계속돼요."`.

- [ ] **Step 1: 실패하는 테스트**
  - `startsAtZero`: 첫 `update(0.0, 0)` == `[0]`, 다시 호출하면 `[]`.
  - `distanceStepAdvances`: [워밍업 2000, 빠르게 400] — `update(1999.0,600)`→`[]`, `update(2000.0,610)`→`[1]`, `update(2400.0,700)`→`[2]`, `finished`.
  - `timeStepAdvances`: [템포 sec 1500] 시작 elapsed 100 → `update(x,1599)` `[]`, `update(x,1600)` `[1]`.
  - `untilTotal`: [쿨다운 untilTotalM 5000] → 4999 `[]`, 5000 `[1]`.
  - `bigJumpCrossesSeveralSteps`: [400, 200, 400] 시작 후 `update(1000.0, 60)` → `[1,2,3]`, finished.
  - `pausedTimeDoesNotAdvance`: 같은 elapsed·distance로 여러 번 호출해도 변화 없음.
  - `progressTextFormats`: 위 예시 문자열 3개 정확히 일치.
- [ ] **Step 2:** `./gradlew :core:test --tests com.runner.core.GuideTest` → FAIL.
- [ ] **Step 3:** 구현. 구간 시작 시점의 (distanceM, elapsedSec)를 기억하고, 종료 조건 충족 시 다음 구간의 시작값은 **현재 값**으로 둔다(점프 시 남은 구간도 연속 판정).
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** 커밋 `Add guided workout tracker`.

### Task 3: 서비스 연동

**Files:**
- Modify: `app/src/main/java/com/runner/app/tracking/RunTrackingService.kt` (`start()` ~109, `finish()` ~176, `startTimer()` ~280, companion ~396)

**Interfaces:**
- Consumes: `GuideSteps.forWorkout`, `GuideTracker`, `VoiceText.guideStep` (Task 1–2), `container.setWorkoutDone(epochDay, done)`.
- Produces: `const val EXTRA_GUIDED_DAY = "com.runner.app.GUIDED_DAY"`; `fun send(context, action, guidedDay: Long? = null)` — 기존 호출은 그대로 컴파일.

- [ ] **Step 1:** `onStartCommand`에서 ACTION_START 시 `intent.getLongExtra(EXTRA_GUIDED_DAY, Long.MIN_VALUE)`를 `start(guidedDay)`로 전달.
- [ ] **Step 2:** `start()`: guidedDay가 있고 해당 운동이 존재·비REST면 `guide = GuideTracker(GuideSteps.forWorkout(plan, workout))`, `guideDay = day`; 이때 초보 `intervalSteps = null`(훈련 모드 우선), `paceAlert = null`, `targetPace = null`로 시작하고 시작 음성은 `"훈련을 시작합니다."`.
- [ ] **Step 3:** 타이머 루프에서 `updateInterval` 옆에 `updateGuide(elapsedSec)`: `guide.update(snap.distanceM, elapsedSec)` 결과마다 진동 + `say(VoiceText.guideStep(step))` 또는 완료 문구; 새 구간의 `pace`로 `paceAlert = pace?.takeIf { settings.paceAlert }?.let { PaceAlertPolicy(it) }`, 스냅샷 `targetPace = pace`; `intervalText = guide.progressText(...)`. 자동 일시정지 중에는 elapsed가 멈춰 있으므로 별도 처리 불필요.
- [ ] **Step 4:** `finish()`: 기록이 실제로 저장되는 분기 안에서만 `if (guide?.finished == true) container.setWorkoutDone(guideDay, true)`; 끝에서 `guide = null`.
- [ ] **Step 5:** `./gradlew assembleDebug` → BUILD SUCCESSFUL, `./gradlew :core:test` PASS.
- [ ] **Step 6:** 커밋 `Run guided workouts in tracking service`.

### Task 4: "훈련 시작" 버튼

**Files:**
- Modify: `app/src/main/java/com/runner/app/ui/run/RunScreen.kt` (오늘의 훈련 카드 ~176-193, `startRun()` ~151, permissionLauncher ~140)

**Interfaces:**
- Consumes: `RunTrackingService.send(context, ACTION_START, guidedDay)` (Task 3)

- [ ] **Step 1:** `startRun(guidedDay: Long? = null)`로 바꾸고, 권한 요청을 거치는 경우를 위해 `var pendingGuidedDay by remember { mutableStateOf<Long?>(null) }`에 저장했다가 permissionLauncher 성공 시 사용 후 null로.
- [ ] **Step 2:** 오늘의 훈련 카드 Column 아래(비REST일 때만) 아웃라인 버튼 "▶ 훈련 시작" → `startRun(today.dateEpochDay)`. 기존 카드 레이아웃 유지, 버튼은 카드 전체 폭.
- [ ] **Step 3:** `./gradlew assembleDebug` 성공 후 에뮬레이터 수동 확인: 훈련표 있는 날 버튼 표시 / 휴식일·훈련표 없음엔 미표시 / 버튼 → 진행 표시("워밍업 · 2.0km 남음")와 음성 / `adb emu geo fix`로 이동시켜 구간 전환 / 저장 후 훈련표에 완료 표시 / 일반 "러닝 시작"은 기존과 동일 / 위치 권한 없는 상태에서 훈련 시작 → 권한 허용 후 훈련 모드로 시작.
- [ ] **Step 4:** 커밋 `Add start-training button to today's workout card`.
