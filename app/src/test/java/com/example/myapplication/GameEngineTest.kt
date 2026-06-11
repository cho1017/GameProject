package com.example.myapplication

import com.example.myapplication.model.CarState
import com.example.myapplication.model.GameEngine
import com.example.myapplication.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun `차량은 진행 방향으로 전진한다`() {
        var car = CarState(x = 500f, y = 500f, heading = 0f, speed = 100f)
        car = GameEngine.step(car, steer = 0f, targetSpeed = 100f, dt = 1f, walls = emptyList())
        assertTrue("x가 증가해야 함", car.x > 500f)
        assertEquals(500f, car.y, 1f)
    }

    @Test
    fun `조향하면 heading이 바뀐다`() {
        val car = CarState(500f, 500f, heading = 0f, speed = 100f)
        val left = GameEngine.step(car, steer = -1f, targetSpeed = 100f, dt = 0.1f, walls = emptyList())
        val right = GameEngine.step(car, steer = 1f, targetSpeed = 100f, dt = 0.1f, walls = emptyList())
        assertTrue(left.heading < 0f)
        assertTrue(right.heading > 0f)
    }

    @Test
    fun `벽을 통과할 수 없다`() {
        val wall = Wall(600f, 0f, 700f, 1000f)
        var car = CarState(550f, 500f, heading = 0f, speed = 300f)
        repeat(120) {
            car = GameEngine.step(car, 0f, 300f, 0.016f, listOf(wall))
        }
        assertTrue("벽 왼쪽에 막혀야 함", car.x <= 600f - GameEngine.CAR_RADIUS + 0.5f)
    }

    @Test
    fun `월드 경계를 벗어날 수 없다`() {
        var car = CarState(50f, 50f, heading = (Math.PI).toFloat(), speed = 300f) // 왼쪽으로 질주
        repeat(120) {
            car = GameEngine.step(car, 0f, 300f, 0.016f, emptyList())
        }
        assertTrue(car.x >= GameEngine.CAR_RADIUS)
    }

    @Test
    fun `충돌 판정은 반지름 합 기준이다`() {
        val r2 = GameEngine.CAR_RADIUS * 2
        assertTrue(GameEngine.carsCollide(0f, 0f, r2 - 1f, 0f))
        assertFalse(GameEngine.carsCollide(0f, 0f, r2 + 1f, 0f))
    }

    @Test
    fun `목적지 도착 판정`() {
        val car = CarState(100f, 100f, 0f, 0f)
        assertTrue(GameEngine.reachedGoal(car, 100f + GameEngine.GOAL_RADIUS - 1f, 100f))
        assertFalse(GameEngine.reachedGoal(car, 100f + GameEngine.GOAL_RADIUS + 1f, 100f))
    }
}
