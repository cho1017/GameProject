package io.github.cho1017.commutechaos.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.cho1017.commutechaos.model.LeaderboardEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 전역 온라인 리더보드. Firestore의 "leaderboard" 컬렉션에 별명(nickname)을 문서 ID로
 * 써서, 플레이어 1명당 한 줄(본인 최고 기록)만 남긴다.
 *
 * [LeaderboardConfig]가 비어 있으면 모든 호출이 조용히 아무 일도 하지 않는다 — 리더보드는
 * 선택 기능이고, 이 게임은 항상 오프라인으로도 완결돼야 한다.
 *
 * 모든 네트워크 호출에 [TIMEOUT_MS] 제한을 건다. 특히 오프라인 상태의 Firestore 쓰기는
 * 서버 확인이 올 때까지 Task가 완료되지 않아, 제한이 없으면 UI가 "불러오는 중"에 영원히
 * 머물 수 있다. (시간 초과로 포기해도 Firestore가 쓰기를 로컬 큐에 보관했다가 온라인이
 * 되면 자동으로 반영해 준다.)
 *
 * 인증 없이 문서 ID(별명)만으로 쓰기를 허용하는 단순한 구조라, 악의적인 사용자가 다른
 * 별명의 기록을 덮어쓰는 걸 막지는 못한다. 나중에 Firebase Anonymous Auth를 붙여 문서
 * ID를 uid로 바꾸면 이 한계를 없앨 수 있다.
 */
class LeaderboardRepository(context: Context) {

    private val appContext = context.applicationContext

    private val firestore: FirebaseFirestore? by lazy {
        if (!LeaderboardConfig.isConfigured) return@lazy null
        val app = FirebaseApp.getApps(appContext).firstOrNull() ?: FirebaseApp.initializeApp(
            appContext,
            FirebaseOptions.Builder()
                .setApiKey(LeaderboardConfig.API_KEY)
                .setApplicationId(LeaderboardConfig.APPLICATION_ID)
                .setProjectId(LeaderboardConfig.PROJECT_ID)
                .build(),
        )
        FirebaseFirestore.getInstance(app)
    }

    /** 이번 기록이 이 별명의 기존 최고 기록보다 좋을 때만 서버에 반영한다. 실패해도 조용히 무시. */
    suspend fun submitIfBest(nickname: String, timeLeft: Float, stars: Int) {
        val db = firestore ?: return
        try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val doc = db.collection(COLLECTION).document(nickname)
                val existing = doc.get().await().toObject(LeaderboardEntry::class.java)
                if (existing == null || timeLeft > existing.timeLeft) {
                    doc.set(LeaderboardEntry(nickname, timeLeft, stars, System.currentTimeMillis())).await()
                }
            }
        } catch (e: CancellationException) {
            throw e // 코루틴 취소는 삼키면 안 된다
        } catch (_: Exception) {
            // 네트워크/권한 오류: 리더보드는 선택 기능이므로 게임 진행을 막지 않는다
        }
    }

    /**
     * 남은 시간이 긴 순으로 상위 [limit]개.
     * 성공하면 목록(비어 있을 수 있음), 시간 초과나 오류면 null을 돌려줘 호출자가
     * "기록 없음"과 "불러오기 실패"를 구분할 수 있게 한다.
     */
    suspend fun top(limit: Int = 10): List<LeaderboardEntry>? {
        val db = firestore ?: return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                db.collection(COLLECTION)
                    .orderBy("timeLeft", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.toObject(LeaderboardEntry::class.java) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val COLLECTION = "leaderboard"
        const val TIMEOUT_MS = 7_000L
    }
}
