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
import com.android.launcher3.folder.LargeFolderMath.Plan
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

    // ---- resize button cycle ----

    @Test
    fun nextSize_goesFromSmallToTwoByTwo() {
        assertEquals(Size(2, 2), LargeFolderMath.nextSize(1, 1))
    }

    @Test
    fun nextSize_goesFromTwoByTwoToFourByTwo() {
        assertEquals(Size(4, 2), LargeFolderMath.nextSize(2, 2))
    }

    @Test
    fun nextSize_goesFromFourByTwoBackToSmall() {
        assertEquals(Size(1, 1), LargeFolderMath.nextSize(4, 2))
    }

    @Test
    fun nextSize_cyclesBackToTheStartAfterThreeSteps() {
        var size = Size(1, 1)
        repeat(3) { size = LargeFolderMath.nextSize(size.spanX, size.spanY) }
        assertEquals(Size(1, 1), size)
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
        assertFalse(LargeFolderMath.plan(6, fit.grid).showOverflowMarker)
    }

    @Test
    fun fit_neverShrinksBelowTheMinimumSlot() {
        val fit = LargeFolderMath.fit(474, 387, 24, 72, 156, 94, totalApps = 40)
        assertEquals(94, fit.slot)
        // Too many apps even at the smallest size: the plan reports the overflow.
        assertTrue(LargeFolderMath.plan(40, fit.grid).showOverflowMarker)
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

    // ---- what to draw ----

    @Test
    fun plan_showsEveryAppWhenTheyFit() {
        assertEquals(Plan(6, 0, false), LargeFolderMath.plan(6, Grid(3, 2)))
        assertEquals(Plan(2, 0, false), LargeFolderMath.plan(2, Grid(3, 2)))
    }

    @Test
    fun plan_withNoApps_drawsNothing() {
        assertEquals(Plan(0, 0, false), LargeFolderMath.plan(0, Grid(3, 2)))
        assertEquals(Plan(0, 0, false), LargeFolderMath.plan(-2, Grid(3, 2)))
    }

    @Test
    fun plan_whenAppsOverflow_reservesTheLastSlotForTheCounter() {
        // 8 apps in 6 slots: 5 icons plus a "+3" marker.
        assertEquals(Plan(5, 3, true), LargeFolderMath.plan(8, Grid(3, 2)))
    }

    @Test
    fun plan_whenExactlyOneAppOverflows_stillShowsTheMarker() {
        assertEquals(Plan(5, 2, true), LargeFolderMath.plan(7, Grid(3, 2)))
    }

    @Test
    fun plan_withASingleSlot_showsAnAppInsteadOfACounter() {
        // The failure seen on device: with one slot and three apps nothing was drawn but "+2".
        val plan = LargeFolderMath.plan(3, Grid(1, 1))
        assertEquals(1, plan.shown)
        assertFalse(plan.showOverflowMarker)
    }

    @Test
    fun plan_neverDrawsMoreThanTheGridHolds() {
        for (total in 0..40) {
            val grid = Grid(3, 2)
            val plan = LargeFolderMath.plan(total, grid)
            val slotsUsed = plan.shown + if (plan.showOverflowMarker) 1 else 0
            assertTrue("total=$total", slotsUsed <= grid.capacity)
            assertEquals("total=$total", total, plan.shown + plan.hidden)
        }
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
}
