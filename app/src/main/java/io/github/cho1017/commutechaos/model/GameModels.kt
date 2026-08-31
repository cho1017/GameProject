package io.github.cho1017.commutechaos.model

/** 한 프레임의 차량 위치/방향. 리플레이 기록의 단위. */
data class Pose(val x: Float, val y: Float, val heading: Float)

/** 축 정렬 사각형 장애물(건물 블록). */
data class Wall(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** 장식용 선분 (도로 중앙선 등). 충돌 없음. */
data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/** 라운드마다 등장하는 차량의 정의. 개성은 speed/turnRate/radius 조합으로 만든다. */
data class VehicleSpec(
    val name: String,
    /** 라운드 시작 화면에 보여줄 한 줄 사연. */
    val story: String,
    val startX: Float,
    val startY: Float,
    val startHeading: Float,
    val goalX: Float,
    val goalY: Float,
    /** 순항 속도 (unit/s). */
    val speed: Float,
    /** 조향 각속도 (rad/s). 클수록 민첩하다. */
    val turnRate: Float,
    /** 충돌 반경. 클수록 크고 부딪히기 쉽다. */
    val radius: Float,
    val color: Long,
)

/** 현재 조작 중인 차량의 물리 상태. */
data class CarState(
    val x: Float,
    val y: Float,
    val heading: Float,
    val speed: Float,
)

/** 과거 라운드 차량의 리플레이 표시 상태. */
data class GhostState(
    val pose: Pose,
    val color: Long,
    val radius: Float,
    val visible: Boolean,
)

/** 코너 반사경. 근처에 리플레이 차량이 있으면 [alert]가 켜진다. */
data class MirrorState(val x: Float, val y: Float, val alert: Boolean)

/** 길에 떨어진 시간 아이템. 주우면 제한 시간이 늘어난다. */
data class Pickup(val x: Float, val y: Float, val collected: Boolean = false)

/** 플레이어 뒤에 남는 잔상 점. alpha는 1→0으로 줄어든다. */
data class TrailPoint(val x: Float, val y: Float, val alpha: Float)

enum class Phase { INTRO, DRIVING, GAME_OVER, WIN }

/** 온라인 리더보드 한 줄. Firestore POJO 매핑을 위해 모든 필드에 기본값이 있어야 한다. */
data class LeaderboardEntry(
    val nickname: String = "",
    val timeLeft: Float = 0f,
    val stars: Int = 0,
    val createdAt: Long = 0L,
)

/** 온라인 리더보드 패널의 조회 상태. */
enum class LeaderboardStatus { IDLE, LOADING, LOADED, UNAVAILABLE }

/** View가 그리는 데 필요한 모든 것. ViewModel이 매 틱 발행한다. */
data class GameUiState(
    val phase: Phase = Phase.INTRO,
    val roundIndex: Int = 0,
    val totalVehicles: Int = 0,
    val vehicleName: String = "",
    val vehicleStory: String = "",
    val timeLeft: Float = 0f,
    val player: CarState = CarState(0f, 0f, 0f, 0f),
    val playerColor: Long = 0xFFFFFFFF,
    val playerRadius: Float = 24f,
    val ghosts: List<GhostState> = emptyList(),
    val pickups: List<Pickup> = emptyList(),
    val mirrors: List<MirrorState> = emptyList(),
    val trail: List<TrailPoint> = emptyList(),
    val goalX: Float = 0f,
    val goalY: Float = 0f,
    val walls: List<Wall> = emptyList(),
    /** 충돌 직후 1→0으로 줄어드는 화면 플래시 강도. */
    val penaltyFlash: Float = 0f,
    /** 충돌 직후 1→0으로 줄어드는 화면 흔들림 강도. */
    val shake: Float = 0f,
    /** 목적지 펄스 등 시간 기반 연출용 누적 시간(초). */
    val elapsed: Float = 0f,
    /** 클리어 시 남은 시간 최고 기록. 없으면 null. */
    val bestTime: Float? = null,
    /** 이번 판에 신기록을 세웠는가 (WIN 화면용). */
    val newRecord: Boolean = false,
    /** 승리 시 별 등급(1~3). 진행 중엔 0. */
    val stars: Int = 0,
    /** 역대 최고 별 등급. */
    val bestStars: Int = 0,
    /** 현재 니어미스 콤보 수. 충돌하면 0으로 초기화. */
    val nearMissCombo: Int = 0,
    /** 니어미스 직후 1→0으로 줄어드는 연출 강도(토스트 표시용). */
    val nearMissFlash: Float = 0f,
    /** 이 기기에 저장된 내 온라인 리더보드 별명. */
    val nickname: String = "",
    /** 온라인 리더보드 상위 기록 (남은 시간 내림차순). */
    val leaderboard: List<LeaderboardEntry> = emptyList(),
    /** 리더보드 조회 상태. */
    val leaderboardStatus: LeaderboardStatus = LeaderboardStatus.IDLE,
)
