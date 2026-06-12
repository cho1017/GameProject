package com.example.myapplication.model

/**
 * 기본 레벨: 세로로 긴 동네 맵.
 * 좌표계는 월드 단위(0..WORLD_W, 0..WORLD_H)이며 View가 화면에 맞게 스케일한다.
 */
object Level {
    const val WORLD_W = 1000f
    const val WORLD_H = 1600f

    /** 외곽 벽 두께. */
    const val BORDER = 25f

    /** 건물 블록. 사이사이가 도로가 된다. */
    val walls: List<Wall> = listOf(
        // 외곽 벽
        Wall(0f, 0f, WORLD_W, BORDER),
        Wall(0f, WORLD_H - BORDER, WORLD_W, WORLD_H),
        Wall(0f, 0f, BORDER, WORLD_H),
        Wall(WORLD_W - BORDER, 0f, WORLD_W, WORLD_H),
        // 건물 블록 (블록을 줄여 도로 폭을 확보: 외곽 155, 중앙 세로 200)
        Wall(180f, 180f, 400f, 400f),
        Wall(600f, 180f, 820f, 400f),
        // 가운데 줄은 화단 옆 길이 넓어지도록 블록을 좁힌다
        Wall(180f, 600f, 380f, 1000f),
        Wall(620f, 600f, 820f, 1000f),
        Wall(180f, 1200f, 400f, 1430f),
        Wall(600f, 1200f, 820f, 1430f),
        // 중앙 분리 화단 (양옆 통로 폭 100 - 우유 트럭도 여유 있게)
        Wall(480f, 700f, 520f, 900f),
    )

    /**
     * 코너 반사경 위치. 교차로 모퉁이에 세워져 있고,
     * 근처에 리플레이 차량이 오면 빛나서 사각지대를 경고한다.
     */
    val mirrors: List<Pair<Float, Float>> = listOf(
        // 위쪽 교차로 (y 400~600)
        Pair(400f, 400f), Pair(600f, 400f),
        Pair(380f, 600f), Pair(620f, 600f),
        // 아래쪽 교차로 (y 1000~1200)
        Pair(380f, 1000f), Pair(620f, 1000f),
        Pair(400f, 1200f), Pair(600f, 1200f),
    )

    /** 시간 아이템 스폰 위치. 라운드마다 다시 생긴다. 일부러 동선에서 살짝 벗어난 곳에 둔다. */
    val pickupSpots: List<Pickup> = listOf(
        Pickup(500f, 500f),
        Pickup(500f, 1100f),
        Pickup(75f, 500f),
        Pickup(925f, 1100f),
    )

    val vehicles: List<VehicleSpec> = listOf(
        VehicleSpec(
            name = "출근하는 회사원",
            story = "9시 회의인데 벌써 8시 52분. 부장님이 기다린다.",
            startX = 75f, startY = 1530f, startHeading = (-90f).toRad(),
            goalX = 925f, goalY = 75f,
            speed = 240f, turnRate = 3.0f, radius = 24f,
            color = 0xFFE53935,
        ),
        VehicleSpec(
            name = "지각한 대학생",
            story = "1교시 출석 체크까지 5분. 교수님은 지각을 싫어한다.",
            startX = 925f, startY = 1530f, startHeading = (-90f).toRad(),
            goalX = 75f, goalY = 75f,
            speed = 285f, turnRate = 3.6f, radius = 21f,
            color = 0xFF1E88E5,
        ),
        VehicleSpec(
            name = "신문 배달부",
            story = "마지막 한 부만 돌리면 끝. 오늘도 무사고이길.",
            startX = 75f, startY = 75f, startHeading = 0f.toRad(),
            goalX = 925f, goalY = 1530f,
            speed = 255f, turnRate = 3.2f, radius = 22f,
            color = 0xFFFDD835,
        ),
        VehicleSpec(
            name = "우유 트럭",
            story = "적재함 가득한 우유병. 급커브는 곧 대참사다.",
            startX = 925f, startY = 75f, startHeading = 180f.toRad(),
            goalX = 75f, goalY = 1530f,
            speed = 200f, turnRate = 2.0f, radius = 32f,
            color = 0xFFF5F5F5,
        ),
        VehicleSpec(
            name = "드라이브 나온 할머니",
            story = "급할 것 하나 없다. 하지만 도로는 그렇지 않지.",
            startX = 75f, startY = 800f, startHeading = 0f.toRad(),
            goalX = 925f, goalY = 800f,
            speed = 185f, turnRate = 2.4f, radius = 24f,
            color = 0xFF8E24AA,
        ),
        VehicleSpec(
            name = "퇴근하는 택시",
            story = "사납금은 채웠다. 이제 집까지 풀악셀.",
            startX = 925f, startY = 800f, startHeading = 180f.toRad(),
            goalX = 75f, goalY = 800f,
            speed = 310f, turnRate = 4.0f, radius = 22f,
            color = 0xFFFB8C00,
        ),
    )

    private fun Float.toRad(): Float = (this * Math.PI / 180.0).toFloat()
}
