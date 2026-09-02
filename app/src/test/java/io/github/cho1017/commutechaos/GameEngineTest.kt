package io.github.cho1017.commutechaos

import io.github.cho1017.commutechaos.model.CarState
import io.github.cho1017.commutechaos.model.GameEngine
import io.github.cho1017.commutechaos.model.Level
import io.github.cho1017.commutechaos.model.Pickup
import io.github.cho1017.commutechaos.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private val r = 24f // 테스트용 기본 차량 반경

    private fun step(car: CarState, steer: Float, dt: Float, walls: List<Wall> = emptyList()) =
        GameEngine.step(car, steer, targetSpeed = 300f, turnRate = 3f, radius = r, dt = dt, walls = walls)

    @Test
    fun `차량은 진행 방향으로 전진한다`() {
        var car = CarState(x = 500f, y = 500f, heading = 0f, speed = 100f)
        car = GameEngine.step(car, 0f, targetSpeed = 100f, turnRate = 3f, radius = r, dt = 1f, walls = emptyList())
        assertTrue("x가 증가해야 함", car.x > 500f)
        assertEquals(500f, car.y, 1f)
    }

    @Test
    fun `조향하면 heading이 바뀐다`() {
        val car = CarState(500f, 500f, heading = 0f, speed = 100f)
        val left = step(car, steer = -1f, dt = 0.1f)
        val right = step(car, steer = 1f, dt = 0.1f)
        assertTrue(left.heading < 0f)
        assertTrue(right.heading > 0f)
    }

    @Test
    fun `turnRate가 클수록 더 빨리 돈다`() {
        val car = CarState(500f, 500f, heading = 0f, speed = 100f)
        val slow = GameEngine.step(car, 1f, 100f, turnRate = 2f, radius = r, dt = 0.1f, walls = emptyList())
        val fast = GameEngine.step(car, 1f, 100f, turnRate = 4f, radius = r, dt = 0.1f, walls = emptyList())
        assertTrue(fast.heading > slow.heading)
    }

    @Test
    fun `벽을 통과할 수 없다`() {
        val wall = Wall(600f, 0f, 700f, 1000f)
        var car = CarState(550f, 500f, heading = 0f, speed = 300f)
        repeat(120) {
            car = step(car, 0f, 0.016f, listOf(wall))
        }
        assertTrue("벽 왼쪽에 막혀야 함", car.x <= 600f - r + 0.5f)
    }

    @Test
    fun `월드 경계를 벗어날 수 없다`() {
        var car = CarState(50f, 50f, heading = (Math.PI).toFloat(), speed = 300f) // 왼쪽으로 질주
        repeat(120) {
            car = step(car, 0f, 0.016f)
        }
        assertTrue(car.x >= r)
    }

    @Test
    fun `충돌 판정은 두 반지름 합 기준이다`() {
        assertTrue(GameEngine.carsCollide(0f, 0f, 24f, 45f, 0f, 22f))   // 46 미만
        assertFalse(GameEngine.carsCollide(0f, 0f, 24f, 47f, 0f, 22f))  // 46 초과
    }

    @Test
    fun `큰 차는 같은 거리에서도 충돌한다`() {
        val dist = 50f
        assertFalse(GameEngine.carsCollide(0f, 0f, 22f, dist, 0f, 22f)) // 44 < 50: 안 부딪힘
        assertTrue(GameEngine.carsCollide(0f, 0f, 32f, dist, 0f, 22f))  // 54 > 50: 트럭은 부딪힘
    }

    @Test
    fun `목적지 도착 판정`() {
        val car = CarState(100f, 100f, 0f, 0f)
        assertTrue(GameEngine.reachedGoal(car, 100f + GameEngine.GOAL_RADIUS - 1f, 100f))
        assertFalse(GameEngine.reachedGoal(car, 100f + GameEngine.GOAL_RADIUS + 1f, 100f))
    }

    @Test
    fun `아이템 획득 판정`() {
        val car = CarState(100f, 100f, 0f, 0f)
        val near = Pickup(100f + r + GameEngine.PICKUP_RADIUS - 1f, 100f)
        val far = Pickup(100f + r + GameEngine.PICKUP_RADIUS + 1f, 100f)
        assertTrue(GameEngine.touchesPickup(car, r, near))
        assertFalse(GameEngine.touchesPickup(car, r, far))
    }

    @Test
    fun `니어미스는 충돌 밖 근접 밴드에서만 참이다`() {
        val touch = r + r // 48
        // 겹침(충돌) → 니어미스 아님
        assertFalse(GameEngine.isNearMiss(0f, 0f, r, touch - 2f, 0f, r))
        // 밴드 안 → 니어미스
        assertTrue(GameEngine.isNearMiss(0f, 0f, r, touch + 5f, 0f, r))
        // 밴드 밖(너무 멈) → 니어미스 아님
        assertFalse(GameEngine.isNearMiss(0f, 0f, r, touch * GameEngine.NEAR_MISS_FACTOR + 5f, 0f, r))
    }

    @Test
    fun `별 등급은 남은 시간 구간을 따른다`() {
        assertEquals(3, GameEngine.starsFor(20f))
        assertEquals(3, GameEngine.starsFor(25f))
        assertEquals(2, GameEngine.starsFor(10f))
        assertEquals(2, GameEngine.starsFor(19.9f))
        assertEquals(1, GameEngine.starsFor(9.9f))
        assertEquals(1, GameEngine.starsFor(0.1f))
    }

    // ── 고가도로(다리) ────────────────────────────────────────────────

    @Test
    fun `램프로 진입한 차는 다리 위로 올라갔다 내려온다`() {
        var car = CarState(Level.BRIDGE_RAMP_W - 40f, Level.BRIDGE_Y, heading = 0f, speed = 300f)
        var maxZ = 0f
        repeat(160) {
            car = GameEngine.step(car, 0f, 300f, 3f, 22f, 0.016f, Level.walls)
            maxZ = maxOf(maxZ, car.z)
        }
        assertEquals("상판 높이까지 올라가야 함", Level.BRIDGE_H, maxZ, 1f)
        assertEquals("동쪽 램프를 내려와 지상으로 복귀해야 함", 0f, car.z, 0.01f)
        assertTrue("다리를 지나 동쪽으로 진행해야 함", car.x > Level.BRIDGE_RAMP_E)
    }

    @Test
    fun `다리 밑을 지나는 차는 지상에 머문다`() {
        // 세로 도로 2(x=920)를 따라 남쪽으로: 상판 밑을 통과한다
        var car = CarState(920f, 960f, heading = (Math.PI / 2).toFloat(), speed = 200f)
        repeat(90) {
            car = GameEngine.step(car, 0f, 200f, 3f, 22f, 0.016f, Level.walls)
            assertEquals("다리 밑에서는 항상 지상", 0f, car.z, 0.01f)
        }
        assertTrue("다리 구간을 통과해야 함", car.y > Level.BRIDGE_Y + Level.BRIDGE_HALF_W)
    }

    @Test
    fun `지상 차는 램프 덩어리를 뚫을 수 없다`() {
        // 상판 밑(x=1000)에서 동쪽으로: 동쪽 램프의 서쪽 벽에 막혀야 한다
        var car = CarState(1000f, Level.BRIDGE_Y, heading = 0f, speed = 200f)
        repeat(120) {
            car = GameEngine.step(car, 0f, 200f, 3f, r, 0.016f, Level.walls)
        }
        assertTrue("램프 벽에 막혀야 함", car.x <= Level.BRIDGE_DECK_E - r + 0.5f)
        assertEquals(0f, car.z, 0.01f)
    }
}
