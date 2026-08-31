package io.github.cho1017.commutechaos.data

/**
 * 온라인 리더보드(Firestore) 연결 정보.
 *
 * google-services.json이나 Google Services Gradle 플러그인 없이, Firebase 콘솔에 나오는
 * 값 3개만 그대로 채워 넣으면 동작하도록 [com.google.firebase.FirebaseOptions]로 수동
 * 초기화한다.
 *
 * 값을 채우는 법:
 * 1. https://console.firebase.google.com 에서 새 프로젝트 생성 (무료 Spark 요금제로 충분)
 * 2. 프로젝트 개요 → Android 아이콘을 눌러 앱 추가
 *    - 패키지 이름: io.github.cho1017.commutechaos
 *    - google-services.json은 내려받아도 되지만 이 프로젝트에서는 사용하지 않는다
 * 3. 프로젝트 설정(⚙️) → 일반 탭 → "내 앱"에서 방금 등록한 Android 앱을 선택하면
 *    API 키 / 앱 ID / 프로젝트 ID가 그대로 보인다. 그 값을 아래에 붙여넣는다.
 * 4. Firestore Database를 만들고(프로덕션 모드), 저장소 루트의 firestore.rules 내용을
 *    Firestore 콘솔의 "규칙" 탭에 붙여넣고 게시한다.
 *
 * 값을 채우기 전까지는 [isConfigured]가 false라 리더보드 관련 네트워크 호출이 전혀
 * 일어나지 않는다 — 게임 자체는 항상 오프라인으로 완결되게 동작해야 하기 때문이다.
 */
object LeaderboardConfig {
    const val API_KEY = "YOUR_API_KEY"
    const val APPLICATION_ID = "YOUR_APPLICATION_ID" // 예: 1:1234567890123:android:abcdef1234567890
    const val PROJECT_ID = "YOUR_PROJECT_ID"

    val isConfigured: Boolean
        get() = API_KEY != "YOUR_API_KEY" && API_KEY.isNotBlank() &&
            APPLICATION_ID != "YOUR_APPLICATION_ID" && APPLICATION_ID.isNotBlank() &&
            PROJECT_ID != "YOUR_PROJECT_ID" && PROJECT_ID.isNotBlank()
}
