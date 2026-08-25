# 출근길 대란 🚗

[![Android CI](https://github.com/cho1017/GameProject/actions/workflows/android-ci.yml/badge.svg)](https://github.com/cho1017/GameProject/actions/workflows/android-ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF?logo=kotlin&logoColor=white)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)

[Does Not Commute](https://en.wikipedia.org/wiki/Does_Not_Commute) 스타일의 탑다운 드라이빙 퍼즐 게임입니다.
차 한 대를 목적지까지 운전하면, 다음 라운드에 그 주행이 **그대로 리플레이**되어 도로를 달립니다.
과거의 나와 충돌하지 않고 모든 차량을 제시간에 보내세요!

<!-- 플레이 화면 GIF/스크린샷을 이 자리에 추가하세요.
     예: ![gameplay](docs/gameplay.gif) -->

## 게임 방법

- 차는 자동으로 전진합니다. 화면 **왼쪽/오른쪽을 터치**해 조향하세요.
- 초록 원(목적지)에 도착하면 다음 차량으로 넘어갑니다. (+8초 보너스)
- 리플레이 차량과 충돌하면 이번 차량이 출발점부터 다시 시작하고, 시간은 계속 흐릅니다.
- 리플레이 차량을 충돌 없이 아슬아슬하게 스치면 **니어미스 보너스**(+1초)를 받습니다.
- 도로에 떨어진 시간 아이템을 주우면 추가 시간을 얻습니다.
- 코너 반사경이 사각지대에 다가오는 리플레이 차량을 미리 경고해줍니다.
- 6대를 모두 보내면 승리! 남은 시간에 따라 별 1~3개 등급이 매겨집니다. 🎉

## 왜 만들었나 / 배운 점

Android에서 상태(state) 하나로 게임 루프 전체를 굴리는 걸 연습하려고 만든 프로젝트입니다.
`GameEngine`을 Android 의존성이 전혀 없는 순수 Kotlin `object`로 분리해두니 물리/충돌 로직을
JVM에서 바로 단위 테스트할 수 있었고, 이 구조 덕분에 "리플레이 차량과의 충돌", "니어미스 판정"
같은 룰을 코드로 먼저 검증하고 나서 화면에 붙이는 순서로 개발할 수 있었습니다.
`GameViewModel`이 16ms 틱으로 `StateFlow<GameUiState>`를 발행하고 `GameView`는 그 상태를
Canvas에 그리기만 하도록 역할을 완전히 분리한 것도 이 프로젝트에서 얻은 소득입니다.

## 아키텍처 (MVVM)

```
app/src/main/java/io/github/cho1017/commutechaos/
├── model/          # 순수 게임 로직 — Android 의존성 없음
│   ├── GameEngine.kt    # 물리, 충돌, 니어미스, 별 등급 판정
│   ├── Level.kt         # 맵(건물 블록, 반사경, 아이템)과 차량 6종 정의
│   └── GameModels.kt    # 상태 데이터 클래스 (GameUiState 등)
├── viewmodel/
│   └── GameViewModel.kt # 60fps 게임 루프, 주행 기록/리플레이, StateFlow 발행
└── view/
    ├── GameView.kt        # Canvas 렌더링 + 터치 입력 (로직 없음)
    ├── ChaseRenderer.kt   # 3인칭 추격 카메라 시점의 의사 3D 렌더링
    └── MainActivity.kt    # StateFlow 구독 → GameView에 전달
```

- **Model**은 순수 Kotlin이라 JVM 단위 테스트가 가능합니다 → `GameEngineTest.kt`
- **ViewModel**은 코루틴으로 16ms 틱 게임 루프를 돌리고 `StateFlow<GameUiState>`를 발행합니다.
- **View**는 상태를 그리기만 하고, 입력을 콜백으로 ViewModel에 위임합니다.

## 빌드 & 테스트

```bash
./gradlew :app:assembleDebug          # 빌드
./gradlew :app:testDebugUnitTest      # 단위 테스트
```

Android Studio에서 열어 Run ▶ 하면 바로 실행됩니다. (minSdk 24)

푸시/PR마다 GitHub Actions에서 단위 테스트와 디버그 빌드를 자동으로 검증합니다
(`.github/workflows/android-ci.yml`). 빌드된 APK는 Actions 실행의 Artifacts에서 내려받을 수 있습니다.

## 라이선스

[MIT License](LICENSE)
