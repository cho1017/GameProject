package io.github.cho1017.commutechaos.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 순수 게임 물리/규칙. Android 의존성이 없어 JVM 단위 테스트가 가능하다.
 */
object GameEngine {

    const val GOAL_RADIUS = 70f
    const val PICKUP_RADIUS = 34f

    /** 벽에 부딪히면 속도가 이 비율로 줄고, 이후 다시 가속한다. */
    private const val WALL_SLOWDOWN = 0.35f
    private const val ACCEL = 180f // unit/s^2

    /**
     * 다리 노면 추종 한계. 현재 높이와 노면 높이의 차가 이보다 작을 때만
     * "다리를 타고 있는" 것으로 본다 — 램프를 오르는 차는 연속적으로 따라가고,
     * 다리 밑을 지나는 차가 갑자기 상판 위로 점프하는 일은 없다.
     */
    private const val Z_SNAP = 10f

    /** 원(차)을 사각형 밖으로 밀어낸다. 밀었으면 [pos]의 x, y를 고쳐 쓰고 true. */
    private fun pushCircleOut(pos: FloatArray, radius: Float, w: Wall): Boolean {
        val x = pos[0]
        val y = pos[1]
        val cx = x.coerceIn(w.left, w.right)
        val cy = y.coerceIn(w.top, w.bottom)
        val dx = x - cx
        val dy = y - cy
        val dist = hypot(dx, dy)
        if (dist >= radius) return false
        if (dist > 1e-4f) {
            val push = radius - dist
            pos[0] = x + dx / dist * push
            pos[1] = y + dy / dist * push
        } else {
            // 중심이 사각형 내부: 가장 가까운 변으로 탈출
            val toLeft = x - w.left
            val toRight = w.right - x
            val toTop = y - w.top
            val toBottom = w.bottom - y
            when (minOf(toLeft, toRight, toTop, toBottom)) {
                toLeft -> pos[0] = w.left - radius
                toRight -> pos[0] = w.right + radius
                toTop -> pos[1] = w.top - radius
                else -> pos[1] = w.bottom + radius
            }
        }
        return true
    }

    /**
     * 차량을 dt초만큼 전진시킨다. 다리(고가) 차선 안에서는 노면 높이(z)를 따라간다.
     * @param steer -1(좌회전)..1(우회전)
     * @param targetSpeed 이 차량의 순항 속도
     * @param turnRate 조향 각속도 (rad/s)
     * @param radius 차량 충돌 반경
     */
    fun step(
        car: CarState,
        steer: Float,
        targetSpeed: Float,
        turnRate: Float,
        radius: Float,
        dt: Float,
        walls: List<Wall>,
    ): CarState {
        val heading = car.heading + steer.coerceIn(-1f, 1f) * turnRate * dt
        val speed = (car.speed + ACCEL * dt).coerceAtMost(targetSpeed)
        val pos = floatArrayOf(
            car.x + cos(heading) * speed * dt,
            car.y + sin(heading) * speed * dt,
        )
        var newSpeed = speed

        // 월드 경계
        var hitWall = false
        if (pos[0] < radius) { pos[0] = radius; hitWall = true }
        if (pos[0] > Level.WORLD_W - radius) { pos[0] = Level.WORLD_W - radius; hitWall = true }
        if (pos[1] < radius) { pos[1] = radius; hitWall = true }
        if (pos[1] > Level.WORLD_H - radius) { pos[1] = Level.WORLD_H - radius; hitWall = true }

        // 건물 블록: 원-사각형 충돌, 가장 얕은 축으로 밀어낸다.
        for (w in walls) {
            if (pushCircleOut(pos, radius, w)) hitWall = true
        }

        // ── 다리(고가) ──────────────────────────────────────────────
        val profile = if (Level.inBridgeLane(pos[1])) Level.bridgeProfile(pos[0]) else 0f
        val riding = profile > 0f && kotlin.math.abs(car.z - profile) < Z_SNAP
        var z = 0f
        if (riding) {
            z = profile
            // 난간: 다리 위에서는 차선 밖으로 벗어날 수 없다
            val top = Level.BRIDGE_Y - Level.BRIDGE_HALF_W + radius
            val bottom = Level.BRIDGE_Y + Level.BRIDGE_HALF_W - radius
            if (pos[1] < top) { pos[1] = top; hitWall = true }
            if (pos[1] > bottom) { pos[1] = bottom; hitWall = true }
        } else {
            // 지상 차에게 램프 덩어리는 벽이다 (다리 밑 상판 구간은 뚫려 있어 통과)
            for (w in Level.rampSolids) {
                if (pushCircleOut(pos, radius, w)) hitWall = true
            }
        }
        if (hitWall) newSpeed = speed * WALL_SLOWDOWN

        return CarState(pos[0], pos[1], heading, newSpeed, z)
    }

    /** 니어미스 판정 거리 = (두 반지름 합) * 이 배수. 충돌은 아니지만 스칠 정도. */
    const val NEAR_MISS_FACTOR = 1.9f

    /** 두 차량(원 근사)이 겹치는가. */
    fun carsCollide(ax: Float, ay: Float, aRadius: Float, bx: Float, by: Float, bRadius: Float): Boolean =
        hypot(ax - bx, ay - by) < aRadius + bRadius

    /**
     * 니어미스: 충돌은 아니지만 아슬아슬하게 스친 상태.
     * 충돌 반경 밖 ~ 충돌 반경 * [NEAR_MISS_FACTOR] 안.
     */
    fun isNearMiss(ax: Float, ay: Float, aRadius: Float, bx: Float, by: Float, bRadius: Float): Boolean {
        val d = hypot(ax - bx, ay - by)
        val touch = aRadius + bRadius
        return d >= touch && d < touch * NEAR_MISS_FACTOR
    }

    /** 최종 남은 시간으로 별 등급(1~3)을 매긴다. */
    fun starsFor(timeLeft: Float): Int = when {
        timeLeft >= 20f -> 3
        timeLeft >= 10f -> 2
        else -> 1
    }

    /** 아이템 획득 판정. */
    fun touchesPickup(car: CarState, carRadius: Float, pickup: Pickup): Boolean =
        hypot(car.x - pickup.x, car.y - pickup.y) < carRadius + PICKUP_RADIUS

    /** 목적지 도착 판정. */
    fun reachedGoal(car: CarState, goalX: Float, goalY: Float): Boolean =
        hypot(car.x - goalX, car.y - goalY) < GOAL_RADIUS
}
