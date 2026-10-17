package org.harmonia.player.data.local.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedPreferencesEqualizerUserIntentSourceTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var intentSource: SharedPreferencesEqualizerUserIntentSource

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        intentSource = SharedPreferencesEqualizerUserIntentSource(fakePrefs)
    }

    @Test
    fun defaultStateIsStrictlyFalse() {
        // Preference key is not yet set
        assertFalse("Default equalizer enabled state must be strictly false", intentSource.isEqualizerEnabled())
    }

    @Test
    fun setEqualizerEnabledPersistsTrue() {
        intentSource.setEqualizerEnabled(true)
        assertTrue(intentSource.isEqualizerEnabled())
        assertTrue(fakePrefs.getBoolean(SharedPreferencesEqualizerUserIntentSource.KEY_EQUALIZER_ENABLED, false))
    }

    @Test
    fun setEqualizerEnabledPersistsFalse() {
        intentSource.setEqualizerEnabled(true)
        assertTrue(intentSource.isEqualizerEnabled())

        intentSource.setEqualizerEnabled(false)
        assertFalse(intentSource.isEqualizerEnabled())
        assertFalse(fakePrefs.getBoolean(SharedPreferencesEqualizerUserIntentSource.KEY_EQUALIZER_ENABLED, true))
    }

    @Test
    fun separateInstancesShareStateViaPreferences() {
        val firstInstance = SharedPreferencesEqualizerUserIntentSource(fakePrefs)
        val secondInstance = SharedPreferencesEqualizerUserIntentSource(fakePrefs)

        assertFalse(firstInstance.isEqualizerEnabled())
        assertFalse(secondInstance.isEqualizerEnabled())

        firstInstance.setEqualizerEnabled(true)
        assertTrue(secondInstance.isEqualizerEnabled())

        secondInstance.setEqualizerEnabled(false)
        assertFalse(firstInstance.isEqualizerEnabled())
    }

    @Test
    fun observeEqualizerEnabledEmitsInitialValueAndUpdates() = runTest {
        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            intentSource.observeEqualizerEnabled().collect { emissions.add(it) }
        }

        assertEquals(listOf(false), emissions)

        intentSource.setEqualizerEnabled(true)
        assertEquals(listOf(false, true), emissions)

        intentSource.setEqualizerEnabled(false)
        assertEquals(listOf(false, true, false), emissions)

        job.cancel()
    }

    @Test
    fun observeEqualizerEnabledCleansUpListenerOnCancellation() = runTest {
        assertEquals(0, fakePrefs.registeredListeners.size)

        val job = launch(UnconfinedTestDispatcher()) {
            intentSource.observeEqualizerEnabled().collect {}
        }

        assertEquals(1, fakePrefs.registeredListeners.size)

        job.cancel()

        assertEquals(0, fakePrefs.registeredListeners.size)
    }

    @Test
    fun unrelatedKeysDoNotProduceEqualizerEmissions() = runTest {
        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            intentSource.observeEqualizerEnabled().collect { emissions.add(it) }
        }

        assertEquals(listOf(false), emissions)

        // Mutate an unrelated preference key
        fakePrefs.edit().putString("unrelated_theme_mode", "DARK").apply()

        // Emissions must remain unchanged
        assertEquals("Unrelated preference keys must not trigger equalizer emissions", listOf(false), emissions)

        job.cancel()
    }

    @Test
    fun preferenceClearEmitsDefaultFalseWithoutMissedUpdate() = runTest {
        intentSource.setEqualizerEnabled(true)
        assertTrue(intentSource.isEqualizerEnabled())

        val emissions = mutableListOf<Boolean>()
        val job = launch(UnconfinedTestDispatcher()) {
            intentSource.observeEqualizerEnabled().collect { emissions.add(it) }
        }

        assertEquals(listOf(true), emissions)

        // Clear preferences (passes key = null in Android contract)
        fakePrefs.edit().clear().apply()

        assertEquals(listOf(true, false), emissions)
        assertFalse(intentSource.isEqualizerEnabled())

        job.cancel()
    }
}

private class FakeSharedPreferences : SharedPreferences {
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
    override fun edit(): SharedPreferences.Editor = FakeEditor(storage, registeredListeners)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) registeredListeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) registeredListeners.remove(listener)
    }

    private class FakeEditor(
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
