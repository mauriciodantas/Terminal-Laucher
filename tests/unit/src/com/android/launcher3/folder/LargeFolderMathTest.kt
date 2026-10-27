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

import com.android.launcher3.folder.LargeFolderMath.Grid
import com.android.launcher3.folder.LargeFolderMath.Origin
import com.android.launcher3.folder.LargeFolderMath.Page
import com.android.launcher3.folder.LargeFolderMath.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFolderMathTest {

    // ---- spans read from storage ----

    @Test
    fun clampSpan_keepsValidValues() {
        assertEquals(1, LargeFolderMath.clampSpan(1))
        assertEquals(2, LargeFolderMath.clampSpan(2))
        assertEquals(4, LargeFolderMath.clampSpan(4))
    }

    @Test
    fun clampSpan_fixesCorruptValues() {
        assertEquals(1, LargeFolderMath.clampSpan(0))
        assertEquals(1, LargeFolderMath.clampSpan(-3))
        assertEquals(4, LargeFolderMath.clampSpan(9))
    }

    @Test
    fun isLarge_isTrueWhenEitherAxisSpansMoreThanOneCell() {
        assertFalse(LargeFolderMath.isLarge(1, 1))
        assertTrue(LargeFolderMath.isLarge(2, 1))
        assertTrue(LargeFolderMath.isLarge(1, 2))
        assertTrue(LargeFolderMath.isLarge(4, 2))
    }

    // ---- size chooser ----

    @Test
    fun presets_areSmallMediumLargeInThatOrder() {
        assertEquals(listOf(Size(1, 1), Size(2, 2), Size(4, 2)), LargeFolderMath.PRESETS)
    }

    @Test
    fun presetIndex_findsTheExactPreset() {
        assertEquals(0, LargeFolderMath.presetIndex(1, 1))
        assertEquals(1, LargeFolderMath.presetIndex(2, 2))
        assertEquals(2, LargeFolderMath.presetIndex(4, 2))
    }

    @Test
    fun presetIndex_matchesAnOddSizeToTheSmallestPresetThatCoversIt() {
        assertEquals(1, LargeFolderMath.presetIndex(2, 1))
        assertEquals(1, LargeFolderMath.presetIndex(1, 2))
        assertEquals(2, LargeFolderMath.presetIndex(3, 2))
        assertEquals(2, LargeFolderMath.presetIndex(4, 4))
    }

    @Test
    fun presetIndex_alwaysReturnsAValidIndex() {
        for (x in 0..6) for (y in 0..6) {
            assertTrue("$x x $y", LargeFolderMath.presetIndex(x, y) in LargeFolderMath.PRESETS.indices)
        }
    }

    // ---- grid of app slots ----

    @Test
    fun computeGrid_countsSlotsAfterPaddingAndTitle() {
        // 400 x 320, padding 20, title 40, slot 100:
        // width 360 -> 3 columns; height 320 - 40 - 40 = 240 -> 2 rows.
        assertEquals(Grid(3, 2), LargeFolderMath.computeGrid(400, 320, 20, 40, 100))
    }

    @Test
    fun computeGrid_neverReturnsAnEmptyGrid() {
        assertEquals(Grid(1, 1), LargeFolderMath.computeGrid(50, 50, 20, 40, 100))
        assertEquals(Grid(1, 1), LargeFolderMath.computeGrid(0, 0, 20, 40, 100))
    }

    @Test
    fun computeGrid_ignoresAZeroSlotSize() {
        val grid = LargeFolderMath.computeGrid(100, 100, 0, 0, 0)
        assertTrue(grid.columns >= 1 && grid.rows >= 1)
    }

    @Test
    fun computeGrid_regression_aCellSizedForTheOpenFolderLeavesOnlyOneSlot() {
        // A 2x2 folder on a 1080 px wide phone is about 474 x 387 px. Sizing slots like the cells
        // of the opened folder (about 280 px) gave a single slot and hid every app behind "+N".
        assertEquals(1, LargeFolderMath.computeGrid(474, 387, 24, 72, 280).capacity)
    }

    // ---- shrinking icons to fit ----

    @Test
    fun fit_keepsTheNaturalSizeWhenEverythingFits() {
        val fit = LargeFolderMath.fit(900, 700, 24, 72, 156, 94, totalApps = 4)
        assertEquals(156, fit.slot)
        assertTrue(fit.grid.capacity >= 4)
    }

    @Test
    fun fit_shrinksIconsToFitAllAppsInATwoByTwoFolder() {
        // The folder from the screenshot (474 x 387 px) with six apps. At full size only two slots
        // fit; the icons must shrink so that all six are visible without a counter.
        val full = LargeFolderMath.computeGrid(474, 387, 24, 72, 156)
        assertEquals(2, full.capacity)

        val fit = LargeFolderMath.fit(474, 387, 24, 72, 156, 94, totalApps = 6)
        assertTrue(fit.slot < 156)
        assertTrue(fit.grid.capacity >= 6)
        assertEquals(1, LargeFolderMath.page(6, fit.grid.capacity, 0).pages)
    }

    @Test
    fun fit_neverShrinksBelowTheMinimumSlot() {
        val fit = LargeFolderMath.fit(474, 387, 24, 72, 156, 94, totalApps = 40)
        assertEquals(94, fit.slot)
        // Too many apps even at the smallest size: the rest goes on more pages.
        assertTrue(LargeFolderMath.page(40, fit.grid.capacity, 0).pages > 1)
    }

    @Test
    fun fit_choosesTheLargestSlotThatFits() {
        val fit = LargeFolderMath.fit(474, 387, 24, 72, 156, 94, totalApps = 6)
        // One step larger would not have fitted all six apps.
        val larger = LargeFolderMath.computeGrid(474, 387, 24, 72, fit.slot + 7)
        assertTrue(larger.capacity < 6)
    }

    @Test
    fun fit_withNoApps_keepsTheNaturalSize() {
        assertEquals(156, LargeFolderMath.fit(474, 387, 24, 72, 156, 94, totalApps = 0).slot)
    }

    @Test
    fun fit_toleratesAMinimumLargerThanTheNaturalSize() {
        val fit = LargeFolderMath.fit(474, 387, 24, 72, 100, 500, totalApps = 3)
        assertTrue(fit.slot in 1..100)
    }

    // ---- pages ----

    @Test
    fun page_showsEveryAppOnOnePageWhenTheyFit() {
        assertEquals(Page(0, 1, 0, 6), LargeFolderMath.page(6, 6, 0))
        assertEquals(Page(0, 1, 0, 2), LargeFolderMath.page(2, 6, 0))
    }

    @Test
    fun page_withNoApps_hasOneEmptyPage() {
        assertEquals(Page(0, 1, 0, 0), LargeFolderMath.page(0, 6, 0))
        assertEquals(Page(0, 1, 0, 0), LargeFolderMath.page(-2, 6, 3))
    }

    @Test
    fun page_splitsOverflowingAppsAcrossPagesWithoutACounter() {
        assertEquals(Page(0, 2, 0, 6), LargeFolderMath.page(8, 6, 0))
        assertEquals(Page(1, 2, 6, 8), LargeFolderMath.page(8, 6, 1))
    }

    @Test
    fun page_keepsThePageInRangeWhenTheFolderShrinks() {
        assertEquals(Page(0, 1, 0, 4), LargeFolderMath.page(4, 6, 2))
        assertEquals(Page(0, 1, 0, 1), LargeFolderMath.page(1, 0, 5))
    }

    @Test
    fun page_coversEveryAppExactlyOnce() {
        for (total in 0..40) {
            val pages = LargeFolderMath.page(total, 6, 0).pages
            var covered = 0
            for (p in 0 until pages) {
                val slice = LargeFolderMath.page(total, 6, p)
                assertTrue("total=$total", slice.end - slice.start <= 6)
                assertEquals("total=$total", covered, slice.start)
                covered = slice.end
            }
            assertEquals("total=$total", total, covered)
        }
    }

    @Test
    fun previousPage_wrapsAroundToTheLast() {
        assertEquals(2, LargeFolderMath.previousPage(0, 3))
        assertEquals(0, LargeFolderMath.previousPage(1, 3))
        assertEquals(0, LargeFolderMath.previousPage(0, 1))
    }

    @Test
    fun nextPage_wrapsAroundToTheFirst() {
        assertEquals(1, LargeFolderMath.nextPage(0, 3))
        assertEquals(0, LargeFolderMath.nextPage(2, 3))
        assertEquals(0, LargeFolderMath.nextPage(0, 1))
    }

    // ---- growing a folder on the workspace grid ----

    @Test
    fun resolveOrigin_keepsTheCellWhenItFits() {
        assertEquals(Origin(1, 1), LargeFolderMath.resolveOrigin(1, 1, 2, 2, 4, 5))
    }

    @Test
    fun resolveOrigin_movesBackOnlyAsFarAsNeeded() {
        // A 2x2 folder in the last column moves one cell left; the row is untouched.
        assertEquals(Origin(2, 1), LargeFolderMath.resolveOrigin(3, 1, 2, 2, 4, 5))
        // A 4x2 folder must start in the first column.
        assertEquals(Origin(0, 3), LargeFolderMath.resolveOrigin(2, 3, 4, 2, 4, 5))
        // And moves up when it would run past the bottom row.
        assertEquals(Origin(0, 3), LargeFolderMath.resolveOrigin(0, 4, 4, 2, 4, 5))
    }

    @Test
    fun resolveOrigin_returnsNullWhenTheGridIsTooSmall() {
        assertNull(LargeFolderMath.resolveOrigin(0, 0, 4, 2, 3, 5))
        assertNull(LargeFolderMath.resolveOrigin(0, 0, 2, 2, 4, 1))
    }

    @Test
    fun candidateOrigins_listsTheNearestFirst() {
        val origins = LargeFolderMath.candidateOrigins(1, 2, 2, 2, 4, 5)
        assertEquals(Origin(1, 2), origins.first())
        assertEquals(3 * 4, origins.size)
        assertTrue(origins.all { it.x in 0..2 && it.y in 0..3 })
    }

    @Test
    fun candidateOrigins_isEmptyWhenTheGridIsTooSmall() {
        assertTrue(LargeFolderMath.candidateOrigins(0, 0, 4, 2, 3, 5).isEmpty())
    }
}
