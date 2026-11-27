package org.harmonia.player.service

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * Shuffle order that respects "Play next" and "Add to queue" while shuffle is on.
 *
 * ExoPlayer's default shuffle order puts every newly added song at a random place, so with shuffle
 * on, "Play next" could play much later. This order keeps a random order for the queue you start,
 * but places songs inserted straight after the current song right after it in the play order
 * ("Play next"), and songs appended to the end of the queue at the end of the play order
 * ("Add to queue"). Anything else is placed randomly, like the default.
 *
 * [currentIndex] returns the player's current item index (before the insertion), or
 * [C.INDEX_UNSET] when unknown.
 */
@OptIn(UnstableApi::class)
class PlayNextShuffleOrder private constructor(
    private val order: IntArray,
    private val random: Random,
    private val currentIndex: () -> Int
) : ShuffleOrder {

    /** position in [order] of each item index. */
    private val positionOf = IntArray(order.size).also { positions ->
        order.forEachIndexed { position, item -> positions[item] = position }
    }

    constructor(length: Int, random: Random = Random.Default, currentIndex: () -> Int) :
        this(shuffledIndices(0, length, random), random, currentIndex)

    /** Item indices in the order they will play; for tests. */
    internal fun playOrder(): List<Int> = order.toList()

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int {
        val next = positionOf[index] + 1
        return if (next < order.size) order[next] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val previous = positionOf[index] - 1
        return if (previous >= 0) order[previous] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order.last()

    override fun getFirstIndex(): Int = if (order.isEmpty()) C.INDEX_UNSET else order.first()

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        if (insertionCount <= 0) return this
        if (order.isEmpty()) {
            return PlayNextShuffleOrder(shuffledIndices(insertionIndex, insertionCount, random), random, currentIndex)
        }
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }.toMutableList()
        val added = (insertionIndex until insertionIndex + insertionCount).toList()
        val current = currentIndex()
        when {
            // "Play next": inserted right after the current song (its index is below the insertion
            // point, so it did not shift).
            current in order.indices && insertionIndex == current + 1 ->
                shifted.addAll(shifted.indexOf(current) + 1, added)
            // "Add to queue": appended to the queue, so it plays after everything already queued.
            insertionIndex == order.size -> shifted.addAll(added)
            else -> added.forEach { shifted.add(random.nextInt(shifted.size + 1), it) }
        }
        return PlayNextShuffleOrder(shifted.toIntArray(), random, currentIndex)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexToExclusive - indexFrom
        if (removed <= 0) return this
        val kept = order
            .filter { it < indexFrom || it >= indexToExclusive }
            .map { if (it >= indexToExclusive) it - removed else it }
        return PlayNextShuffleOrder(kept.toIntArray(), random, currentIndex)
    }

    /** Reordering the queue keeps the play order of songs; only their indices change. */
    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        if (indexFrom == newIndexFrom || indexToExclusive <= indexFrom) return this
        val items = order.indices.toMutableList()
        val moved = items.subList(indexFrom, indexToExclusive).toList()
        items.subList(indexFrom, indexToExclusive).clear()
        items.addAll(newIndexFrom, moved)
        val newIndexOf = IntArray(items.size).also { map -> items.forEachIndexed { newIndex, old -> map[old] = newIndex } }
        return PlayNextShuffleOrder(order.map { newIndexOf[it] }.toIntArray(), random, currentIndex)
    }

    override fun cloneAndClear(): ShuffleOrder = PlayNextShuffleOrder(IntArray(0), random, currentIndex)

    private companion object {
        fun shuffledIndices(start: Int, count: Int, random: Random): IntArray =
            (start until start + count).shuffled(random).toIntArray()
    }
}
