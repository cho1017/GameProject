package com.example.myapplication.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.model.CarState
import com.example.myapplication.model.GameEngine
import com.example.myapplication.model.GameUiState
import com.example.myapplication.model.GhostState
import com.example.myapplication.model.Level
import com.example.myapplication.model.MirrorState
import com.example.myapplication.model.Phase
import com.example.myapplication.model.Pickup
import com.example.myapplication.model.Pose
import com.example.myapplication.model.TrailPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 게임 루프와 라운드 진행을 소유한다.
 * View는 [ui]를 구독해 그리고, [setSteer]/[onTap]으로 입력만 전달한다.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TICK_MS = 16L
        const val DT = TICK_MS / 1000f
        const val START_TIME = 25f       // 첫 제한 시간(초)
        const val ROUND_BONUS = 8f       // 라운드 클리어 보너스
        const val CRASH_INVULN = 1.0f    // 리셋 직후 무적 시간(연쇄 충돌 방지)
        const val PICKUP_BONUS = 3f      // 시간 아이템 보너스
        const val MIRROR_RANGE = 260f    // 반사경이 차량을 감지하는 거리
        const val TRAIL_EVERY = 3        // 몇 프레임마다 잔상 점을 남길지
        const val TRAIL_MAX = 30         // 잔상 점 최대 개수
        const val PREFS = "game_records"
        const val KEY_BEST = "best_time_left"
    }

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        GameUiState(
            walls = Level.walls,
            totalVehicles = Level.vehicles.size,
            bestTime = loadBest(),
        )
    )
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    /** 완주한 차량들의 주행 기록. 라운드마다 프레임 단위로 재생된다. */
    private val recordings = mutableListOf<List<Pose>>()
    private val currentRecording = mutableListOf<Pose>()
    private val trail = ArrayDeque<TrailPoint>()
    private var pickups: List<Pickup> = emptyList()

    private var roundIndex = 0
    private var frame = 0
    private var timeLeft = START_TIME
    private var steer = 0f
    private var invuln = 0f
    private var penaltyFlash = 0f
    private var shake = 0f
    private var elapsed = 0f

    init {
        prepareRound()
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (_ui.value.phase == Phase.DRIVING) tick()
            }
        }
    }

    /** 화면 터치 위치에 따라 View가 -1/0/1을 넘긴다. */
    fun setSteer(value: Float) {
        steer = value.coerceIn(-1f, 1f)
    }

    fun onTap() {
        when (_ui.value.phase) {
            Phase.INTRO -> _ui.value = _ui.value.copy(phase = Phase.DRIVING)
            Phase.GAME_OVER, Phase.WIN -> restart()
            Phase.DRIVING -> Unit
        }
    }

    private fun restart() {
        recordings.clear()
        roundIndex = 0
        timeLeft = START_TIME
        prepareRound()
    }

    /** 현재 라운드의 플레이어를 출발 위치에 세우고 INTRO 상태로 전환한다. */
    private fun prepareRound() {
        val spec = Level.vehicles[roundIndex]
        frame = 0
        steer = 0f
        invuln = 0f
        shake = 0f
        currentRecording.clear()
        trail.clear()
        pickups = Level.pickupSpots
        _ui.value = _ui.value.copy(
            phase = Phase.INTRO,
            roundIndex = roundIndex,
            vehicleName = spec.name,
            vehicleStory = spec.story,
            timeLeft = timeLeft,
            player = CarState(spec.startX, spec.startY, spec.startHeading, 0f),
            playerColor = spec.color,
            playerRadius = spec.radius,
            goalX = spec.goalX,
            goalY = spec.goalY,
            ghosts = ghostsAt(0),
            mirrors = mirrorsAt(ghostsAt(0)),
            pickups = pickups,
            trail = emptyList(),
            penaltyFlash = 0f,
            shake = 0f,
            newRecord = false,
        )
    }

    /**
     * 충돌: 이번 차량만 출발점부터 다시. 시간은 계속 흐르므로
     * 박을수록 남은 시간이 녹는다. 리플레이 타임라인도 함께 처음으로 돌린다.
     */
    private fun crash() {
        vibrate()
        val spec = Level.vehicles[roundIndex]
        frame = 0
        invuln = CRASH_INVULN
        penaltyFlash = 1f
        shake = 1f
        currentRecording.clear()
        trail.clear()
        _ui.value = _ui.value.copy(
            player = CarState(spec.startX, spec.startY, spec.startHeading, 0f),
            ghosts = ghostsAt(0),
            trail = emptyList(),
        )
    }

    private fun tick() {
        val spec = Level.vehicles[roundIndex]
        val state = _ui.value

        val player = GameEngine.step(
            state.player, steer, spec.speed, spec.turnRate, spec.radius, DT, Level.walls,
        )
        currentRecording.add(Pose(player.x, player.y, player.heading))
        frame++

        timeLeft -= DT
        elapsed += DT
        if (invuln > 0f) invuln -= DT
        if (penaltyFlash > 0f) penaltyFlash = (penaltyFlash - DT * 2f).coerceAtLeast(0f)
        if (shake > 0f) shake = (shake - DT * 3f).coerceAtLeast(0f)

        if (frame % TRAIL_EVERY == 0) {
            trail.addLast(TrailPoint(player.x, player.y, 1f))
            while (trail.size > TRAIL_MAX) trail.removeFirst()
        }
        val fadedTrail = trail.mapIndexed { i, t ->
            t.copy(alpha = (i + 1f) / trail.size * 0.5f)
        }

        if (timeLeft <= 0f) {
            _ui.value = state.copy(phase = Phase.GAME_OVER, timeLeft = 0f, player = player)
            return
        }

        val ghosts = ghostsAt(frame)

        // 과거의 나와 충돌 → 이 차량은 처음부터
        if (invuln <= 0f) {
            val hit = ghosts.withIndex().any { (i, g) ->
                g.visible && GameEngine.carsCollide(
                    player.x, player.y, spec.radius,
                    g.pose.x, g.pose.y, Level.vehicles[i].radius,
                )
            }
            if (hit) {
                crash()
                return
            }
        }

        // 시간 아이템 획득
        pickups = pickups.map { p ->
            if (!p.collected && GameEngine.touchesPickup(player, spec.radius, p)) {
                timeLeft += PICKUP_BONUS
                p.copy(collected = true)
            } else p
        }

        if (GameEngine.reachedGoal(player, spec.goalX, spec.goalY)) {
            recordings.add(currentRecording.toList())
            roundIndex++
            if (roundIndex >= Level.vehicles.size) {
                val best = loadBest()
                val newRecord = best == null || timeLeft > best
                if (newRecord) saveBest(timeLeft)
                _ui.value = state.copy(
                    phase = Phase.WIN,
                    timeLeft = timeLeft,
                    player = player,
                    ghosts = ghosts,
                    bestTime = if (newRecord) timeLeft else best,
                    newRecord = newRecord,
                )
            } else {
                timeLeft += ROUND_BONUS
                prepareRound()
            }
            return
        }

        _ui.value = state.copy(
            player = player,
            ghosts = ghosts,
            mirrors = mirrorsAt(ghosts),
            pickups = pickups,
            trail = fadedTrail,
            timeLeft = timeLeft,
            penaltyFlash = penaltyFlash,
            shake = shake,
            elapsed = elapsed,
        )
    }

    /** frame 시점의 리플레이 차량들. 기록이 끝난 차는 목적지에 도착해 사라진 것으로 본다. */
    private fun ghostsAt(frame: Int): List<GhostState> =
        recordings.mapIndexed { i, rec ->
            val visible = frame < rec.size
            val pose = if (visible) rec[frame] else rec.last()
            GhostState(pose, Level.vehicles[i].color, Level.vehicles[i].radius, visible)
        }

    /** 반사경 경고 상태: 근처에 보이는 리플레이 차량이 있으면 켜진다. */
    private fun mirrorsAt(ghosts: List<GhostState>): List<MirrorState> =
        Level.mirrors.map { (mx, my) ->
            val alert = ghosts.any { g ->
                g.visible && kotlin.math.hypot(g.pose.x - mx, g.pose.y - my) < MIRROR_RANGE
            }
            MirrorState(mx, my, alert)
        }

    private fun loadBest(): Float? =
        prefs.getFloat(KEY_BEST, -1f).takeIf { it >= 0f }

    private fun saveBest(value: Float) {
        prefs.edit().putFloat(KEY_BEST, value).apply()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val app = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(120)
        }
    }
}
