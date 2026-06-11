package com.example.myapplication.model

/**
 * 기본 레벨: 세로로 긴 동네 맵.
 * 좌표계는 월드 단위(0..WORLD_W, 0..WORLD_H)이며 View가 화면에 맞게 스케일한다.
 */
object Level {
    const val WORLD_W = 1000f
    const val WORLD_H = 1600f

    /** 건물 블록. 사이사이가 도로가 된다. */
    val walls: List<Wall> = listOf(
        // 바깥 테두리는 GameEngine이 월드 경계로 처리하므로 내부 블록만 둔다.
        Wall(150f, 150f, 420f, 420f),
        Wall(580f, 150f, 850f, 420f),
        Wall(150f, 580f, 420f, 1020f),
        Wall(580f, 580f, 850f, 1020f),
        Wall(150f, 1180f, 420f, 1450f),
        Wall(580f, 1180f, 850f, 1450f),
        // 중앙 분리 화단
        Wall(470f, 700f, 530f, 900f),
    )

    val vehicles: List<VehicleSpec> = listOf(
        VehicleSpec("출근하는 회사원", 75f, 1530f, -90f.toRad(), 925f, 75f, 240f, 0xFFE53935),
        VehicleSpec("지각한 대학생", 925f, 1530f, -90f.toRad(), 75f, 75f, 270f, 0xFF1E88E5),
        VehicleSpec("신문 배달부", 75f, 75f, 0f.toRad(), 925f, 1530f, 255f, 0xFFFDD835),
        VehicleSpec("우유 트럭", 925f, 75f, 180f.toRad(), 75f, 1530f, 210f, 0xFFF5F5F5),
        VehicleSpec("드라이브 나온 할머니", 75f, 800f, 0f.toRad(), 925f, 800f, 195f, 0xFF8E24AA),
        VehicleSpec("퇴근하는 택시", 925f, 800f, 180f.toRad(), 75f, 800f, 285f, 0xFFFB8C00),
    )

    private fun Float.toRad(): Float = (this * Math.PI / 180.0).toFloat()
}
