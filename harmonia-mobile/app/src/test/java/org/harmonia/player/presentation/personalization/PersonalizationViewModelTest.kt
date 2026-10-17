package org.harmonia.player.presentation.personalization

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.PersonalizationRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonalizationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakePersonalizationRepository
    private lateinit var fakePlaybackPort: FakePlaybackPortForPersonalization
    private lateinit var viewModel: PersonalizationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePersonalizationRepository()
        fakePlaybackPort = FakePlaybackPortForPersonalization()
        viewModel = PersonalizationViewModel(
            personalizationRepository = fakeRepository,
            playbackPort = fakePlaybackPort
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_when_no_track_selected() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.trackId)
        assertFalse(state.isFavorite)
        assertNull(state.rating)
        assertFalse(state.isLoading)
        assertNull(state.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun initial_unrated_state_when_track_is_bound() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("trk_1", state.trackId)
        assertFalse(state.isFavorite)
        assertNull(state.rating)
        assertFalse(state.isLoading)
        assertNull(state.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun favorite_updates_reactively() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        viewModel.setFavorite(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertTrue(fakeRepository.getPersonalization("trk_1").isFavorite)

        viewModel.setFavorite(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertFalse(fakeRepository.getPersonalization("trk_1").isFavorite)

        collectJob.cancel()
    }

    @Test
    fun toggle_favorite_flips_state() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFavorite)

        collectJob.cancel()
    }

    @Test
    fun rating_updates_reactively_for_valid_values() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        for (star in 1..5) {
            viewModel.setRating(star)
            advanceUntilIdle()
            assertEquals(star, viewModel.uiState.value.rating)
            assertEquals(star, fakeRepository.getPersonalization("trk_1").rating)
        }

        collectJob.cancel()
    }

    @Test
    fun rating_rejected_when_out_of_range() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        viewModel.setRating(0)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.userErrorMessage)
        assertTrue(viewModel.uiState.value.userErrorMessage!!.contains("between 1 and 5 stars"))
        assertNull(viewModel.uiState.value.rating)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.userErrorMessage)

        viewModel.setRating(6)
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.userErrorMessage)
        assertTrue(viewModel.uiState.value.userErrorMessage!!.contains("between 1 and 5 stars"))

        collectJob.cancel()
    }

    @Test
    fun clear_rating_sets_rating_to_null() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        viewModel.setRating(4)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.rating)

        viewModel.clearRating()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.rating)
        assertNull(fakeRepository.getPersonalization("trk_1").rating)

        collectJob.cancel()
    }

    @Test
    fun clear_personalization_removes_both_favorite_and_rating() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        viewModel.setFavorite(true)
        viewModel.setRating(5)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(5, viewModel.uiState.value.rating)

        viewModel.clearPersonalization()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.rating)

        collectJob.cancel()
    }

    @Test
    fun track_changes_via_playback_port_updates_ui_state_cleanly() = runTest(testDispatcher) {
        // Seed track personalization in fake repository
        fakeRepository.setFavorite("trk_b", true)
        fakeRepository.setRating("trk_b", 4)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Active playback plays trk_a
        fakePlaybackPort.setCurrentTrackId("trk_a")
        advanceUntilIdle()
        assertEquals("trk_a", viewModel.uiState.value.trackId)
        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.rating)

        // Active playback transitions to trk_b
        fakePlaybackPort.setCurrentTrackId("trk_b")
        advanceUntilIdle()
        assertEquals("trk_b", viewModel.uiState.value.trackId)
        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(4, viewModel.uiState.value.rating)

        collectJob.cancel()
    }

    @Test
    fun manual_track_binding_overrides_playback_and_resumes() = runTest(testDispatcher) {
        fakePlaybackPort.setCurrentTrackId("trk_playing")
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()
        assertEquals("trk_playing", viewModel.uiState.value.trackId)

        // Manual bind to trk_manual
        viewModel.bindTrack("trk_manual")
        advanceUntilIdle()
        assertEquals("trk_manual", viewModel.uiState.value.trackId)

        // Resume following playback
        viewModel.resumeFollowingPlayback()
        advanceUntilIdle()
        assertEquals("trk_playing", viewModel.uiState.value.trackId)

        collectJob.cancel()
    }

    @Test
    fun rapid_actions_are_serialized_via_mutex_without_race_conditions() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        // Launch multiple rapid operations in sequence
        viewModel.setFavorite(true)
        viewModel.setRating(2)
        viewModel.setRating(5)
        viewModel.toggleFavorite() // true -> false
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(5, viewModel.uiState.value.rating)
        assertNull(viewModel.uiState.value.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun repository_failure_on_mutation_exposes_user_error_message() = runTest(testDispatcher) {
        fakeRepository.shouldThrowOnFavorite = true

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_1")
        advanceUntilIdle()

        viewModel.setFavorite(true)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.userErrorMessage)
        assertEquals("Simulated repository failure on favorite", viewModel.uiState.value.userErrorMessage)

        viewModel.clearError()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun repository_failure_on_observe_exposes_user_error_message_without_crashing_flow() = runTest(testDispatcher) {
        fakeRepository.shouldThrowOnObserve = true

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_fail")
        advanceUntilIdle()

        assertEquals("trk_fail", viewModel.uiState.value.trackId)
        assertNotNull(viewModel.uiState.value.userErrorMessage)
        assertEquals("Simulated failure observing personalization", viewModel.uiState.value.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun action_with_explicit_track_id_affects_targeted_track() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_active")
        advanceUntilIdle()

        // Mutate a different track explicitly
        viewModel.setFavorite(isFavorite = true, trackId = "trk_other")
        viewModel.setRating(rating = 5, trackId = "trk_other")
        advanceUntilIdle()

        // Active track remains unaffected
        assertEquals("trk_active", viewModel.uiState.value.trackId)
        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.rating)

        // Other track was updated in repository
        val other = fakeRepository.getPersonalization("trk_other")
        assertTrue(other.isFavorite)
        assertEquals(5, other.rating)

        collectJob.cancel()
    }

    @Test
    fun action_when_no_track_selected_sets_error() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.setFavorite(true)
        advanceUntilIdle()

        assertEquals("No track currently selected", viewModel.uiState.value.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun lifecycle_cancellation_propagates_cleanly() = runTest(testDispatcher) {
        fakeRepository.shouldThrowCancellation = true

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.bindTrack("trk_cancel")
        advanceUntilIdle()

        // Mutate track which triggers CancellationException
        viewModel.setFavorite(true)
        advanceUntilIdle()

        // Error message should NOT be set because CancellationException must be rethrown, not swallowed
        assertNull(viewModel.uiState.value.userErrorMessage)

        collectJob.cancel()
    }

    @Test
    fun observe_favorite_tracks_exposes_updates_reactively() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.favoriteTracks.collect()
        }
        advanceUntilIdle()
        assertTrue(viewModel.favoriteTracks.value.isEmpty())

        val t1 = Track(id = "fav_1", title = "Paranoid Android", artistName = "Radiohead", durationSec = 180.0)
        val t2 = Track(id = "fav_2", title = "Karma Police", artistName = "Radiohead", durationSec = 180.0)
        fakeRepository.favoriteTracksFlow.value = listOf(t1, t2)
        advanceUntilIdle()

        assertEquals(2, viewModel.favoriteTracks.value.size)
        assertEquals("fav_1", viewModel.favoriteTracks.value[0].id)
        assertEquals("fav_2", viewModel.favoriteTracks.value[1].id)

        collectJob.cancel()
    }

    @Test
    fun observe_rated_tracks_exposes_updates_reactively() = runTest(testDispatcher) {
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.ratedTracks.collect()
        }
        advanceUntilIdle()
        assertTrue(viewModel.ratedTracks.value.isEmpty())

        val t1 = Track(id = "rate_1", title = "Subterranean", artistName = "Radiohead", durationSec = 180.0)
        val r1 = RatedTrack(track = t1, rating = 5, ratedAt = 1000L)
        fakeRepository.ratedTracksFlow.value = listOf(r1)
        advanceUntilIdle()

        assertEquals(1, viewModel.ratedTracks.value.size)
        assertEquals(5, viewModel.ratedTracks.value[0].rating)
        assertEquals("rate_1", viewModel.ratedTracks.value[0].track.id)

        collectJob.cancel()
    }

    @Test
    fun rapid_track_switching_cancels_previous_observation_without_leak() = runTest(testDispatcher) {
        fakeRepository.setFavorite("trk_fast_1", true)
        fakeRepository.setRating("trk_fast_1", 5)

        fakeRepository.setFavorite("trk_fast_2", false)
        fakeRepository.setRating("trk_fast_2", 2)

        fakeRepository.setFavorite("trk_fast_3", true)
        fakeRepository.setRating("trk_fast_3", null)

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        // Switch through multiple tracks in rapid succession
        viewModel.bindTrack("trk_fast_1")
        viewModel.bindTrack("trk_fast_2")
        viewModel.bindTrack("trk_fast_3")
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertEquals("trk_fast_3", finalState.trackId)
        assertTrue(finalState.isFavorite)
        assertNull(finalState.rating)

        collectJob.cancel()
    }
}

