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

/**
 * 순수 렌더러. [render]로 받은 상태를 그리기만 하고,
 * 입력은 [onSteer]/[onTap] 콜백으로 ViewModel에 위임한다.
 */
class GameView(context: Context) : View(context) {

    var onSteer: (Float) -> Unit = {}
    var onTap: () -> Unit = {}

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
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 20, 30, 40) }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
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

    private val rect = RectF()

    fun render(newState: GameUiState) {
        state = newState
        invalidate()
    }

    /** 월드 좌표 → 화면 좌표 스케일. 화면을 꽉 채우도록 늘린다. */
    private fun sx() = width / Level.WORLD_W
    private fun sy() = height / Level.WORLD_H

    override fun onDraw(canvas: Canvas) {
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

        // 목적지
        val gr = GameEngine.GOAL_RADIUS * sx
        canvas.drawCircle(state.goalX * sx, state.goalY * sy, gr, goalFillPaint)
        canvas.drawCircle(state.goalX * sx, state.goalY * sy, gr, goalPaint)

        // 리플레이 차량들
        for (g in state.ghosts) {
            if (!g.visible) continue
            drawCar(canvas, g.pose.x, g.pose.y, g.pose.heading, g.color, alpha = 255)
        }

        // 플레이어
        val p = state.player
        drawCar(canvas, p.x, p.y, p.heading, state.playerColor, alpha = 255)

        // 충돌 플래시
        if (state.penaltyFlash > 0f) {
            flashPaint.color = Color.argb((90 * state.penaltyFlash).toInt(), 244, 67, 54)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
        }

        // HUD
        canvas.drawText("⏱ %.1f".format(state.timeLeft), 24f, 64f, hudPaint)
        canvas.drawText(
            "${state.roundIndex + 1}/${state.totalVehicles}  ${state.vehicleName}",
            24f, 110f, hudSmallPaint,
        )

        when (state.phase) {
            Phase.INTRO -> drawOverlay(
                canvas,
                state.vehicleName,
                "화면 왼쪽/오른쪽을 눌러 조향 · 탭해서 출발!",
            )
            Phase.GAME_OVER -> drawOverlay(canvas, "지각했다... 😵", "탭해서 처음부터 다시")
            Phase.WIN -> drawOverlay(canvas, "전원 무사 도착! 🎉", "탭해서 한 판 더")
            Phase.DRIVING -> Unit
        }
    }

    private fun drawCar(canvas: Canvas, wx: Float, wy: Float, heading: Float, color: Long, alpha: Int) {
        val sx = sx()
        val sy = sy()
        val cx = wx * sx
        val cy = wy * sy
        val halfL = GameEngine.CAR_RADIUS * 1.25f * sx
        val halfW = GameEngine.CAR_RADIUS * 0.75f * sx

        carPaint.color = Color.argb(
            alpha,
            (color shr 16 and 0xFF).toInt(),
            (color shr 8 and 0xFF).toInt(),
            (color and 0xFF).toInt(),
        )
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

    private fun drawOverlay(canvas: Canvas, title: String, sub: String) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        canvas.drawText(title, width / 2f, height / 2f - 30f, centerText)
        canvas.drawText(sub, width / 2f, height / 2f + 50f, centerSub)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
