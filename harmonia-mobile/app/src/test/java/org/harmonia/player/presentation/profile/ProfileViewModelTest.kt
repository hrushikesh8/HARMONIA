package org.harmonia.player.presentation.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.PersonalizationRepository
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trackRepo: FakeProfileTrackRepository
    private lateinit var persRepo: FakeProfilePersonalizationRepository
    private lateinit var sourceRepo: FakeProfileStorageSourceRepository
    private lateinit var listeningRepo: FakeProfileListeningEventRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trackRepo = FakeProfileTrackRepository()
        persRepo = FakeProfilePersonalizationRepository()
        sourceRepo = FakeProfileStorageSourceRepository()
        listeningRepo = FakeProfileListeningEventRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileViewModel {
        return ProfileViewModel(
            trackRepository = trackRepo,
            personalizationRepository = persRepo,
            storageSourceRepository = sourceRepo,
            listeningEventRepository = listeningRepo
        )
    }

    private fun TestScope.subscribeUiState(viewModel: ProfileViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun initialProfileUiStateReflectsRepositories() = runTest(testDispatcher) {
        val tracks = listOf(
            Track(
                id = "trk1",
                title = "Track One",
                durationSec = 180.0,
                localFile = LocalFile(
                    id = "lf1",
                    trackId = "trk1",
                    sourceId = "src1",
                    storageType = StorageType.MEDIA_STORE_PRIMARY,
                    fileUri = "content://media/1",
                    fileSize = 1048576L,
                    fileHash = "h1",
                    format = "mp3"
                )
            ),
            Track(
                id = "trk2",
                title = "Track Two",
                durationSec = 240.0,
                localFile = LocalFile(
                    id = "lf2",
                    trackId = "trk2",
                    sourceId = "src1",
                    storageType = StorageType.MEDIA_STORE_PRIMARY,
                    fileUri = "content://media/2",
                    fileSize = 2097152L,
                    fileHash = "h2",
                    format = "mp3"
                )
            )
        )
        trackRepo.allTracksFlow.value = tracks
        trackRepo.offlineTracksFlow.value = tracks
        persRepo.favsFlow.value = listOf(tracks[0])
        persRepo.ratedFlow.value = listOf(RatedTrack(tracks[0], 5, 1000L), RatedTrack(tracks[1], 3, 2000L))
        sourceRepo.sourcesFlow.value = listOf(StorageSource.MediaStorePrimary())
        listeningRepo.eventsFlow.value = listOf(
            ListeningEvent(eventType = ListeningEventType.STARTED, trackId = "trk1"),
            ListeningEvent(eventType = ListeningEventType.COMPLETED, trackId = "trk1")
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.totalTracks)
        assertEquals(2, state.offlineTracks)
        assertEquals(1, state.favoritesCount)
        assertEquals(2, state.ratedTracksCount)
        assertEquals(1, state.storageSourcesCount)
        assertEquals(2, state.totalListeningEvents)
        assertEquals(2, state.recentListeningEvents.size)
        assertNotNull(state.averageRating)
        assertEquals(4.0, state.averageRating!!, 0.01)
        assertEquals(3.0, state.storageUsageEstimateMb, 0.01)
    }

    @Test
    fun dynamicUpdatesAreReflectedReactively() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.totalTracks)
        assertEquals(0, viewModel.uiState.value.favoritesCount)

        val track = Track(id = "trk_new", title = "Fresh Audio", durationSec = 200.0)
        trackRepo.allTracksFlow.value = listOf(track)
        persRepo.favsFlow.value = listOf(track)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.totalTracks)
        assertEquals(1, viewModel.uiState.value.favoritesCount)
    }
}

private class FakeProfileTrackRepository : TrackRepository {
    val allTracksFlow = MutableStateFlow<List<Track>>(emptyList())
    val offlineTracksFlow = MutableStateFlow<List<Track>>(emptyList())

    override fun observeAllTracks(): Flow<List<Track>> = allTracksFlow
    override fun observeOfflineTracks(): Flow<List<Track>> = offlineTracksFlow
    override suspend fun getTrackById(id: String): Track? = allTracksFlow.value.find { it.id == id }
    override fun searchTracks(query: String): Flow<List<Track>> = allTracksFlow
    override suspend fun upsertTracks(tracks: List<Track>) { allTracksFlow.value = tracks }
    override suspend fun deleteTrack(id: String) {}
}

private class FakeProfilePersonalizationRepository : PersonalizationRepository {
    val favsFlow = MutableStateFlow<List<Track>>(emptyList())
    val ratedFlow = MutableStateFlow<List<RatedTrack>>(emptyList())

    override fun observePersonalization(trackId: String): Flow<TrackPersonalization> = error("Not needed")
    override fun observeFavorite(trackId: String): Flow<Boolean> = error("Not needed")
    override fun observeRating(trackId: String): Flow<Int?> = error("Not needed")
    override fun observeFavoriteTracks(): Flow<List<Track>> = favsFlow
    override fun observeRatedTracks(): Flow<List<RatedTrack>> = ratedFlow
    override suspend fun getPersonalization(trackId: String): TrackPersonalization = error("Not needed")
    override suspend fun setFavorite(trackId: String, isFavorite: Boolean) {}
    override suspend fun toggleFavorite(trackId: String): Boolean = true
    override suspend fun setRating(trackId: String, rating: Int?) {}
    override suspend fun clearPersonalization(trackId: String) {}
}

private class FakeProfileStorageSourceRepository : StorageSourceRepository {
    val sourcesFlow = MutableStateFlow<List<StorageSource>>(emptyList())

    override fun observeAllSources(): Flow<List<StorageSource>> = sourcesFlow
    override fun observeEnabledSources(): Flow<List<StorageSource>> = sourcesFlow
    override suspend fun getSourceById(id: String): StorageSource? = sourcesFlow.value.find { it.id == id }
    override suspend fun saveSource(source: StorageSource) {}
    override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {}
    override suspend fun deleteSource(id: String) {}
}

private class FakeProfileListeningEventRepository : ListeningEventRepository {
    val eventsFlow = MutableStateFlow<List<ListeningEvent>>(emptyList())

    override suspend fun recordEvent(event: ListeningEvent) {}
    override fun observeRecentEvents(limit: Int): Flow<List<ListeningEvent>> = eventsFlow
    override suspend fun getEventCount(): Int = eventsFlow.value.size
}