/**
 * Fake in-memory PersonalizationRepository for ViewModel testing.
 */
class FakePersonalizationRepository : PersonalizationRepository {

    private val data = mutableMapOf<String, TrackPersonalization>()
    private val dataFlow = MutableStateFlow<Map<String, TrackPersonalization>>(emptyMap())

    var shouldThrowOnFavorite = false
    var shouldThrowOnRating = false
    var shouldThrowOnObserve = false
    var shouldThrowCancellation = false

    override fun observePersonalization(trackId: String): Flow<TrackPersonalization> {
        if (shouldThrowOnObserve) {
            return flow { throw RuntimeException("Simulated failure observing personalization") }
        }
        return dataFlow.map { map ->
            map[trackId] ?: TrackPersonalization(trackId = trackId, isFavorite = false, rating = null)
        }
    }

    override fun observeFavorite(trackId: String): Flow<Boolean> {
        return observePersonalization(trackId).map { it.isFavorite }
    }

    override fun observeRating(trackId: String): Flow<Int?> {
        return observePersonalization(trackId).map { it.rating }
    }

    val favoriteTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    val ratedTracksFlow = MutableStateFlow<List<RatedTrack>>(emptyList())

    override fun observeFavoriteTracks(): Flow<List<Track>> = favoriteTracksFlow

