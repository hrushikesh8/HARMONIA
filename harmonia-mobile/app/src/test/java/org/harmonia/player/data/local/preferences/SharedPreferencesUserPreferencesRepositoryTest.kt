package org.harmonia.player.data.local.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.harmonia.player.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedPreferencesUserPreferencesRepositoryTest {

    private lateinit var fakePrefs: TestSharedPreferences
    private lateinit var repo: SharedPreferencesUserPreferencesRepository

    @Before
    fun setUp() {
        fakePrefs = TestSharedPreferences()
        repo = SharedPreferencesUserPreferencesRepository(fakePrefs)
    }

    @Test
    fun defaultDarkThemeIsTrue() {
        assertTrue("Default theme must be dark", repo.isDarkTheme())
    }

    @Test
    fun setIsDarkThemePersistsValue() {
        repo.setIsDarkTheme(false)
        assertFalse(repo.isDarkTheme())
        assertFalse(fakePrefs.getBoolean(SharedPreferencesUserPreferencesRepository.KEY_DARK_THEME, true))

        repo.setIsDarkTheme(true)
        assertTrue(repo.isDarkTheme())
        assertTrue(fakePrefs.getBoolean(SharedPreferencesUserPreferencesRepository.KEY_DARK_THEME, false))
    }

    @Test
    fun observeIsDarkThemeEmitsInitialAndUpdates() = runTest {
        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            repo.observeIsDarkTheme().collect { emissions.add(it) }
        }

        assertEquals(listOf(true), emissions)

        repo.setIsDarkTheme(false)
        assertEquals(listOf(true, false), emissions)

        repo.setIsDarkTheme(true)
        assertEquals(listOf(true, false, true), emissions)

        job.cancel()
    }

    @Test
    fun defaultShuffleModeIsFalse() {
        assertFalse(repo.isDefaultShuffleEnabled())
    }

    @Test
    fun setDefaultShufflePersistsAndObserves() = runTest {
        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            repo.observeDefaultShuffleEnabled().collect { emissions.add(it) }
        }

        assertEquals(listOf(false), emissions)

        repo.setDefaultShuffleEnabled(true)
        assertEquals(listOf(false, true), emissions)
        assertTrue(repo.isDefaultShuffleEnabled())

        job.cancel()
    }

    @Test
    fun defaultRepeatModeIsOff() {
        assertEquals(RepeatMode.OFF, repo.getDefaultRepeatMode())
    }

    @Test
    fun setDefaultRepeatModeCyclesAndObserves() = runTest {
        val emissions = mutableListOf<RepeatMode>()
        val job = launch(UnconfinedTestDispatcher()) {
            repo.observeDefaultRepeatMode().collect { emissions.add(it) }
        }

        assertEquals(listOf(RepeatMode.OFF), emissions)

        repo.setDefaultRepeatMode(RepeatMode.ALL)
        repo.setDefaultRepeatMode(RepeatMode.ONE)
        repo.setDefaultRepeatMode(RepeatMode.OFF)

        assertEquals(listOf(RepeatMode.OFF, RepeatMode.ALL, RepeatMode.ONE, RepeatMode.OFF), emissions)

        job.cancel()
    }

    @Test
    fun keepScreenOnDefaultsToFalseAndPersists() = runTest {
        assertFalse(repo.isKeepScreenOn())

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            repo.observeKeepScreenOn().collect { emissions.add(it) }
        }

        assertEquals(listOf(false), emissions)

        repo.setKeepScreenOn(true)
        assertTrue(repo.isKeepScreenOn())
        assertEquals(listOf(false, true), emissions)

        job.cancel()
    }
}

private class TestSharedPreferences : SharedPreferences {
    private val storage = mutableMapOf<String, Any>()
    val registeredListeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = storage[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (storage[key] as? MutableSet<String> ?: defValues)
    override fun getInt(key: String?, defValue: Int): Int = storage[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = storage[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = storage[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = storage.containsKey(key)
    override fun edit(): SharedPreferences.Editor = TestEditor(storage, registeredListeners)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) registeredListeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) registeredListeners.remove(listener)
    }

    private class TestEditor(
        private val storage: MutableMap<String, Any>,
        private val listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>
    ) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null && value != null) pending[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null && values != null) pending[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removals.add(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) {
                storage.clear()
                listeners.toList().forEach { it.onSharedPreferenceChanged(null, null) }
            }
            removals.forEach { storage.remove(it) }
            storage.putAll(pending)
            val updatedKeys = pending.keys + removals
            updatedKeys.forEach { key ->
                listeners.toList().forEach { it.onSharedPreferenceChanged(null, key) }
            }
        }
    }
}
