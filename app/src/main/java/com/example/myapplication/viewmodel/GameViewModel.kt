package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.model.CarState
import com.example.myapplication.model.GameEngine
import com.example.myapplication.model.GameUiState
import com.example.myapplication.model.GhostState
import com.example.myapplication.model.Level
import com.example.myapplication.model.Phase
import com.example.myapplication.model.Pose
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
class GameViewModel : ViewModel() {

    private companion object {
        const val TICK_MS = 16L
        const val DT = TICK_MS / 1000f
        const val START_TIME = 25f      // 첫 제한 시간(초)
        const val ROUND_BONUS = 8f      // 라운드 클리어 보너스
        const val CRASH_PENALTY = 3f    // 리플레이 차량과 충돌 시 감점
        const val INVULN_TIME = 1.5f    // 충돌 후 무적 시간
    }

    private val _ui = MutableStateFlow(GameUiState(walls = Level.walls, totalVehicles = Level.vehicles.size))
    val ui: StateFlow<GameUiState> = _ui.asStateFlow()

    /** 완주한 차량들의 주행 기록. 라운드마다 프레임 단위로 재생된다. */
    private val recordings = mutableListOf<List<Pose>>()
    private val currentRecording = mutableListOf<Pose>()

    private var roundIndex = 0
    private var frame = 0
    private var timeLeft = START_TIME
    private var steer = 0f
    private var invuln = 0f
    private var penaltyFlash = 0f

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
        currentRecording.clear()
        _ui.value = _ui.value.copy(
            phase = Phase.INTRO,
            roundIndex = roundIndex,
            vehicleName = spec.name,
            timeLeft = timeLeft,
            player = CarState(spec.startX, spec.startY, spec.startHeading, 0f),
            playerColor = spec.color,
            goalX = spec.goalX,
            goalY = spec.goalY,
            ghosts = ghostsAt(0),
            penaltyFlash = 0f,
        )
    }

    private fun tick() {
        val spec = Level.vehicles[roundIndex]
        val state = _ui.value

        val player = GameEngine.step(state.player, steer, spec.speed, DT, Level.walls)
        currentRecording.add(Pose(player.x, player.y, player.heading))
        frame++

        timeLeft -= DT
        if (invuln > 0f) invuln -= DT
        if (penaltyFlash > 0f) penaltyFlash = (penaltyFlash - DT * 2f).coerceAtLeast(0f)

        val ghosts = ghostsAt(frame)

        // 과거의 나와 충돌
        if (invuln <= 0f) {
            val hit = ghosts.any { g ->
                g.visible && GameEngine.carsCollide(player.x, player.y, g.pose.x, g.pose.y)
            }
            if (hit) {
                timeLeft -= CRASH_PENALTY
                invuln = INVULN_TIME
                penaltyFlash = 1f
            }
        }

        if (timeLeft <= 0f) {
            _ui.value = state.copy(phase = Phase.GAME_OVER, timeLeft = 0f, player = player, ghosts = ghosts)
            return
        }

        if (GameEngine.reachedGoal(player, spec.goalX, spec.goalY)) {
            recordings.add(currentRecording.toList())
            roundIndex++
            if (roundIndex >= Level.vehicles.size) {
                _ui.value = state.copy(phase = Phase.WIN, timeLeft = timeLeft, player = player, ghosts = ghosts)
            } else {
                timeLeft += ROUND_BONUS
                prepareRound()
            }
            return
        }

        _ui.value = state.copy(
            player = player,
            ghosts = ghosts,
            timeLeft = timeLeft,
            penaltyFlash = penaltyFlash,
        )
    }

    /** frame 시점의 리플레이 차량들. 기록이 끝난 차는 목적지에 도착해 사라진 것으로 본다. */
    private fun ghostsAt(frame: Int): List<GhostState> =
        recordings.mapIndexed { i, rec ->
            val visible = frame < rec.size
            val pose = if (visible) rec[frame] else rec.last()
            GhostState(pose, Level.vehicles[i].color, visible)
        }
}
