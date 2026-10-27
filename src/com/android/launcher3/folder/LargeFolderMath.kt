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
     * The apps one page of a large folder shows: [start] until [end] (exclusive) of [pages] pages,
     * with [page] the one being shown.
     */
    data class Page(val page: Int, val pages: Int, val start: Int, val end: Int)

    /** The [grid] that results from slots of [slot] pixels. */
    data class Fit(val grid: Grid, val slot: Int)

    data class Size(val spanX: Int, val spanY: Int)

    data class Origin(val x: Int, val y: Int)

    /** Keeps a span read from storage inside the supported range. */
    @JvmStatic
    fun clampSpan(span: Int): Int = span.coerceIn(1, MAX_SPAN)

    @JvmStatic
    fun isLarge(spanX: Int, spanY: Int): Boolean = spanX > 1 || spanY > 1

    /** The sizes a folder can have, in the order the size chooser lists them: small, medium, large. */
    @JvmField
    val PRESETS: List<Size> = listOf(Size(1, 1), Size(2, 2), Size(4, 2))

    /**
     * The index in [PRESETS] of the size a folder has now. A span that is not a preset (for example
     * from an older version) maps to the smallest preset that covers its area, so the chooser never
     * marks a size smaller than the folder really is.
     */
    @JvmStatic
    fun presetIndex(spanX: Int, spanY: Int): Int {
        val exact = PRESETS.indexOf(Size(spanX, spanY))
        if (exact >= 0) return exact
        val area = spanX * spanY
        val covering = PRESETS.indexOfFirst { it.spanX * it.spanY >= area }
        return if (covering >= 0) covering else PRESETS.lastIndex
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
     * Splits [totalApps] into pages of [capacity] apps and returns the one asked for, with [page]
     * kept inside the range that exists (a folder that lost apps may have fewer pages than before).
     * A large folder never opens the regular folder: what does not fit goes on the next page.
     */
    @JvmStatic
    fun page(totalApps: Int, capacity: Int, page: Int): Page {
        val total = totalApps.coerceAtLeast(0)
        val size = capacity.coerceAtLeast(1)
        val pages = ((total + size - 1) / size).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        return Page(current, pages, current * size, minOf(total, (current + 1) * size))
    }

    /** The page after [page], wrapping around to the first. */
    @JvmStatic
    fun nextPage(page: Int, pages: Int): Int = if (pages <= 1) 0 else (page + 1) % pages

    /** The page before [page], wrapping around to the last. */
    @JvmStatic
    fun previousPage(page: Int, pages: Int): Int = if (pages <= 1) 0 else (page - 1 + pages) % pages

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

    /**
     * Every origin where a folder of [spanX] x [spanY] cells fits in the grid, the closest to
     * [cellX], [cellY] first, so a folder that cannot grow in place moves to the nearest free room.
     */
    @JvmStatic
    fun candidateOrigins(
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
        countX: Int,
        countY: Int,
    ): List<Origin> {
        val all = mutableListOf<Origin>()
        for (y in 0..(countY - spanY)) {
            for (x in 0..(countX - spanX)) all.add(Origin(x, y))
        }
        return all.sortedBy { Math.abs(it.x - cellX) + Math.abs(it.y - cellY) }
    }
}
