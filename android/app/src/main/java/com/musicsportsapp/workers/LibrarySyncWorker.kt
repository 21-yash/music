package com.musicsportsapp.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.musicsportsapp.data.repository.LikedSongsRepository
import com.musicsportsapp.data.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val likedSongsRepository: LikedSongsRepository,
    private val playlistRepository: PlaylistRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 1. Sync liked songs
            val likesResult = likedSongsRepository.syncWithServer()
            // 2. Sync playlists
            val playlistsResult = playlistRepository.syncPlaylists()

            if (likesResult is com.musicsportsapp.core.domain.AppResult.Error && runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