    override fun observeRatedTracks(): Flow<List<RatedTrack>> = ratedTracksFlow

    override suspend fun getPersonalization(trackId: String): TrackPersonalization {
        return data[trackId] ?: TrackPersonalization(trackId = trackId, isFavorite = false, rating = null)
    }

    override suspend fun setFavorite(trackId: String, isFavorite: Boolean) {
        if (shouldThrowCancellation) throw CancellationException("Coroutine cancelled")
        if (shouldThrowOnFavorite) throw RuntimeException("Simulated repository failure on favorite")
        val current = getPersonalization(trackId)
        val updated = current.copy(isFavorite = isFavorite, updatedAt = System.currentTimeMillis())
        data[trackId] = updated
        dataFlow.value = data.toMap()
    }

    override suspend fun toggleFavorite(trackId: String): Boolean {
        val current = getPersonalization(trackId)
        val next = !current.isFavorite
        setFavorite(trackId, next)
        return next
    }

    override suspend fun setRating(trackId: String, rating: Int?) {
        if (shouldThrowCancellation) throw CancellationException("Coroutine cancelled")
        if (shouldThrowOnRating) throw RuntimeException("Simulated repository failure on rating")
        if (rating != null) {
            require(rating in 1..5) { "Rating out of bounds" }
        }
        val current = getPersonalization(trackId)
        val updated = current.copy(rating = rating, updatedAt = System.currentTimeMillis())
        data[trackId] = updated
        dataFlow.value = data.toMap()
    }

    override suspend fun clearPersonalization(trackId: String) {
        data[trackId] = TrackPersonalization(trackId = trackId, isFavorite = false, rating = null)
        dataFlow.value = data.toMap()
    }
}

/**
 * Lightweight fake PlaybackPort for testing active playback track transitions.
 */
class FakePlaybackPortForPersonalization : PlaybackPort {
    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: kotlinx.coroutines.flow.StateFlow<PlaybackState> = _playbackState

    private val _queue = MutableStateFlow<List<org.harmonia.player.domain.model.QueueItem>>(emptyList())
    override val queue: kotlinx.coroutines.flow.StateFlow<List<org.harmonia.player.domain.model.QueueItem>> = _queue

    private val _sleepTimerState = MutableStateFlow(org.harmonia.player.domain.model.audio.SleepTimerState())
    override val sleepTimerState: kotlinx.coroutines.flow.StateFlow<org.harmonia.player.domain.model.audio.SleepTimerState> = _sleepTimerState

    fun setCurrentTrackId(trackId: String?) {
        _playbackState.value = _playbackState.value.copy(currentTrackId = trackId)
    }

    override fun play() {}
    override fun pause() {}
    override fun stop() {}
    override fun seekTo(positionMs: Long) {}
    override fun next() {}
    override fun previous() {}
    override fun setQueue(tracks: List<Track>, startIndex: Int) {}
    override fun appendToQueue(tracks: List<Track>) {}
    override fun jumpToQueueIndex(index: Int) {}
    override fun setShuffleMode(enabled: Boolean) {}
    override fun setRepeatMode(mode: org.harmonia.player.domain.model.RepeatMode) {}
    override fun setVolume(volume: Float) {}
    override fun startSleepTimer(durationMs: Long) {}
    override fun startSleepTimerEndOfTrack() {}
    override fun cancelSleepTimer() {}
}
