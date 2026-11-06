package com.synq

import com.synq.data.remote.dto.UserDto
import com.synq.data.repository.AuthRepository
import com.synq.data.repository.LikedSongsRepository
import com.synq.data.repository.PlaylistRepository
import com.synq.features.music.jam.JamPreferences
import com.synq.features.music.jam.JamSessionManager
import com.synq.features.music.playback.MusicPlayerManager
import com.synq.features.music.sleeptimer.SleepTimerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RootViewModelJamAuthTest {

    private val testDispatcher = StandardTestDispatcher()
    private val playerManager: MusicPlayerManager = mockk(relaxed = true)
    private val likedSongsRepository: LikedSongsRepository = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val playlistRepository: PlaylistRepository = mockk(relaxed = true)
    private val sleepTimerManager: SleepTimerManager = mockk(relaxed = true)
    private val jamSessionManager: JamSessionManager = mockk(relaxed = true)
    private val themePreferences: com.synq.data.local.ThemePreferences = mockk(relaxed = true)
    private val jamPreferences: JamPreferences = mockk(relaxed = true)

    private val cachedUserFlow = MutableStateFlow<UserDto?>(null)
    private val isLoggedInFlow = MutableStateFlow<Boolean>(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { authRepository.cachedUser } returns cachedUserFlow
        every { authRepository.isLoggedIn } returns isLoggedInFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): RootViewModel {
        return RootViewModel(
            playerManager = playerManager,
            likedSongsRepository = likedSongsRepository,
            authRepository = authRepository,
            playlistRepository = playlistRepository,
            sleepTimerManager = sleepTimerManager,
            jamSessionManager = jamSessionManager,
            themePreferences = themePreferences,
            jamPreferences = jamPreferences
        )
    }

    @Test
    fun `when user is null startJam refuses to create Jam and sets error`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.startJam()

        // Should NOT call jamSessionManager.createJam
        verify(exactly = 0) { jamSessionManager.createJam(any(), any(), any()) }
        // Should report error
        verify(exactly = 1) { jamSessionManager.setError(match { it.contains("Sign in required") || it.contains("authenticated") }) }
    }

    @Test
    fun `when user is null joinJam refuses to join Jam and sets error`() {
        val vm = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.joinJam("ROOM1")

        // Should NOT call jamSessionManager.joinJam
        verify(exactly = 0) { jamSessionManager.joinJam(any(), any(), any(), any()) }
        // Should report error
        verify(exactly = 1) { jamSessionManager.setError(match { it.contains("Sign in required") || it.contains("authenticated") }) }
    }
}

