package com.example.myapplication.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 순수 게임 물리/규칙. Android 의존성이 없어 JVM 단위 테스트가 가능하다.
 */
object GameEngine {

    const val CAR_RADIUS = 24f
    const val GOAL_RADIUS = 70f

    /** 최대 조향 각속도 (rad/s). */
    const val TURN_RATE = 3.0f

    /** 벽에 부딪히면 속도가 이 비율로 줄고, 이후 다시 가속한다. */
    private const val WALL_SLOWDOWN = 0.35f
    private const val ACCEL = 180f // unit/s^2

    /**
     * 차량을 dt초만큼 전진시킨다.
     * @param steer -1(좌회전)..1(우회전)
     * @param targetSpeed 이 차량의 순항 속도
     */
    fun step(
        car: CarState,
        steer: Float,
        targetSpeed: Float,
        dt: Float,
        walls: List<Wall>,
    ): CarState {
        val heading = car.heading + steer.coerceIn(-1f, 1f) * TURN_RATE * dt
        val speed = (car.speed + ACCEL * dt).coerceAtMost(targetSpeed)
        var x = car.x + cos(heading) * speed * dt
        var y = car.y + sin(heading) * speed * dt
        var newSpeed = speed

        // 월드 경계
        var hitWall = false
        if (x < CAR_RADIUS) { x = CAR_RADIUS; hitWall = true }
        if (x > Level.WORLD_W - CAR_RADIUS) { x = Level.WORLD_W - CAR_RADIUS; hitWall = true }
        if (y < CAR_RADIUS) { y = CAR_RADIUS; hitWall = true }
        if (y > Level.WORLD_H - CAR_RADIUS) { y = Level.WORLD_H - CAR_RADIUS; hitWall = true }

        // 건물 블록: 원-사각형 충돌, 가장 얕은 축으로 밀어낸다.
        for (w in walls) {
            val cx = x.coerceIn(w.left, w.right)
            val cy = y.coerceIn(w.top, w.bottom)
            val dx = x - cx
            val dy = y - cy
            val dist = hypot(dx, dy)
            if (dist < CAR_RADIUS) {
                hitWall = true
                if (dist > 1e-4f) {
                    val push = CAR_RADIUS - dist
                    x += dx / dist * push
                    y += dy / dist * push
                } else {
                    // 중심이 사각형 내부: 가장 가까운 변으로 탈출
                    val toLeft = x - w.left
                    val toRight = w.right - x
                    val toTop = y - w.top
                    val toBottom = w.bottom - y
                    when (minOf(toLeft, toRight, toTop, toBottom)) {
                        toLeft -> x = w.left - CAR_RADIUS
                        toRight -> x = w.right + CAR_RADIUS
                        toTop -> y = w.top - CAR_RADIUS
                        else -> y = w.bottom + CAR_RADIUS
                    }
                }
            }
        }
        if (hitWall) newSpeed = speed * WALL_SLOWDOWN

        return CarState(x, y, heading, newSpeed)
    }

    /** 두 차량(원 근사)이 겹치는가. */
    fun carsCollide(ax: Float, ay: Float, bx: Float, by: Float): Boolean =
        hypot(ax - bx, ay - by) < CAR_RADIUS * 2f

    /** 목적지 도착 판정. */
    fun reachedGoal(car: CarState, goalX: Float, goalY: Float): Boolean =
        hypot(car.x - goalX, car.y - goalY) < GOAL_RADIUS
}
