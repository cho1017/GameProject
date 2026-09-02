package io.github.cho1017.commutechaos.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import io.github.cho1017.commutechaos.model.GameEngine
import io.github.cho1017.commutechaos.model.GameUiState
import io.github.cho1017.commutechaos.model.Level
import io.github.cho1017.commutechaos.model.Wall
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
        const val CAR_H = 32f       // 차량 높이 (납작하면 멀리서 안 보인다)
        const val BORDER_H = 45f    // 외곽 벽 높이 (건물보다 낮게)
        const val MIRROR_H = 70f    // 반사경 기둥 높이
        const val GARDEN_H = 22f    // 화단 높이
        const val NEAR = 10f        // 근접 클리핑 평면
        const val FAR_FADE = 2600f  // 이 거리에서 아침 안개에 완전히 잠김
        const val HORIZON_RATIO = 0.38f

        // 아침 안개(대기 원근)색. 멀수록 이 색에 가까워진다.
        const val HAZE_R = 216
        const val HAZE_G = 198
        const val HAZE_B = 178
    }

    private val skyPaint = Paint()
    private val groundPaint = Paint().apply { color = Color.rgb(92, 100, 106) }
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
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val miniBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 20, 28, 32) }
    private val miniPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val miniRect = RectF()

    /** 건물마다 다른 높이를 주기 위한 후보값들 (BUILD_H 기준 비율). 가장 낮은 건물도 창문 한 줄은 들어가는 높이. */
    private val buildingHeights = floatArrayOf(
        BUILD_H, BUILD_H * 1.3f, BUILD_H * 0.8f, BUILD_H * 1.6f, BUILD_H * 1.1f,
    )

    /** 건물 재질 팔레트: 콘크리트/벽돌/슬레이트/타일/청록 패널. 몸체-지붕 색이 한 쌍으로 대응. */
    private val buildingBodyPalette = intArrayOf(
        Color.rgb(96, 125, 139),
        Color.rgb(141, 110, 99),
        Color.rgb(120, 144, 156),
        Color.rgb(161, 136, 127),
        Color.rgb(84, 122, 122),
    )
    private val buildingRoofPalette = intArrayOf(
        Color.rgb(69, 90, 100),
        Color.rgb(78, 52, 46),
        Color.rgb(69, 90, 96),
        Color.rgb(109, 87, 78),
        Color.rgb(51, 77, 77),
    )

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

        // 하늘 & 바닥 (아침 출근 시간: 지평선 쪽이 해 뜨는 주황빛)
        if (skyShader == null) {
            skyShader = LinearGradient(
                0f, 0f, 0f, horizonY,
                Color.rgb(110, 156, 205), Color.rgb(255, 205, 148), Shader.TileMode.CLAMP,
            )
        }
        skyPaint.shader = skyShader
        canvas.drawRect(0f, 0f, screenW, horizonY, skyPaint)
        drawSky(canvas)
        canvas.drawRect(0f, horizonY, screenW, screenH, groundPaint)

        // 도로 표시: 바닥에 붙어 있으니 정렬 없이 바닥 직후에 그린다
        drawEdgeLines(canvas)
        drawLaneDashes(canvas)
        drawCrosswalks(canvas)

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

        // 코너 반사경
        for (m in state.mirrors) addMirror(jobs, canvas, m)

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

    /** 아침 하늘: 떠오르는 해와 옅은 구름. 카메라 요에 따라 살짝 흐른다. */
    private fun drawSky(canvas: Canvas) {
        val yaw = kotlin.math.atan2(sinH, cosH)
        val drift = yaw / (2f * Math.PI.toFloat()) * screenW * 2f

        // 해: 지평선 가까이 낮게 떠 있는 밝은 원 + 글로우
        val sunX = ((screenW * 0.30f + drift) % (screenW + 300f) + screenW + 300f) % (screenW + 300f) - 150f
        val sunY = horizonY * 0.62f
        starPaint.color = Color.argb(60, 255, 205, 120)
        canvas.drawCircle(sunX, sunY, 96f, starPaint)
        starPaint.color = Color.argb(110, 255, 224, 158)
        canvas.drawCircle(sunX, sunY, 60f, starPaint)
        starPaint.color = Color.rgb(255, 246, 218)
        canvas.drawCircle(sunX, sunY, 40f, starPaint)

        // 구름: 결정적 위치의 납작한 타원 무리 (매 프레임 동일, 요에 따라 흐른다)
        starPaint.color = Color.argb(88, 255, 255, 255)
        for (i in 0 until 6) {
            val cx = ((i * 373f + 90f + drift * 0.6f) % (screenW + 260f) + screenW + 260f) % (screenW + 260f) - 130f
            val cy = horizonY * (0.18f + (i % 3) * 0.16f)
            val w = 90f + (i % 4) * 34f
            val h = 16f + (i % 3) * 5f
            canvas.drawOval(cx - w, cy - h, cx + w, cy + h, starPaint)
            canvas.drawOval(cx - w * 0.45f, cy - h * 1.7f, cx + w * 0.55f, cy + h * 0.3f, starPaint)
        }
    }

    /** 이 지점이 카메라 뒤이거나 안개 너머면 그릴 필요가 없다 (도로 표시용 저렴한 컬링). */
    private fun cullFar(wx: Float, wy: Float): Boolean {
        val (_, f) = toCam(wx, wy)
        return f > FAR_FADE || f < -300f
    }

    /** 도로 중앙선을 짧은 바닥 사각형(대시)으로 투영해 그린다. */
    private fun drawLaneDashes(canvas: Canvas) {
        val dashLen = 34f
        val gap = 30f
        val halfW = 3.5f
        lanePaint.color = Color.argb(150, 244, 196, 68)
        for (seg in Level.roadLines) {
            val dx = seg.x2 - seg.x1
            val dy = seg.y2 - seg.y1
            val len = kotlin.math.hypot(dx, dy)
            val ux = dx / len
            val uy = dy / len
            // 수직 방향 (대시 폭)
            val px = -uy * halfW
            val py = ux * halfW
            var d = 0f
            while (d + dashLen <= len) {
                val x1 = seg.x1 + ux * d
                val y1 = seg.y1 + uy * d
                if (!cullFar(x1, y1)) {
                    val x2 = seg.x1 + ux * (d + dashLen)
                    val y2 = seg.y1 + uy * (d + dashLen)
                    val result = groundPolyPath(
                        listOf(
                            Pair(x1 + px, y1 + py), Pair(x2 + px, y2 + py),
                            Pair(x2 - px, y2 - py), Pair(x1 - px, y1 - py),
                        )
                    )
                    if (result != null) canvas.drawPath(result.first, lanePaint)
                }
                d += dashLen + gap
            }
        }
    }

    /** 도로 가장자리 흰 실선: 세그먼트 하나를 통째로 얇은 바닥 사각형으로 그린다. */
    private fun drawEdgeLines(canvas: Canvas) {
        val halfW = 3f
        lanePaint.color = Color.argb(150, 245, 245, 245)
        for (seg in Level.edgeLines) {
            // 양 끝과 중점 모두 안개 너머면 통째로 생략
            val mx = (seg.x1 + seg.x2) / 2f
            val my = (seg.y1 + seg.y2) / 2f
            if (cullFar(seg.x1, seg.y1) && cullFar(seg.x2, seg.y2) && cullFar(mx, my)) continue
            val dx = seg.x2 - seg.x1
            val dy = seg.y2 - seg.y1
            val len = kotlin.math.hypot(dx, dy)
            if (len < 1f) continue
            val px = -dy / len * halfW
            val py = dx / len * halfW
            val result = groundPolyPath(
                listOf(
                    Pair(seg.x1 + px, seg.y1 + py), Pair(seg.x2 + px, seg.y2 + py),
                    Pair(seg.x2 - px, seg.y2 - py), Pair(seg.x1 - px, seg.y1 - py),
                )
            )
            if (result != null) canvas.drawPath(result.first, lanePaint)
        }
    }

    /** 횡단보도: 얼룩말 줄무늬를 바닥 사각형들로 그린다. */
    private fun drawCrosswalks(canvas: Canvas) {
        val stripe = 12f
        val gap = 10f
        lanePaint.color = Color.argb(185, 238, 240, 242)
        for (c in Level.crosswalks) {
            val cx = (c.left + c.right) / 2f
            val cy = (c.top + c.bottom) / 2f
            if (cullFar(cx, cy)) continue
            if (c.stripesAlongX) {
                var x = c.left
                while (x + stripe <= c.right) {
                    val result = groundPolyPath(
                        listOf(
                            Pair(x, c.top), Pair(x + stripe, c.top),
                            Pair(x + stripe, c.bottom), Pair(x, c.bottom),
                        )
                    )
                    if (result != null) canvas.drawPath(result.first, lanePaint)
                    x += stripe + gap
                }
            } else {
                var y = c.top
                while (y + stripe <= c.bottom) {
                    val result = groundPolyPath(
                        listOf(
                            Pair(c.left, y), Pair(c.right, y),
                            Pair(c.right, y + stripe), Pair(c.left, y + stripe),
                        )
                    )
                    if (result != null) canvas.drawPath(result.first, lanePaint)
                    y += stripe + gap
                }
            }
        }
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

    /**
     * 거리에 따라 아침 안개색으로 옅어지는 색 (대기 원근).
     * [factor]는 면 밝기 배율(가짜 조명)로, 안개 혼합 후에 곱한다.
     */
    private fun shade(color: Int, f: Float, factor: Float = 1f): Int {
        val t = (f / FAR_FADE).coerceIn(0f, 0.65f)
        fun ch(c: Int, haze: Int) = ((c + (haze - c) * t) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(
            ch(Color.red(color), HAZE_R),
            ch(Color.green(color), HAZE_G),
            ch(Color.blue(color), HAZE_B),
        )
    }

    private fun colorWithAlpha(color: Long, alpha: Int): Int = Color.argb(
        alpha.coerceIn(0, 255),
        (color shr 16 and 0xFF).toInt(),
        (color shr 8 and 0xFF).toInt(),
        (color and 0xFF).toInt(),
    )

    /** [shade]와 같지만 원래 색의 alpha(반투명)를 유지한다. 창문처럼 비치는 소재용. */
    private fun shadeAlpha(color: Int, f: Float): Int {
        val t = (f / FAR_FADE).coerceIn(0f, 0.65f)
        fun ch(c: Int, haze: Int) = (c + (haze - c) * t).toInt().coerceIn(0, 255)
        return Color.argb(
            Color.alpha(color),
            ch(Color.red(color), HAZE_R),
            ch(Color.green(color), HAZE_G),
            ch(Color.blue(color), HAZE_B),
        )
    }

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
        val rgb = colorWithAlpha(color, 255)
        // 차체: 높이 있는 박스. 지붕에 앞유리를 얹는다.
        val c = cos(heading)
        val s = sin(heading)
        val glassCorners = carCorners(
            x + c * radius * 0.4f, y + s * radius * 0.4f, heading, radius * 0.4f, radius * 0.6f,
        )
        addPrism(
            jobs, canvas,
            corners = carCorners(x, y, heading, radius * 1.25f, radius * 0.75f),
            heightZ = CAR_H,
            color = rgb,
            topBrightness = 1.1f,
            topExtra = { topDepth ->
                val glass = polyPathAt(glassCorners, CAR_H + 0.5f)
                if (glass != null) {
                    polyPaint.color = Color.argb(190, 20, 30, 40)
                    canvas.drawPath(glass, polyPaint)
                }
            },
        )
    }

    /** 월드 꼭짓점들을 높이 z 평면에 투영한 Path. 일부가 카메라 뒤면 null (간략화). */
    private fun polyPathAt(pts: List<Pair<Float, Float>>, z: Float): Path? {
        val cam = pts.map { toCam(it.first, it.second) }
        if (cam.any { it.second < NEAR }) return null
        val p = Path()
        cam.forEachIndexed { i, (r, f) ->
            val x = projX(r, f)
            val y = projY(f, z)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    /**
     * 바닥 다각형을 [baseZ]에서 heightZ까지 돌출시킨 기둥. 건물·차량·옥상 구조물 공용.
     * 측면은 어둡게, 윗면은 [topBrightness] 배율로 그린다.
     */
    private fun addPrism(
        jobs: MutableList<Job>, canvas: Canvas,
        corners: List<Pair<Float, Float>>, heightZ: Float, color: Int,
        topBrightness: Float = 1.0f,
        topExtra: ((Float) -> Unit)? = null,
        baseZ: Float = 0f,
    ) {
        val cam = corners.map { toCam(it.first, it.second) }
        val faceShade = floatArrayOf(0.85f, 0.62f, 0.5f, 0.72f)
        for (i in corners.indices) {
            addWallFace(jobs, canvas, cam[i], cam[(i + 1) % corners.size], heightZ, color, faceShade[i % 4], baseZ)
        }
        // 윗면: 모든 꼭짓점이 앞에 있을 때만 (클리핑 단순화)
        if (cam.all { it.second >= NEAR }) {
            val top = Path()
            var depth = 0f
            cam.forEachIndexed { i, (r, f) ->
                depth += f
                val x = projX(r, f)
                val y = projY(f, heightZ)
                if (i == 0) top.moveTo(x, y) else top.lineTo(x, y)
            }
            top.close()
            val f = depth / cam.size
            // 윗면은 측면보다 살짝 가까운 깊이로 취급해 위에 그려지게 한다
            jobs.add(Job(f - 1f) {
                polyPaint.color = shade(color, f, topBrightness.coerceAtMost(1f))
                canvas.drawPath(top, polyPaint)
                topExtra?.invoke(f)
            })
        }
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

    // ── 건물: 4개 측면 + 지붕 + 창문 + 옥상 구조물 ──────────────────────

    private fun rectCorners(w: Wall): List<Pair<Float, Float>> = listOf(
        Pair(w.left, w.top), Pair(w.right, w.top),
        Pair(w.right, w.bottom), Pair(w.left, w.bottom),
    )

    /** 사각형 꼭짓점 목록을 안쪽으로 [margin]만큼 줄인다 (파라펫/옥상 트림용). */
    private fun insetRect(corners: List<Pair<Float, Float>>, margin: Float): List<Pair<Float, Float>> {
        val left = corners.minOf { it.first } + margin
        val right = corners.maxOf { it.first } - margin
        val top = corners.minOf { it.second } + margin
        val bottom = corners.maxOf { it.second } - margin
        return listOf(Pair(left, top), Pair(right, top), Pair(right, bottom), Pair(left, bottom))
    }

    /** 건물 위치 기반 결정적 시드 (높이/색/옥상 구조물 배치를 프레임마다 동일하게 고정). */
    private fun buildingSeed(w: Wall): Int {
        val sx = (w.left / 10f).toInt()
        val sy = (w.top / 10f).toInt()
        return (sx * 73 + sy * 131) and 0x7fffffff
    }

    private fun addBuilding(jobs: MutableList<Job>, canvas: Canvas, w: Wall) {
        // 도로 위 화단: 낮은 녹지 + 수풀
        if (w in Level.gardens) {
            addPrism(
                jobs, canvas,
                corners = rectCorners(w),
                heightZ = GARDEN_H,
                color = Color.rgb(67, 132, 78),
            )
            // 수풀: 화단 윗면 위 작은 원
            val cx = (w.left + w.right) / 2f
            var by = w.top + 30f
            var i = 0
            while (by < w.bottom - 20f) {
                val bx = cx + if (i % 2 == 0) -7f else 7f
                val bush = polyPathAt(circlePts(bx, by, 15f, 8), GARDEN_H + 1f)
                if (bush != null) {
                    val (_, f) = toCam(bx, by)
                    val bushColor = if (i % 2 == 0) Color.rgb(56, 118, 68) else Color.rgb(46, 100, 58)
                    val snapshot = Path(bush)
                    jobs.add(Job(f - 2f) {
                        polyPaint.color = shade(bushColor, f)
                        canvas.drawPath(snapshot, polyPaint)
                    })
                }
                by += 42f
                i++
            }
            return
        }

        val corners = rectCorners(w)
        // 월드 가장자리에 붙은 벽은 낮고 밋밋한 외곽 벽으로 그린다 (건물 아님)
        val isBorder = w.left <= 0f || w.top <= 0f || w.right >= Level.WORLD_W || w.bottom >= Level.WORLD_H
        if (isBorder) {
            addPrism(jobs, canvas, corners = corners, heightZ = BORDER_H, color = Color.rgb(84, 110, 122))
            return
        }

        // 건물마다 높이/재질을 결정적으로 다르게 줘서 스카이라인에 변화를 준다
        val seed = buildingSeed(w)
        val height = buildingHeights[seed % buildingHeights.size]
        val paletteIdx = (seed / 7) % buildingBodyPalette.size
        val bodyColor = buildingBodyPalette[paletteIdx]
        val roofColor = buildingRoofPalette[paletteIdx]

        addPrism(
            jobs, canvas,
            corners = corners,
            heightZ = height,
            color = bodyColor,
            topExtra = { _ ->
                // 지붕 테두리(파라펫)를 살짝 어둡게 둘러 지붕이 납작해 보이지 않게 한다
                val trim = polyPathAt(insetRect(corners, 14f), height + 0.4f)
                if (trim != null) {
                    polyPaint.color = roofColor
                    canvas.drawPath(trim, polyPaint)
                }
            },
        )
        addBuildingWindows(jobs, canvas, corners, height, seed)
        addRooftopUnit(jobs, canvas, w, height, seed)
    }

    /** 건물 네 벽면에 격자로 창문을 그린다. 일부는 결정적으로 불이 켜져 있다. */
    private fun addBuildingWindows(
        jobs: MutableList<Job>, canvas: Canvas,
        corners: List<Pair<Float, Float>>, height: Float, seed: Int,
    ) {
        val winSize = 24f
        val winStep = 62f
        val marginEdge = 28f
        val winH = 26f
        val rowStep = 46f
        val marginTop = 22f
        val marginBottom = 20f

        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val dx = b.first - a.first
            val dy = b.second - a.second
            val len = hypot(dx, dy)
            if (len < marginEdge * 2 + winSize) continue
            val ux = dx / len
            val uy = dy / len

            // 창문의 깊이는 자신이 붙어 있는 벽면(잡)의 깊이에 고정한다. 창문 위치 기준의
            // 깊이를 쓰면, 카메라에서 멀어지는 측면 벽에서 벽 평균보다 깊은 창문이 벽보다
            // 먼저 그려져 벽에 가려지는 버그가 생긴다. (addWallFace의 근접 클리핑과 같은
            // 방식으로 NEAR 밑을 잘라 평균 깊이를 계산한다.)
            val aCam = toCam(a.first, a.second)
            val bCam = toCam(b.first, b.second)
            if (aCam.second < NEAR && bCam.second < NEAR) continue
            val faceDepth = (max(aCam.second, NEAR) + max(bCam.second, NEAR)) / 2f

            var z = height - marginTop - winH
            var row = 0
            while (z > marginBottom) {
                var t = marginEdge
                var col = 0
                while (t + winSize <= len - marginEdge) {
                    // 아침이라 대부분은 하늘이 비치는 유리, 드문드문 불 켜진 사무실
                    val lit = (row * 7 + col * 13 + i * 5 + seed) % 7 == 0
                    addWindowQuad(
                        jobs, canvas,
                        x1 = a.first + ux * t, y1 = a.second + uy * t,
                        x2 = a.first + ux * (t + winSize), y2 = a.second + uy * (t + winSize),
                        z0 = z, z1 = z + winH, lit = lit,
                        jobDepth = faceDepth - 0.6f,
                    )
                    t += winStep
                    col++
                }
                z -= rowStep
                row++
            }
        }
    }

    /**
     * 벽면 위의 창문 하나를 (x1,y1)-(x2,y2), [z0]~[z1] 사각형으로 투영해 그린다.
     * [jobDepth]는 부모 벽면 깊이 기준으로 정해 벽 위에 확실히 그려지게 한다.
     */
    private fun addWindowQuad(
        jobs: MutableList<Job>, canvas: Canvas,
        x1: Float, y1: Float, x2: Float, y2: Float, z0: Float, z1: Float, lit: Boolean,
        jobDepth: Float,
    ) {
        val (r1, f1) = toCam(x1, y1)
        val (r2, f2) = toCam(x2, y2)
        if (f1 < NEAR || f2 < NEAR) return
        val depth = (f1 + f2) / 2f
        val color = if (lit) Color.argb(220, 255, 214, 120) else Color.argb(150, 168, 200, 226)
        jobs.add(Job(jobDepth) {
            val p = Path().apply {
                moveTo(projX(r1, f1), projY(f1, z0))
                lineTo(projX(r2, f2), projY(f2, z0))
                lineTo(projX(r2, f2), projY(f2, z1))
                lineTo(projX(r1, f1), projY(f1, z1))
                close()
            }
            polyPaint.color = shadeAlpha(color, depth)
            canvas.drawPath(p, polyPaint)
        })
    }

    /** 옥상 위 작은 설비함(에어컨 실외기/물탱크 느낌). 건물마다 위치가 결정적으로 다르다. */
    private fun addRooftopUnit(jobs: MutableList<Job>, canvas: Canvas, w: Wall, height: Float, seed: Int) {
        val bw = w.right - w.left
        val bh = w.bottom - w.top
        val size = (min(bw, bh) * 0.22f).coerceAtMost(34f)
        if (size < 14f) return
        val margin = size
        val (cx, cy) = when (seed % 4) {
            0 -> Pair(w.left + margin, w.top + margin)
            1 -> Pair(w.right - margin, w.top + margin)
            2 -> Pair(w.right - margin, w.bottom - margin)
            else -> Pair(w.left + margin, w.bottom - margin)
        }
        val half = size / 2f
        addPrism(
            jobs, canvas,
            corners = listOf(
                Pair(cx - half, cy - half), Pair(cx + half, cy - half),
                Pair(cx + half, cy + half), Pair(cx - half, cy + half),
            ),
            heightZ = height + size * 0.85f,
            baseZ = height,
            color = Color.rgb(124, 132, 136),
        )
    }

    /** 두 바닥 꼭짓점(카메라 좌표) 사이의 [baseZ]~heightZ 수직 벽면. */
    private fun addWallFace(
        jobs: MutableList<Job>, canvas: Canvas,
        a: Pair<Float, Float>, b: Pair<Float, Float>,
        heightZ: Float, color: Int, brightness: Float,
        baseZ: Float = 0f,
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
            moveTo(projX(r1, f1), projY(f1, baseZ))
            lineTo(projX(r2, f2), projY(f2, baseZ))
            lineTo(projX(r2, f2), projY(f2, heightZ))
            lineTo(projX(r1, f1), projY(f1, heightZ))
            close()
        }
        val depth = (f1 + f2) / 2f
        jobs.add(Job(depth) {
            polyPaint.color = shade(color, depth, brightness)
            canvas.drawPath(face, polyPaint)
        })
    }

    // ── 코너 반사경: 기둥 + 거울 원판 ─────────────────────────────────

    private fun addMirror(jobs: MutableList<Job>, canvas: Canvas, m: io.github.cho1017.commutechaos.model.MirrorState) {
        val poleHalf = 5f
        // 기둥
        addPrism(
            jobs, canvas,
            corners = listOf(
                Pair(m.x - poleHalf, m.y - poleHalf), Pair(m.x + poleHalf, m.y - poleHalf),
                Pair(m.x + poleHalf, m.y + poleHalf), Pair(m.x - poleHalf, m.y + poleHalf),
            ),
            heightZ = MIRROR_H,
            color = Color.rgb(120, 134, 142),
        )
        // 거울 원판 (빌보드)
        val (r, f) = toCam(m.x, m.y)
        if (f < NEAR) return
        val cx = projX(r, f)
        val cy = projY(f, MIRROR_H + 14f)
        val radius = (focal / f * 15f).coerceIn(3f, 70f)
        jobs.add(Job(f - 2f) {
            if (m.alert) {
                polyPaint.color = Color.argb(100, 255, 152, 0)
                canvas.drawCircle(cx, cy, radius * 1.9f, polyPaint)
            }
            polyPaint.color = if (m.alert) Color.rgb(255, 152, 0) else shade(Color.rgb(207, 216, 220), f)
            canvas.drawCircle(cx, cy, radius, polyPaint)
            strokePaint.color = Color.rgb(55, 71, 79)
            canvas.drawCircle(cx, cy, radius, strokePaint)
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
