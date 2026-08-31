package io.github.cho1017.commutechaos.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.cho1017.commutechaos.model.LeaderboardEntry
import kotlinx.coroutines.tasks.await

/**
 * 전역 온라인 리더보드. Firestore의 "leaderboard" 컬렉션에 별명(nickname)을 문서 ID로
 * 써서, 플레이어 1명당 한 줄(본인 최고 기록)만 남긴다.
 *
 * [LeaderboardConfig]가 비어 있으면 모든 호출이 조용히 아무 일도 하지 않는다 — 리더보드는
 * 선택 기능이고, 이 게임은 항상 오프라인으로도 완결돼야 한다.
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
        runCatching {
            val doc = db.collection(COLLECTION).document(nickname)
            val existing = doc.get().await().toObject(LeaderboardEntry::class.java)
            if (existing == null || timeLeft > existing.timeLeft) {
                doc.set(LeaderboardEntry(nickname, timeLeft, stars, System.currentTimeMillis())).await()
            }
        }
    }

    /** 남은 시간이 긴 순으로 상위 [limit]개. 설정이 안 됐거나 네트워크 오류면 빈 목록. */
    suspend fun top(limit: Int = 10): List<LeaderboardEntry> {
        val db = firestore ?: return emptyList()
        return runCatching {
            db.collection(COLLECTION)
                .orderBy("timeLeft", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(LeaderboardEntry::class.java) }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val COLLECTION = "leaderboard"
    }
}
