package io.github.cho1017.commutechaos.model

/**
 * 기본 레벨: 세로로 긴 동네 맵 (블록 3열 × 4행).
 * 좌표계는 월드 단위(0..WORLD_W, 0..WORLD_H)이며 View가 화면에 맞게 스케일한다.
 *
 * 도로망은 격자다: 내부 세로 도로 2개([vRoadXs]) + 내부 가로 도로 3개([hRoadYs]) +
 * 외곽 순환 도로. 도로 표시(중앙선/차로선/가장자리선/정지선/횡단보도)는 이 격자에서
 * 기계적으로 생성한다.
 *
 * 가로 도로 2(y=[BRIDGE_Y])는 세로 도로 2(x=920) 위를 고가(다리)로 넘는다.
 * 그 지점은 평면 교차로가 아니므로 횡단보도/정지선/반사경이 없고,
 * 아래를 지나는 세로 도로의 차선은 다리 밑으로 계속 이어진다.
 */
object Level {
    const val WORLD_W = 1400f
    const val WORLD_H = 2200f

    /** 외곽 벽 두께. */
    const val BORDER = 25f

    /** 내부 도로 반폭 (도로 전체 폭 200). */
    const val ROAD_HALF = 100f

    /** 내부 세로 도로 중심 x. */
    private val vRoadXs = listOf(500f, 920f)

    /** 내부 가로 도로 중심 y. */
    private val hRoadYs = listOf(500f, 1100f, 1700f)

    // ── 다리(고가): 가로 도로 2가 세로 도로 2 위를 넘는다 ─────────────────
    const val BRIDGE_Y = 1100f
    const val BRIDGE_HALF_W = 100f   // 도로 전체 폭과 같아 옆 건물과 틈이 없다
    const val BRIDGE_H = 55f
    const val BRIDGE_RAMP_W = 620f   // 서쪽 램프 시작 x (z=0)
    const val BRIDGE_DECK_W = 780f   // 상판 시작 x (z=BRIDGE_H)
    const val BRIDGE_DECK_E = 1060f  // 상판 끝 x
    const val BRIDGE_RAMP_E = 1220f  // 동쪽 램프 끝 x (z=0)

    /**
     * 지상 차량에게 벽처럼 작동하는 램프의 "높은 부분".
     *
     * 램프의 낮은 초입(양끝 40)은 일부러 벽에서 뺀다: 접근하는 차의 앞부분이 중심보다
     * 먼저 램프에 닿는데, 초입까지 벽으로 막으면 차 중심이 램프에 올라 "다리 타는 중"
     * 판정을 받기 전에 밀려나서 영영 못 올라간다. 초입은 노면이 낮아 벽이 아니라
     * 오르는 길이 맞다. 높은 부분만 벽이면 다리 밑을 지나던 차가 옆·정면으로
     * 들이받는 것은 여전히 막힌다.
     */
    val rampSolids: List<Wall> = listOf(
        Wall(BRIDGE_RAMP_W + 40f, BRIDGE_Y - BRIDGE_HALF_W, BRIDGE_DECK_W, BRIDGE_Y + BRIDGE_HALF_W),
        Wall(BRIDGE_DECK_E, BRIDGE_Y - BRIDGE_HALF_W, BRIDGE_RAMP_E - 40f, BRIDGE_Y + BRIDGE_HALF_W),
    )

    /** 다리 노면 높이. 다리 차선(y) 안에서 x에 따라 0 → BRIDGE_H → 0. */
    fun bridgeProfile(x: Float): Float = when {
        x < BRIDGE_RAMP_W || x > BRIDGE_RAMP_E -> 0f
        x < BRIDGE_DECK_W -> (x - BRIDGE_RAMP_W) / (BRIDGE_DECK_W - BRIDGE_RAMP_W) * BRIDGE_H
        x <= BRIDGE_DECK_E -> BRIDGE_H
        else -> (BRIDGE_RAMP_E - x) / (BRIDGE_RAMP_E - BRIDGE_DECK_E) * BRIDGE_H
    }

