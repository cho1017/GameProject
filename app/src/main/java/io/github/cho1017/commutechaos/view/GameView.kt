package io.github.cho1017.commutechaos.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import io.github.cho1017.commutechaos.model.GameEngine
import io.github.cho1017.commutechaos.model.GameUiState
import io.github.cho1017.commutechaos.model.LeaderboardStatus
import io.github.cho1017.commutechaos.model.Level
import io.github.cho1017.commutechaos.model.Phase
import kotlin.math.sin
import kotlin.random.Random

/**
 * 순수 렌더러. [render]로 받은 상태를 그리기만 하고,
 * 입력은 [onSteer]/[onTap] 콜백으로 ViewModel에 위임한다.
 */
class GameView(context: Context) : View(context) {

    private companion object {
        /** WIN 패널에 화면상 몇 줄까지 보여줄지 (서버에서는 더 받아와도 됨). */
        const val LEADERBOARD_ROWS = 5
    }

    var onSteer: (Float) -> Unit = {}
    var onTap: () -> Unit = {}

    private enum class CameraMode { TOP_DOWN, CHASE }

    private var cameraMode = CameraMode.CHASE
    private val chaseRenderer = ChaseRenderer()
    private val toggleRect = RectF()

    /** WIN 화면에서 온라인 리더보드 패널을 펼쳤는지. 라운드가 바뀌면 자동으로 닫힌다. */
    private var showLeaderboard = false
    private val leaderboardToggleRect = RectF()
    private val leaderboardPanelRect = RectF()

    private var state = GameUiState()

