/*
 * Copyright (C) 2026 The Terminal Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.folder

/**
 * Pure rules for large folders (folders that span several workspace cells). Kept free of Android
 * types so they can be unit tested without a device.
 */
object LargeFolderMath {

    /** Largest span, in cells, a folder may take on either axis. */
    const val MAX_SPAN = 4

    /** A grid of app slots inside a large folder. */
    data class Grid(val columns: Int, val rows: Int) {
        val capacity: Int get() = columns * rows
    }

    /**
     * What to draw for [totalApps] apps in a [Grid].
     *
     * @property shown how many app icons are drawn
     * @property hidden how many apps do not fit
     * @property showOverflowMarker whether the last slot shows "+N" in place of an app
     */
    data class Plan(val shown: Int, val hidden: Int, val showOverflowMarker: Boolean)

    /** The [grid] that results from slots of [slot] pixels. */
    data class Fit(val grid: Grid, val slot: Int)

    data class Size(val spanX: Int, val spanY: Int)

    data class Origin(val x: Int, val y: Int)

    /** Keeps a span read from storage inside the supported range. */
    @JvmStatic
    fun clampSpan(span: Int): Int = span.coerceIn(1, MAX_SPAN)

    @JvmStatic
    fun isLarge(spanX: Int, spanY: Int): Boolean = spanX > 1 || spanY > 1

    /** The size the resize button moves to: small, then 2x2, then 4x2, then small again. */
    @JvmStatic
    fun nextSize(spanX: Int, spanY: Int): Size = when {
        !isLarge(spanX, spanY) -> Size(2, 2)
        spanX < MAX_SPAN -> Size(MAX_SPAN, 2)
        else -> Size(1, 1)
    }

    /**
     * How many square slots of [slot] pixels fit in a folder of [width] x [height] pixels, after
     * [padding] on every side and a [titleHeight] header. There is always at least one slot.
     */
    @JvmStatic
    fun computeGrid(width: Int, height: Int, padding: Int, titleHeight: Int, slot: Int): Grid {
        val size = slot.coerceAtLeast(1)
        val availableWidth = (width - 2 * padding).coerceAtLeast(0)
        val availableHeight = (height - 2 * padding - titleHeight).coerceAtLeast(0)
        return Grid(
            columns = (availableWidth / size).coerceAtLeast(1),
            rows = (availableHeight / size).coerceAtLeast(1),
        )
    }

    /**
     * Picks the largest slot, from [naturalSlot] down to [minSlot], that lets all [totalApps] apps
     * fit, so a small folder shrinks its icons before it resorts to a "+N" counter. When even
     * [minSlot] is not enough, the grid for [minSlot] is returned and the plan reports the overflow.
     */
    @JvmStatic
    fun fit(
        width: Int,
        height: Int,
        padding: Int,
        titleHeight: Int,
        naturalSlot: Int,
        minSlot: Int,
        totalApps: Int,
    ): Fit {
        val natural = naturalSlot.coerceAtLeast(1)
        val smallest = minSlot.coerceIn(1, natural)
        val step = (natural / 20).coerceAtLeast(1)
        var slot = natural
        while (slot > smallest) {
            val grid = computeGrid(width, height, padding, titleHeight, slot)
            if (grid.capacity >= totalApps) return Fit(grid, slot)
            slot -= step
        }
        return Fit(computeGrid(width, height, padding, titleHeight, smallest), smallest)
    }

    /**
     * Decides how many icons to draw. When the apps do not fit, the last slot becomes a "+N"
     * marker, unless there is a single slot, where an app is more useful than a counter.
     */
    @JvmStatic
    fun plan(totalApps: Int, grid: Grid): Plan {
        val total = totalApps.coerceAtLeast(0)
        val capacity = grid.capacity
        return when {
            total <= capacity -> Plan(shown = total, hidden = 0, showOverflowMarker = false)
            capacity <= 1 -> Plan(shown = capacity, hidden = total - capacity, showOverflowMarker = false)
            else -> Plan(shown = capacity - 1, hidden = total - (capacity - 1), showOverflowMarker = true)
        }
    }

    /**
     * Where a folder of [spanX] x [spanY] cells goes when it grows from [cellX], [cellY] in a grid
     * of [countX] x [countY] cells: the same cell, moved back only as far as needed to fit.
     * Returns null when the grid is too small for that size.
     */
    @JvmStatic
    fun resolveOrigin(cellX: Int, cellY: Int, spanX: Int, spanY: Int, countX: Int, countY: Int): Origin? {
        val x = minOf(cellX, countX - spanX)
        val y = minOf(cellY, countY - spanY)
        return if (x < 0 || y < 0) null else Origin(x, y)
    }
}
