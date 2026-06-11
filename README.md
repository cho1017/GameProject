# 출근길 대란 🚗

[Does Not Commute](https://en.wikipedia.org/wiki/Does_Not_Commute) 스타일의 탑다운 드라이빙 퍼즐 게임입니다.
차 한 대를 목적지까지 운전하면, 다음 라운드에 그 주행이 **그대로 리플레이**되어 도로를 달립니다.
과거의 나와 충돌하지 않고 모든 차량을 제시간에 보내세요!

## 게임 방법

- 차는 자동으로 전진합니다. 화면 **왼쪽/오른쪽을 터치**해 조향하세요.
- 초록 원(목적지)에 도착하면 다음 차량으로 넘어갑니다. (+8초 보너스)
- 리플레이 차량과 충돌하면 **-3초** 감점, 시간이 다 되면 게임 오버.
- 6대를 모두 보내면 승리! 🎉

## 아키텍처 (MVVM)

```
app/src/main/java/com/example/myapplication/
├── model/          # 순수 게임 로직 — Android 의존성 없음
│   ├── GameEngine.kt    # 물리, 충돌, 도착 판정
│   ├── Level.kt         # 맵(건물 블록)과 차량 6종 정의
│   └── GameModels.kt    # 상태 데이터 클래스 (GameUiState 등)
├── viewmodel/
│   └── GameViewModel.kt # 60fps 게임 루프, 주행 기록/리플레이, StateFlow 발행
└── view/
    ├── GameView.kt      # Canvas 렌더링 + 터치 입력 (로직 없음)
    └── MainActivity.kt  # StateFlow 구독 → GameView에 전달
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
