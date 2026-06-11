package com.example.myapplication.model

/** 한 프레임의 차량 위치/방향. 리플레이 기록의 단위. */
data class Pose(val x: Float, val y: Float, val heading: Float)

/** 축 정렬 사각형 장애물(건물 블록). */
data class Wall(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** 라운드마다 등장하는 차량의 정의. */
data class VehicleSpec(
    val name: String,
    val startX: Float,
    val startY: Float,
    val startHeading: Float,
    val goalX: Float,
    val goalY: Float,
    val speed: Float,
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
    val visible: Boolean,
)

enum class Phase { INTRO, DRIVING, GAME_OVER, WIN }

/** View가 그리는 데 필요한 모든 것. ViewModel이 매 틱 발행한다. */
data class GameUiState(
    val phase: Phase = Phase.INTRO,
    val roundIndex: Int = 0,
    val totalVehicles: Int = 0,
    val vehicleName: String = "",
    val timeLeft: Float = 0f,
    val player: CarState = CarState(0f, 0f, 0f, 0f),
    val playerColor: Long = 0xFFFFFFFF,
    val ghosts: List<GhostState> = emptyList(),
    val goalX: Float = 0f,
    val goalY: Float = 0f,
    val walls: List<Wall> = emptyList(),
    /** 충돌 직후 1→0으로 줄어드는 화면 플래시 강도. */
    val penaltyFlash: Float = 0f,
)
