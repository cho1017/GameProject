package com.example.myapplication.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.example.myapplication.model.GameEngine
import com.example.myapplication.model.GameUiState
import com.example.myapplication.model.Level
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 3인칭 추격 카메라 렌더러 (Mode-7 스타일 의사 3D).
 *
 * 카메라는 플레이어 뒤 [CAM_BACK], 높이 [CAM_H]에서 플레이어 진행 방향을 바라본다.
 * 모든 지형/차량은 바닥 평면(z=0) 위 다각형의 꼭짓점을 원근 투영해 그리고,
 * 건물은 바닥 사각형을 [BUILD_H]만큼 수직으로 돌출시킨다.
 * 깊이 정렬(painter's algorithm)로 가림을 처리한다.
 */
class ChaseRenderer {

    private companion object {
        const val CAM_BACK = 200f   // 플레이어 뒤 거리 (월드 단위)
        const val CAM_H = 130f      // 카메라 높이
        const val BUILD_H = 90f     // 건물 높이
        const val NEAR = 10f        // 근접 클리핑 평면
        const val FAR_FADE = 2600f  // 이 거리에서 완전히 어두워짐
        const val HORIZON_RATIO = 0.38f
    }

    private val skyPaint = Paint()
    private val groundPaint = Paint().apply { color = Color.rgb(45, 58, 64) }
    private val polyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(38, 50, 56)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val miniBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 20, 28, 32) }
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val miniRect = RectF()

    /** 깊이 정렬용 그리기 작업. */
    private class Job(val depth: Float, val draw: () -> Unit)

    // 카메라 상태 (draw마다 갱신)
    private var camX = 0f
    private var camY = 0f
    private var cosH = 1f
    private var sinH = 0f
    private var focal = 0f
    private var horizonY = 0f
    private var screenW = 0f
    private var screenH = 0f
    private var skyShader: LinearGradient? = null

    fun draw(canvas: Canvas, state: GameUiState, w: Int, h: Int) {
        screenW = w.toFloat()
        screenH = h.toFloat()
        focal = w * 1.0f
        horizonY = h * HORIZON_RATIO

        val p = state.player
        cosH = cos(p.heading)
        sinH = sin(p.heading)
        camX = p.x - cosH * CAM_BACK
        camY = p.y - sinH * CAM_BACK

        // 하늘 & 바닥
        if (skyShader == null) {
            skyShader = LinearGradient(
                0f, 0f, 0f, horizonY,
                Color.rgb(16, 24, 38), Color.rgb(58, 72, 86), Shader.TileMode.CLAMP,
            )
        }
        skyPaint.shader = skyShader
        canvas.drawRect(0f, 0f, screenW, horizonY, skyPaint)
        canvas.drawRect(0f, horizonY, screenW, screenH, groundPaint)

        val jobs = ArrayList<Job>(64)

        // 목적지 링 (바닥)
        addGroundCircle(jobs, canvas, state.goalX, state.goalY,
            GameEngine.GOAL_RADIUS * (1f + 0.08f * sin(state.elapsed * 4f)),
            fill = Color.argb(60, 102, 187, 106), stroke = Color.rgb(102, 187, 106))

        // 타이어 잔상 (바닥 점)
        for (t in state.trail) {
            addGroundCircle(jobs, canvas, t.x, t.y, state.playerRadius * 0.4f,
                fill = colorWithAlpha(state.playerColor, (t.alpha * 160).toInt()), stroke = null)
        }

        // 시간 아이템
        for (pk in state.pickups) {
            if (pk.collected) continue
            addPickup(jobs, canvas, pk.x, pk.y, state.elapsed)
        }

        // 건물
        for (wall in state.walls) addBuilding(jobs, canvas, wall)

        // 리플레이 차량
        for (g in state.ghosts) {
            if (!g.visible) continue
            addCar(jobs, canvas, g.pose.x, g.pose.y, g.pose.heading, g.radius, g.color)
        }

        // 플레이어
        addCar(jobs, canvas, p.x, p.y, p.heading, state.playerRadius, state.playerColor)

        // 먼 것부터 그린다
        jobs.sortByDescending { it.depth }
        for (j in jobs) j.draw()

        drawMinimap(canvas, state)
    }

    // ── 투영 ──────────────────────────────────────────────────────────

    /** 월드 → 카메라 좌표 (r: 우측 방향 오프셋, f: 전방 깊이). */
    private fun toCam(wx: Float, wy: Float): Pair<Float, Float> {
        val dx = wx - camX
        val dy = wy - camY
        return Pair(-dx * sinH + dy * cosH, dx * cosH + dy * sinH)
    }

    private fun projX(r: Float, f: Float) = screenW / 2f + r * focal / f

    /** 바닥(z=0) 또는 높이 z인 점의 화면 y. */
    private fun projY(f: Float, z: Float = 0f) = horizonY + (CAM_H - z) * focal / f

    /** 거리에 따라 어두워지는 색. */
    private fun shade(color: Int, f: Float, factor: Float = 1f): Int {
        val k = (1f - (f / FAR_FADE).coerceIn(0f, 0.75f)) * factor
        return Color.rgb(
            (Color.red(color) * k).toInt(),
            (Color.green(color) * k).toInt(),
            (Color.blue(color) * k).toInt(),
        )
    }

    private fun colorWithAlpha(color: Long, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        (color shr 16 and 0xFF).toInt(),
        (color shr 8 and 0xFF).toInt(),
        (color and 0xFF).toInt(),
    )

    // ── 바닥 다각형 ───────────────────────────────────────────────────

    /**
     * 바닥 위 다각형(월드 좌표 꼭짓점들)을 근접 평면에서 클리핑해 화면 Path로 만든다.
     * 전부 카메라 뒤면 null.
     */
    private fun groundPolyPath(pts: List<Pair<Float, Float>>): Pair<Path, Float>? {
        val cam = pts.map { toCam(it.first, it.second) }
        // Sutherland-Hodgman: f >= NEAR 평면으로 클리핑
        val clipped = ArrayList<Pair<Float, Float>>(cam.size + 2)
        for (i in cam.indices) {
            val a = cam[i]
            val b = cam[(i + 1) % cam.size]
            val aIn = a.second >= NEAR
            val bIn = b.second >= NEAR
            if (aIn) clipped.add(a)
            if (aIn != bIn) {
                val t = (NEAR - a.second) / (b.second - a.second)
                clipped.add(Pair(a.first + (b.first - a.first) * t, NEAR))
            }
        }
        if (clipped.size < 3) return null
        path.reset()
        var depth = 0f
        clipped.forEachIndexed { i, (r, f) ->
            depth += f
            val x = projX(r, f)
            val y = projY(f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Pair(path, depth / clipped.size)
    }

    private fun circlePts(cx: Float, cy: Float, radius: Float, n: Int = 10): List<Pair<Float, Float>> =
        (0 until n).map { i ->
            val a = i * (2.0 * Math.PI / n)
            Pair(cx + (radius * cos(a)).toFloat(), cy + (radius * sin(a)).toFloat())
        }

    private fun addGroundCircle(
        jobs: MutableList<Job>, canvas: Canvas,
        cx: Float, cy: Float, radius: Float, fill: Int, stroke: Int?,
    ) {
        val (poly, depth) = groundPolyPath(circlePts(cx, cy, radius)) ?: return
        val snapshot = Path(poly)
        jobs.add(Job(depth) {
            polyPaint.color = fill
            canvas.drawPath(snapshot, polyPaint)
            if (stroke != null) {
                strokePaint.color = stroke
                canvas.drawPath(snapshot, strokePaint)
            }
        })
    }

    // ── 차량: 바닥에 누운 사각형 + 앞유리 ──────────────────────────────

    private fun carCorners(
        x: Float, y: Float, heading: Float, halfL: Float, halfW: Float,
    ): List<Pair<Float, Float>> {
        val c = cos(heading)
        val s = sin(heading)
        fun pt(lx: Float, ly: Float) = Pair(x + lx * c - ly * s, y + lx * s + ly * c)
        return listOf(pt(halfL, -halfW), pt(halfL, halfW), pt(-halfL, halfW), pt(-halfL, -halfW))
    }

    private fun addCar(
        jobs: MutableList<Job>, canvas: Canvas,
        x: Float, y: Float, heading: Float, radius: Float, color: Long,
    ) {
        val body = groundPolyPath(carCorners(x, y, heading, radius * 1.25f, radius * 0.75f)) ?: return
        val bodySnapshot = Path(body.first)
        val depth = body.second
        // 앞유리 (진행 방향 앞쪽 절반)
        val c = cos(heading)
        val s = sin(heading)
        val glass = groundPolyPath(
            carCorners(x + c * radius * 0.5f, y + s * radius * 0.5f, heading, radius * 0.35f, radius * 0.55f)
        )
        val glassSnapshot = glass?.let { Path(it.first) }
        val f = depth
        jobs.add(Job(depth) {
            polyPaint.color = shade(colorWithAlpha(color, 255), f)
            canvas.drawPath(bodySnapshot, polyPaint)
            if (glassSnapshot != null) {
                polyPaint.color = Color.argb(170, 20, 30, 40)
                canvas.drawPath(glassSnapshot, polyPaint)
            }
        })
    }

    // ── 아이템 ────────────────────────────────────────────────────────

    private fun addPickup(jobs: MutableList<Job>, canvas: Canvas, x: Float, y: Float, elapsed: Float) {
        val bob = 1f + 0.1f * sin(elapsed * 5f)
        val result = groundPolyPath(circlePts(x, y, GameEngine.PICKUP_RADIUS * bob)) ?: return
        val snapshot = Path(result.first)
        val depth = result.second
        val (r, f) = toCam(x, y)
        jobs.add(Job(depth) {
            polyPaint.color = shade(Color.rgb(255, 213, 79), f)
            canvas.drawPath(snapshot, polyPaint)
            if (f >= NEAR) {
                val size = (focal / f * 28f).coerceIn(0f, 60f)
                if (size > 11f) {
                    textPaint.textSize = size
                    canvas.drawText("+3", projX(r, f), projY(f) - size * 0.3f, textPaint)
                }
            }
        })
    }

    // ── 건물: 4개 측면 + 지붕 ─────────────────────────────────────────

    private fun addBuilding(jobs: MutableList<Job>, canvas: Canvas, w: com.example.myapplication.model.Wall) {
        val corners = listOf(
            Pair(w.left, w.top), Pair(w.right, w.top),
            Pair(w.right, w.bottom), Pair(w.left, w.bottom),
        )
        val cam = corners.map { toCam(it.first, it.second) }

        // 측면 4개. 면마다 명암을 달리해 입체감을 준다.
        val faceShade = floatArrayOf(1.0f, 0.78f, 0.62f, 0.88f)
        for (i in 0 until 4) {
            val a = cam[i]
            val b = cam[(i + 1) % 4]
            addWallFace(jobs, canvas, a, b, faceShade[i])
        }

        // 지붕: 모든 꼭짓점이 앞에 있을 때만 (클리핑 단순화)
        if (cam.all { it.second >= NEAR }) {
            val roof = Path()
            var depth = 0f
            cam.forEachIndexed { i, (r, f) ->
                depth += f
                val x = projX(r, f)
                val y = projY(f, BUILD_H)
                if (i == 0) roof.moveTo(x, y) else roof.lineTo(x, y)
            }
            roof.close()
            val f = depth / 4f
            // 지붕은 측면보다 살짝 가까운 깊이로 취급해 위에 그려지게 한다
            jobs.add(Job(f - 1f) {
                polyPaint.color = shade(Color.rgb(96, 125, 139), f)
                canvas.drawPath(roof, polyPaint)
            })
        }
    }

    /** 두 바닥 꼭짓점(카메라 좌표) 사이의 수직 벽면. */
    private fun addWallFace(
        jobs: MutableList<Job>, canvas: Canvas,
        a: Pair<Float, Float>, b: Pair<Float, Float>, brightness: Float,
    ) {
        var (r1, f1) = a
        var (r2, f2) = b
        val aIn = f1 >= NEAR
        val bIn = f2 >= NEAR
        if (!aIn && !bIn) return
        if (aIn != bIn) {
            val t = (NEAR - f1) / (f2 - f1)
            val rc = r1 + (r2 - r1) * t
            if (!aIn) { r1 = rc; f1 = NEAR } else { r2 = rc; f2 = NEAR }
        }
        val face = Path().apply {
            moveTo(projX(r1, f1), projY(f1))
            lineTo(projX(r2, f2), projY(f2))
            lineTo(projX(r2, f2), projY(f2, BUILD_H))
            lineTo(projX(r1, f1), projY(f1, BUILD_H))
            close()
        }
        val depth = (f1 + f2) / 2f
        jobs.add(Job(depth) {
            polyPaint.color = shade(Color.rgb(69, 90, 100), depth, brightness)
            canvas.drawPath(face, polyPaint)
        })
    }

    // ── 미니맵 ────────────────────────────────────────────────────────

    private fun drawMinimap(canvas: Canvas, state: GameUiState) {
        val mw = screenW * 0.26f
        val mh = mw * (Level.WORLD_H / Level.WORLD_W)
        val mx = screenW - mw - 16f
        val my = 170f
        val s = mw / Level.WORLD_W

        miniRect.set(mx, my, mx + mw, my + mh)
        canvas.drawRoundRect(miniRect, 10f, 10f, miniBgPaint)

        miniPaint.style = Paint.Style.FILL
        miniPaint.color = Color.argb(200, 96, 125, 139)
        for (w in state.walls) {
            canvas.drawRect(mx + w.left * s, my + w.top * s, mx + w.right * s, my + w.bottom * s, miniPaint)
        }
        // 목적지
        miniPaint.color = Color.rgb(102, 187, 106)
        canvas.drawCircle(mx + state.goalX * s, my + state.goalY * s, max(5f, GameEngine.GOAL_RADIUS * s), miniPaint)
        // 아이템
        miniPaint.color = Color.rgb(255, 213, 79)
        for (p in state.pickups) {
            if (!p.collected) canvas.drawCircle(mx + p.x * s, my + p.y * s, 4f, miniPaint)
        }
        // 리플레이 차량
        for (g in state.ghosts) {
            if (!g.visible) continue
            miniPaint.color = colorWithAlpha(g.color, 255)
            canvas.drawCircle(mx + g.pose.x * s, my + g.pose.y * s, 5f, miniPaint)
        }
        // 플레이어: 진행 방향 삼각형
        val p = state.player
        val px = mx + p.x * s
        val py = my + p.y * s
        val c = cos(p.heading)
        val sn = sin(p.heading)
        path.reset()
        path.moveTo(px + c * 10f, py + sn * 10f)
        path.lineTo(px - c * 6f - sn * 5f, py - sn * 6f + c * 5f)
        path.lineTo(px - c * 6f + sn * 5f, py - sn * 6f - c * 5f)
        path.close()
        miniPaint.color = Color.WHITE
        canvas.drawPath(path, miniPaint)
    }
}