    private val bgPaint = Paint().apply { color = Color.rgb(38, 50, 56) }
    private val laneLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 235, 59)
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(28f, 22f), 0f)
    }
    private val borderWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(84, 110, 122) }
    private val windowDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gardenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(67, 132, 78) }
    private val bushPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(69, 90, 100) }
    private val wallTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(96, 125, 139) }

    /** 건물마다 다른 재질감을 주는 팔레트 (몸체/상단 한 쌍). ChaseRenderer와 톤을 맞췄다. */
    private val buildingBodyPalette = intArrayOf(
        Color.rgb(69, 90, 100), Color.rgb(93, 64, 55), Color.rgb(69, 90, 96),
        Color.rgb(90, 74, 66), Color.rgb(51, 77, 77),
    )
    private val buildingTopPalette = intArrayOf(
        Color.rgb(96, 125, 139), Color.rgb(124, 92, 82), Color.rgb(96, 125, 130),
        Color.rgb(122, 103, 92), Color.rgb(84, 122, 122),
    )
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.rgb(102, 187, 106)
    }
    private val goalFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(50, 102, 187, 106) }
    private val carPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 20, 30, 40) }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f
        isFakeBoldText = true
    }
    private val hudWarnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 83, 80)
        textSize = 48f
        isFakeBoldText = true
    }
    private val hudSmallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = 34f
    }
    private val overlayPaint = Paint().apply { color = Color.argb(170, 0, 0, 0) }
    private val flashPaint = Paint()
    private val centerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val centerSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        textSize = 38f
        textAlign = Paint.Align.CENTER
    }
    private val storyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 213, 79)
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val toggleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 20, 28, 32) }
    private val togglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val nearMissPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 213, 79)
        textSize = 56f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 213, 79)
        textSize = 38f
        isFakeBoldText = true
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }
    private val mirrorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mirrorGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mirrorRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.rgb(55, 71, 79)
    }
    private val pickupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 213, 79) }
    private val pickupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(38, 50, 56)
        textSize = 34f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val leaderboardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 26, 35, 41) }
    private val leaderboardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 213, 79)
        textSize = 38f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val leaderboardRowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
    }
    private val leaderboardMePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(129, 199, 132)
        textSize = 32f
        isFakeBoldText = true
    }
    private val leaderboardHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val rect = RectF()

    fun render(newState: GameUiState) {
        // 라운드가 다시 시작되면 (WIN이 아니게 되면) 리더보드 패널은 자동으로 닫는다.
        if (newState.phase != Phase.WIN) showLeaderboard = false
        state = newState
        invalidate()
    }

    /** 월드 좌표 → 화면 좌표 스케일. 화면을 꽉 채우도록 늘린다. */
    private fun sx() = width / Level.WORLD_W
    private fun sy() = height / Level.WORLD_H

    override fun onDraw(canvas: Canvas) {
        // 충돌 직후 화면 흔들림
        if (state.shake > 0f) {
            val mag = state.shake * 18f
            canvas.save()
            canvas.translate(
                (Random.nextFloat() - 0.5f) * mag,
                (Random.nextFloat() - 0.5f) * mag,
            )
        }

        if (cameraMode == CameraMode.CHASE) {
            chaseRenderer.draw(canvas, state, width, height)
        } else {
            drawTopDown(canvas)
        }

        if (state.shake > 0f) canvas.restore()

        // 충돌 플래시
        if (state.penaltyFlash > 0f) {
            flashPaint.color = Color.argb((90 * state.penaltyFlash).toInt(), 244, 67, 54)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }

        drawHud(canvas)

        // 니어미스 토스트 (주행 중, 콤보 강조)
        if (state.phase == Phase.DRIVING && state.nearMissFlash > 0f) {
            drawNearMiss(canvas)
        }

        when (state.phase) {
            Phase.INTRO -> drawIntro(canvas)
            Phase.GAME_OVER -> drawOverlay(canvas, "지각했다... 😵", "탭해서 처음부터 다시")
            Phase.WIN -> {
                val sub = if (state.newRecord) {
                    "🏆 신기록! 남은 시간 %.1f초 · 탭해서 한 판 더".format(state.timeLeft)
                } else {
                    "남은 시간 %.1f초 · 탭해서 한 판 더".format(state.timeLeft)
                }
                drawOverlay(canvas, "전원 무사 도착! 🎉", sub)
                drawLeaderboardSection(canvas)
            }
            Phase.DRIVING -> Unit
        }
    }

    private fun drawTopDown(canvas: Canvas) {
        val sx = sx()
        val sy = sy()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 도로 중앙선 (점선)
        for (l in Level.roadLines) {
            canvas.drawLine(l.x1 * sx, l.y1 * sy, l.x2 * sx, l.y2 * sy, laneLinePaint)
        }

        // 건물 블록 (살짝 입체감)
        for (w in state.walls) {
            if (w == Level.garden) {
                drawGardenTopDown(canvas, w, sx, sy)
                continue
            }
            val isBorder = w.left <= 0f || w.top <= 0f || w.right >= Level.WORLD_W || w.bottom >= Level.WORLD_H
            rect.set(w.left * sx, w.top * sy, w.right * sx, w.bottom * sy)
            if (isBorder) {
                canvas.drawRect(rect, borderWallPaint)
                continue
            }
            val seed = ((w.left / 10f).toInt() * 73 + (w.top / 10f).toInt() * 131) and 0x7fffffff
            val paletteIdx = seed % buildingBodyPalette.size
            wallPaint.color = buildingBodyPalette[paletteIdx]
            wallTopPaint.color = buildingTopPalette[paletteIdx]
            canvas.drawRoundRect(rect, 12f, 12f, wallPaint)
            rect.inset(8f, 8f)
            rect.offset(0f, -4f)
            canvas.drawRoundRect(rect, 10f, 10f, wallTopPaint)
            drawWindows(canvas, w, sx, sy)
        }

        // 목적지: 시간에 따라 맥박처럼 커졌다 작아진다
        val pulse = 1f + 0.08f * sin(state.elapsed * 4f)
        val gr = GameEngine.GOAL_RADIUS * sx * pulse
        canvas.drawCircle(state.goalX * sx, state.goalY * sy, gr, goalFillPaint)
        canvas.drawCircle(state.goalX * sx, state.goalY * sy, gr, goalPaint)

        // 코너 반사경: 근처에 리플레이 차량이 있으면 주황색으로 빛난다
        for (m in state.mirrors) {
            val mx = m.x * sx
            val my = m.y * sy
            if (m.alert) {
                mirrorGlowPaint.color = Color.argb(90, 255, 152, 0)
                canvas.drawCircle(mx, my, 26f, mirrorGlowPaint)
            }
            mirrorPaint.color = if (m.alert) Color.rgb(255, 152, 0) else Color.rgb(176, 190, 197)
            canvas.drawCircle(mx, my, 11f, mirrorPaint)
            canvas.drawCircle(mx, my, 11f, mirrorRimPaint)
        }

        // 시간 아이템: 동전처럼 살짝 둥실거린다
        for (p in state.pickups) {
            if (p.collected) continue
            val bob = sin(state.elapsed * 5f) * 4f
            val px = p.x * sx
            val py = p.y * sy + bob
            canvas.drawCircle(px, py, GameEngine.PICKUP_RADIUS * sx, pickupPaint)
            canvas.drawText("+3", px, py + 12f, pickupTextPaint)
        }

        // 플레이어 타이어 잔상
        for (t in state.trail) {
            trailPaint.color = colorWithAlpha(state.playerColor, (t.alpha * 255).toInt())
            canvas.drawCircle(t.x * sx, t.y * sy, state.playerRadius * 0.45f * sx, trailPaint)
        }

        // 리플레이 차량들
        for (g in state.ghosts) {
            if (!g.visible) continue
            drawCar(canvas, g.pose.x, g.pose.y, g.pose.heading, g.radius, g.color)
        }

        // 플레이어
        val p = state.player
        drawCar(canvas, p.x, p.y, p.heading, state.playerRadius, state.playerColor)
    }

    /** HUD와 시점 전환 버튼. 두 시점 공통. */
    private fun drawHud(canvas: Canvas) {
        // 5초 미만이면 빨간 경고색
        val timePaint = if (state.timeLeft < 5f) hudWarnPaint else hudPaint
        canvas.drawText("⏱ %.1f".format(state.timeLeft), 24f, 64f, timePaint)
        canvas.drawText(
            "${state.roundIndex + 1}/${state.totalVehicles}  ${state.vehicleName}",
            24f, 110f, hudSmallPaint,
        )
        state.bestTime?.let {
            val stars = if (state.bestStars > 0) "  " + "★".repeat(state.bestStars) else ""
            canvas.drawText("🏆 %.1f".format(it) + stars, 24f, 152f, hudSmallPaint)
        }

        // 니어미스 콤보 (주행 중 유지되는 카운터)
        if (state.phase == Phase.DRIVING && state.nearMissCombo >= 2) {
            canvas.drawText("콤보 x${state.nearMissCombo}", 24f, 196f, comboPaint)
        }

        // 시점 전환 버튼 (오른쪽 위)
        toggleRect.set(width - 150f, 24f, width - 24f, 100f)
        canvas.drawRoundRect(toggleRect, 16f, 16f, toggleBgPaint)
        val label = if (cameraMode == CameraMode.CHASE) "2D" else "3D"
        canvas.drawText("🎥 $label", toggleRect.centerX(), toggleRect.centerY() + 14f, togglePaint)
    }

    private fun drawCar(canvas: Canvas, wx: Float, wy: Float, heading: Float, radius: Float, color: Long) {
        val sx = sx()
        val sy = sy()
        val cx = wx * sx
        val cy = wy * sy
        val halfL = radius * 1.25f * sx
        val halfW = radius * 0.75f * sx

        carPaint.color = colorWithAlpha(color, 255)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(Math.toDegrees(heading.toDouble()).toFloat())
        rect.set(-halfL, -halfW, halfL, halfW)
        canvas.drawRoundRect(rect, halfW * 0.5f, halfW * 0.5f, carPaint)
        // 앞유리: 진행 방향 표시
        rect.set(halfL * 0.15f, -halfW * 0.7f, halfL * 0.65f, halfW * 0.7f)
        canvas.drawRoundRect(rect, 6f, 6f, windowPaint)
        canvas.restore()
    }

    /** 건물 옥상의 창문(채광창) 격자. 일부는 불이 켜져 있다. */
    private fun drawWindows(canvas: Canvas, w: io.github.cho1017.commutechaos.model.Wall, sx: Float, sy: Float) {
        val step = 55f
        val size = 20f
        var wy = w.top + 40f
        var row = 0
        while (wy + size < w.bottom - 30f) {
            var wx = w.left + 40f
            var col = 0
            while (wx + size < w.right - 30f) {
                // 결정적 패턴으로 일부 창문만 점등 (매 프레임 동일해야 깜빡이지 않는다)
                val lit = (row * 7 + col * 13 + (w.left / 10).toInt()) % 5 == 0
                windowDetailPaint.color = if (lit) Color.argb(200, 255, 224, 130) else Color.argb(90, 30, 42, 48)
                rect.set(wx * sx, wy * sy, (wx + size) * sx, (wy + size) * sy)
                canvas.drawRoundRect(rect, 3f, 3f, windowDetailPaint)
                wx += step
                col++
            }
            wy += step
            row++
        }
    }

    /** 중앙 화단: 녹지 + 수풀. */
    private fun drawGardenTopDown(canvas: Canvas, w: io.github.cho1017.commutechaos.model.Wall, sx: Float, sy: Float) {
        rect.set(w.left * sx, w.top * sy, w.right * sx, w.bottom * sy)
        canvas.drawRoundRect(rect, 14f, 14f, gardenPaint)
        val cx = (w.left + w.right) / 2f
        var by = w.top + 30f
        var i = 0
        while (by < w.bottom - 20f) {
            bushPaint.color = if (i % 2 == 0) Color.rgb(56, 118, 68) else Color.rgb(46, 100, 58)
            canvas.drawCircle((cx + if (i % 2 == 0) -6f else 6f) * sx, by * sy, 14f * sx, bushPaint)
            by += 42f
            i++
        }
    }

    private fun colorWithAlpha(color: Long, alpha: Int): Int = Color.argb(
        alpha,
        (color shr 16 and 0xFF).toInt(),
        (color shr 8 and 0xFF).toInt(),
        (color and 0xFF).toInt(),
    )

    private fun drawIntro(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        val cy = height / 2f
        canvas.drawText(state.vehicleName, width / 2f, cy - 80f, centerText)
        canvas.drawText("“${state.vehicleStory}”", width / 2f, cy, storyText)
        canvas.drawText("화면 왼쪽/오른쪽을 눌러 조향 · 탭해서 출발!", width / 2f, cy + 80f, centerSub)
    }

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawText(title, width / 2f, height / 2f - 30f, centerText)
        canvas.drawText(sub, width / 2f, height / 2f + 50f, centerSub)
        // 승리 화면엔 별 등급을 함께
        if (state.phase == Phase.WIN) {
            drawStars(canvas, state.stars, width / 2f, height / 2f + 130f, 48f)
        }
    }

    /** 니어미스 토스트: 화면 상단 중앙에 떠올랐다 사라진다. */
    private fun drawNearMiss(canvas: Canvas) {
        val a = (state.nearMissFlash.coerceIn(0f, 1f) * 255).toInt()
        val rise = (1f - state.nearMissFlash) * 40f
        val y = height * 0.32f - rise
        nearMissPaint.alpha = a
        val combo = state.nearMissCombo
        val text = if (combo >= 2) "간발의 차! x$combo  +1초" else "간발의 차!  +1초"
        canvas.drawText(text, width / 2f, y, nearMissPaint)
    }

    /** 별 등급을 채운 별/빈 별로 그린다. */
    private fun drawStars(canvas: Canvas, filled: Int, cx: Float, cy: Float, size: Float) {
        val gap = size * 1.3f
        val startX = cx - gap
        for (i in 0 until 3) {
            starPaint.color = if (i < filled) Color.rgb(255, 213, 79) else Color.argb(90, 255, 255, 255)
            canvas.drawText("★", startX + i * gap, cy, starPaint.also { it.textSize = size })
        }
    }

    /**
     * WIN 화면의 온라인 리더보드 버튼/패널.
     * 닫혀 있으면 작은 "리더보드 보기" 버튼만, 펼치면 상위 기록 패널을 그린다.
     */
    private fun drawLeaderboardSection(canvas: Canvas) {
        val cy = height / 2f
        if (!showLeaderboard) {
            leaderboardToggleRect.set(width / 2f - 140f, cy + 175f, width / 2f + 140f, cy + 235f)
            canvas.drawRoundRect(leaderboardToggleRect, 16f, 16f, toggleBgPaint)
            canvas.drawText(
                "🌐 온라인 랭킹 보기", leaderboardToggleRect.centerX(), leaderboardToggleRect.centerY() + 12f, togglePaint,
            )
            return
        }

        leaderboardPanelRect.set(width * 0.1f, cy - 260f, width * 0.9f, cy + 235f)
        canvas.drawRoundRect(leaderboardPanelRect, 20f, 20f, leaderboardBgPaint)
        canvas.drawText("🌐 전체 랭킹 TOP $LEADERBOARD_ROWS", leaderboardPanelRect.centerX(), leaderboardPanelRect.top + 52f, leaderboardTitlePaint)

        when (state.leaderboardStatus) {
            LeaderboardStatus.LOADING -> canvas.drawText(
                "불러오는 중…", leaderboardPanelRect.centerX(), leaderboardPanelRect.centerY(), leaderboardHintPaint,
            )
            LeaderboardStatus.UNAVAILABLE -> {
                canvas.drawText(
                    "온라인 리더보드가 아직 설정되지 않았어요", leaderboardPanelRect.centerX(), leaderboardPanelRect.centerY(), leaderboardHintPaint,
                )
            }
            LeaderboardStatus.IDLE -> canvas.drawText(
                "불러오는 중…", leaderboardPanelRect.centerX(), leaderboardPanelRect.centerY(), leaderboardHintPaint,
            )
            LeaderboardStatus.ERROR -> canvas.drawText(
                "기록을 불러오지 못했어요. 네트워크를 확인해주세요",
                leaderboardPanelRect.centerX(), leaderboardPanelRect.centerY(), leaderboardHintPaint,
            )
            LeaderboardStatus.LOADED -> {
                if (state.leaderboard.isEmpty()) {
                    canvas.drawText(
                        "아직 기록이 없어요. 첫 주인공이 되어보세요!",
                        leaderboardPanelRect.centerX(), leaderboardPanelRect.centerY(), leaderboardHintPaint,
                    )
                } else {
                    var ry = leaderboardPanelRect.top + 104f
                    state.leaderboard.take(LEADERBOARD_ROWS).forEachIndexed { i, e ->
                        val paint = if (e.nickname == state.nickname) leaderboardMePaint else leaderboardRowPaint
                        // 아주 긴 별명이 패널을 벗어나지 않게 자른다
                        val name = if (e.nickname.length > 14) e.nickname.take(14) + "…" else e.nickname
                        val stars = "★".repeat(e.stars.coerceIn(0, 3)) + "☆".repeat(3 - e.stars.coerceIn(0, 3))
                        val line = "${i + 1}. $name — %.1f초 %s".format(e.timeLeft, stars)
                        canvas.drawText(line, leaderboardPanelRect.left + 28f, ry, paint)
                        ry += 44f
                    }
                }
            }
        }
        canvas.drawText("탭해서 닫기", leaderboardPanelRect.centerX(), leaderboardPanelRect.bottom - 20f, leaderboardHintPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (toggleRect.contains(event.x, event.y)) {
                    cameraMode = if (cameraMode == CameraMode.CHASE) CameraMode.TOP_DOWN else CameraMode.CHASE
                    invalidate()
                    return true
                }
                if (state.phase == Phase.WIN && showLeaderboard) {
                    // 패널이 펼쳐진 동안은 아무 데나 탭해도 닫히기만 한다 (실수로 재시작 방지)
                    showLeaderboard = false
                    invalidate()
                    return true
                }
                if (state.phase == Phase.WIN && leaderboardToggleRect.contains(event.x, event.y)) {
                    showLeaderboard = true
                    invalidate()
                    return true
                }
                if (state.phase != Phase.DRIVING) {
                    onTap()
                } else {
                    onSteer(if (event.x < width / 2f) -1f else 1f)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (state.phase == Phase.DRIVING) {
                    onSteer(if (event.x < width / 2f) -1f else 1f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onSteer(0f)
        }
        return true
    }
}
