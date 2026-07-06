package com.example.myapplication.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.example.myapplication.model.GameEngine
import com.example.myapplication.model.GameUiState
import com.example.myapplication.model.Level
import com.example.myapplication.model.Phase
import kotlin.math.sin
import kotlin.random.Random

/**
 * 순수 렌더러. [render]로 받은 상태를 그리기만 하고,
 * 입력은 [onSteer]/[onTap] 콜백으로 ViewModel에 위임한다.
 */
class GameView(context: Context) : View(context) {

    var onSteer: (Float) -> Unit = {}
    var onTap: () -> Unit = {}

    private enum class CameraMode { TOP_DOWN, CHASE }

    private var cameraMode = CameraMode.CHASE
    private val chaseRenderer = ChaseRenderer()
    private val toggleRect = RectF()

    private var state = GameUiState()

    private val bgPaint = Paint().apply { color = Color.rgb(38, 50, 56) }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(69, 90, 100) }
    private val wallTopPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(96, 125, 139) }
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

    private val rect = RectF()

    fun render(newState: GameUiState) {
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
            }
            Phase.DRIVING -> Unit
        }
    }

    private fun drawTopDown(canvas: Canvas) {
        val sx = sx()
        val sy = sy()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 건물 블록 (살짝 입체감)
        for (w in state.walls) {
            rect.set(w.left * sx, w.top * sy, w.right * sx, w.bottom * sy)
            canvas.drawRoundRect(rect, 12f, 12f, wallPaint)
            rect.inset(8f, 8f)
            rect.offset(0f, -4f)
            canvas.drawRoundRect(rect, 10f, 10f, wallTopPaint)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (toggleRect.contains(event.x, event.y)) {
                    cameraMode = if (cameraMode == CameraMode.CHASE) CameraMode.TOP_DOWN else CameraMode.CHASE
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