    /** (x는 무관하게) 이 y가 다리 도로 폭 안에 있는가. */
    fun inBridgeLane(y: Float): Boolean = kotlin.math.abs(y - BRIDGE_Y) <= BRIDGE_HALF_W

    /**
     * 도로 위 중앙 화단(분리대). 렌더러가 건물과 다르게(녹지로) 그린다.
     * walls보다 먼저 초기화되어야 한다.
     */
    val gardens: List<Wall> = listOf(
        Wall(480f, 700f, 520f, 900f),    // 세로 도로 1 (x=500)
        Wall(900f, 1300f, 940f, 1500f),  // 세로 도로 2 (x=920)
    )

    /** 건물 블록. 사이사이가 도로가 된다. 화단 옆 블록은 통로 폭 100을 확보하도록 좁힌다. */
    val walls: List<Wall> = listOf(
        // 외곽 벽
        Wall(0f, 0f, WORLD_W, BORDER),
        Wall(0f, WORLD_H - BORDER, WORLD_W, WORLD_H),
        Wall(0f, 0f, BORDER, WORLD_H),
        Wall(WORLD_W - BORDER, 0f, WORLD_W, WORLD_H),
        // 1행 (y 180..400)
        Wall(180f, 180f, 400f, 400f),
        Wall(600f, 180f, 820f, 400f),
        Wall(1020f, 180f, 1220f, 400f),
        // 2행 (y 600..1000) — 화단1 옆이라 1·2열을 좁힌다
        Wall(180f, 600f, 380f, 1000f),
        Wall(620f, 600f, 820f, 1000f),
        Wall(1020f, 600f, 1220f, 1000f),
        // 3행 (y 1200..1600) — 화단2 옆이라 2·3열을 좁힌다
        Wall(180f, 1200f, 400f, 1600f),
        Wall(600f, 1200f, 800f, 1600f),
        Wall(1040f, 1200f, 1220f, 1600f),
        // 4행 (y 1800..2020)
        Wall(180f, 1800f, 400f, 2020f),
        Wall(600f, 1800f, 820f, 2020f),
        Wall(1020f, 1800f, 1220f, 2020f),
    ) + gardens

    /** [start, end] 구간에서 blocked 구간들을 (여유 margin을 두고) 뺀 열린 구간 목록. */
    private fun spans(
        start: Float,
        end: Float,
        blocked: List<Pair<Float, Float>>,
        margin: Float,
    ): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        var cur = start
        for ((bs, be) in blocked.sortedBy { it.first }) {
            if (bs - margin > cur) out.add(cur to (bs - margin))
            cur = maxOf(cur, be + margin)
        }
        if (end > cur) out.add(cur to end)
        return out
    }

    /** 평면 교차로 목록. 다리 밑 (920, 1100)은 입체 교차라 제외한다. */
    private val junctions: List<Pair<Float, Float>> =
        vRoadXs.flatMap { vx -> hRoadYs.map { hy -> vx to hy } }
            .filter { (vx, hy) -> !(vx == 920f && hy == BRIDGE_Y) }

    /** 세로 도로 vx의 차선이 끊겨야 하는 구간 (평면 교차로만; 다리 밑은 이어진다). */
    private fun vBlocked(vx: Float): List<Pair<Float, Float>> =
        hRoadYs.filter { hy -> junctions.contains(vx to hy) }
            .map { it - ROAD_HALF to it + ROAD_HALF }

    private val bridgeBlock = (BRIDGE_RAMP_W - 20f) to (BRIDGE_RAMP_E + 20f)

    /** 가로 도로 hy의 차선이 끊겨야 하는 구간. 다리 도로는 다리 구간 전체가 끊긴다(상판이 대신한다). */
    private fun hBlocked(hy: Float): List<Pair<Float, Float>> =
        if (hy == BRIDGE_Y) {
            vRoadXs.filter { vx -> junctions.contains(vx to hy) }
                .map { it - ROAD_HALF to it + ROAD_HALF } + listOf(bridgeBlock)
        } else {
            vRoadXs.map { it - ROAD_HALF to it + ROAD_HALF }
        }

    /** 중앙 이중 황색 실선 (내부 도로, 중심 ±5). 교차로/화단/다리 구간은 비운다. */
    val centerLines: List<Segment> = buildList {
        for (vx in vRoadXs) {
            val gardenBlocks = gardens
                .filter { it.left < vx && vx < it.right }
                .map { it.top to it.bottom }
            for ((a, b) in spans(60f, WORLD_H - 60f, vBlocked(vx) + gardenBlocks, 20f)) {
                add(Segment(vx - 5f, a, vx - 5f, b))
                add(Segment(vx + 5f, a, vx + 5f, b))
            }
        }
        for (hy in hRoadYs) {
            for ((a, b) in spans(60f, WORLD_W - 60f, hBlocked(hy), 20f)) {
                add(Segment(a, hy - 5f, b, hy - 5f))
                add(Segment(a, hy + 5f, b, hy + 5f))
            }
        }
    }

    /** 같은 방향 차로를 나누는 흰 점선 (중심 ±50). */
    val laneDashes: List<Segment> = buildList {
        for (vx in vRoadXs) {
            for ((a, b) in spans(60f, WORLD_H - 60f, vBlocked(vx), 20f)) {
                add(Segment(vx - 50f, a, vx - 50f, b))
                add(Segment(vx + 50f, a, vx + 50f, b))
            }
        }
        for (hy in hRoadYs) {
            for ((a, b) in spans(60f, WORLD_W - 60f, hBlocked(hy), 20f)) {
                add(Segment(a, hy - 50f, b, hy - 50f))
                add(Segment(a, hy + 50f, b, hy + 50f))
            }
        }
    }

    /**
     * 도로 가장자리 흰 실선. 내부 도로는 양쪽(±88),
     * 외곽 순환 도로는 블록 쪽에만 — 맵 끝 쪽은 연석뿐이라 선이 없는 게 자연스럽다.
     */
    val edgeLines: List<Segment> = buildList {
        val off = ROAD_HALF - 12f
        for (vx in vRoadXs) {
            for ((a, b) in spans(60f, WORLD_H - 60f, vBlocked(vx), 6f)) {
                add(Segment(vx - off, a, vx - off, b))
                add(Segment(vx + off, a, vx + off, b))
            }
        }
        for (hy in hRoadYs) {
            for ((a, b) in spans(60f, WORLD_W - 60f, hBlocked(hy), 6f)) {
                add(Segment(a, hy - off, b, hy - off))
                add(Segment(a, hy + off, b, hy + off))
            }
        }
        // 외곽 순환 도로 (블록 가장자리에서 10 안쪽)
        val hB = hRoadYs.map { it - ROAD_HALF to it + ROAD_HALF }
        val vB = vRoadXs.map { it - ROAD_HALF to it + ROAD_HALF }
        for ((a, b) in spans(190f, WORLD_H - 190f, hB, 6f)) {
            add(Segment(170f, a, 170f, b))
            add(Segment(WORLD_W - 170f, a, WORLD_W - 170f, b))
        }
        for ((a, b) in spans(190f, WORLD_W - 190f, vB, 6f)) {
            add(Segment(a, 170f, b, 170f))
            add(Segment(a, WORLD_H - 170f, b, WORLD_H - 170f))
        }
    }

    /** 교차로 진입 정지선. 우측통행 기준 진입 차로(도로 절반)에만 긋는다. */
    val stopLines: List<Segment> = buildList {
        for ((vx, hy) in junctions) {
            add(Segment(vx - 88f, hy - 102f, vx - 6f, hy - 102f))  // 북쪽 진입(남행 차로)
            add(Segment(vx + 6f, hy + 102f, vx + 88f, hy + 102f))  // 남쪽 진입(북행 차로)
            add(Segment(vx - 102f, hy + 6f, vx - 102f, hy + 88f))  // 서쪽 진입(동행 차로)
            add(Segment(vx + 102f, hy - 88f, vx + 102f, hy - 6f))  // 동쪽 진입(서행 차로)
        }
    }

    /** 평면 교차로 네 진입부의 횡단보도. */
    val crosswalks: List<Crosswalk> = buildList {
        for ((vx, hy) in junctions) {
            // 세로 도로를 건너는 남북 진입부 (줄무늬가 x 방향으로 반복)
            add(Crosswalk(vx - 88f, hy - 94f, vx + 88f, hy - 62f, stripesAlongX = true))
            add(Crosswalk(vx - 88f, hy + 62f, vx + 88f, hy + 94f, stripesAlongX = true))
            // 가로 도로를 건너는 동서 진입부 (줄무늬가 y 방향으로 반복)
            add(Crosswalk(vx - 94f, hy - 88f, vx - 62f, hy + 88f, stripesAlongX = false))
            add(Crosswalk(vx + 62f, hy - 88f, vx + 94f, hy + 88f, stripesAlongX = false))
        }
    }

    /**
     * 코너 반사경 위치. 평면 교차로마다 대각선 모퉁이 두 곳에 세워져 있고,
     * 근처에 리플레이 차량이 오면 빛나서 사각지대를 경고한다.
     */
    val mirrors: List<Pair<Float, Float>> = buildList {
        for ((vx, hy) in junctions) {
            add(Pair(vx - ROAD_HALF, hy - ROAD_HALF))
            add(Pair(vx + ROAD_HALF, hy + ROAD_HALF))
        }
    }

    /** 시간 아이템 스폰 위치. 라운드마다 다시 생긴다. 일부러 동선에서 살짝 벗어난 곳에 둔다. */
    val pickupSpots: List<Pickup> = listOf(
        Pickup(500f, 500f),
        Pickup(920f, 500f),
        Pickup(500f, 1700f),
        Pickup(920f, 1100f),
        Pickup(75f, 1100f),
        Pickup(1325f, 1700f),
    )

    val vehicles: List<VehicleSpec> = listOf(
        VehicleSpec(
            name = "출근하는 회사원",
            story = "9시 회의인데 벌써 8시 52분. 부장님이 기다린다.",
            startX = 75f, startY = 2130f, startHeading = (-90f).toRad(),
            goalX = 1325f, goalY = 75f,
            speed = 240f, turnRate = 3.0f, radius = 24f,
            color = 0xFFE53935,
        ),
        VehicleSpec(
            name = "지각한 대학생",
            story = "1교시 출석 체크까지 5분. 교수님은 지각을 싫어한다.",
            startX = 1325f, startY = 2130f, startHeading = (-90f).toRad(),
            goalX = 75f, goalY = 75f,
            speed = 285f, turnRate = 3.6f, radius = 21f,
            color = 0xFF1E88E5,
        ),
        VehicleSpec(
            name = "신문 배달부",
            story = "마지막 한 부만 돌리면 끝. 오늘도 무사고이길.",
            startX = 75f, startY = 75f, startHeading = 0f.toRad(),
            goalX = 1325f, goalY = 2130f,
            speed = 255f, turnRate = 3.2f, radius = 22f,
            color = 0xFFFDD835,
        ),
        VehicleSpec(
            name = "우유 트럭",
            story = "적재함 가득한 우유병. 급커브는 곧 대참사다.",
            startX = 1325f, startY = 75f, startHeading = 180f.toRad(),
            goalX = 75f, goalY = 2130f,
            speed = 200f, turnRate = 2.0f, radius = 32f,
            color = 0xFFF5F5F5,
        ),
        VehicleSpec(
            name = "드라이브 나온 할머니",
            story = "급할 것 하나 없다. 오늘은 고가도로 위 경치가 좋구나.",
            startX = 75f, startY = 1100f, startHeading = 0f.toRad(),
            goalX = 1325f, goalY = 1100f,
            speed = 185f, turnRate = 2.4f, radius = 24f,
            color = 0xFF8E24AA,
        ),
        VehicleSpec(
            name = "퇴근하는 택시",
            story = "사납금은 채웠다. 고가 넘어 집까지 풀악셀.",
            startX = 1325f, startY = 1100f, startHeading = 180f.toRad(),
            goalX = 75f, goalY = 1100f,
            speed = 310f, turnRate = 4.0f, radius = 22f,
            color = 0xFFFB8C00,
        ),
    )

    private fun Float.toRad(): Float = (this * Math.PI / 180.0).toFloat()
}
