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

package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.R;

import app.lawnchair.theme.ThemeProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A column of section letters (A, C, D...) along the edge of the app directory. Touching or
 * dragging over a letter jumps the list to that section.
 */
public class DirectoryIndexView extends View {

    /** Called when the user picks a section. */
    public interface OnSectionSelectedListener {
        /** @param adapterPosition adapter position of the first app of the section */
        void onSectionSelected(int adapterPosition);
    }

    private static final float TEXT_SIZE_SP = 10f;
    private static final float MIN_TEXT_SIZE_SP = 7f;
    private static final float BALLOON_DP = 56f;
    private static final float BALLOON_GAP_DP = 12f;
    private static final float BALLOON_TEXT_SP = 30f;

    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBalloonTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTmpRect = new RectF();
    private final List<String> mLetters = new ArrayList<>();
    private final List<Integer> mPositions = new ArrayList<>();
    private final int mPhosphor;

    private int mActiveIndex = -1;
    @Nullable private OnSectionSelectedListener mListener;

    public DirectoryIndexView(Context context) {
        this(context, null);
    }

    public DirectoryIndexView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPhosphor = ThemeProvider.INSTANCE.get(context).getPhosphorColor();
        mTextPaint.setTypeface(Typeface.MONOSPACE);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(ColorUtils.setAlphaComponent(mPhosphor, 0xB0));
        mActivePaint.setColor(mPhosphor);
        mBalloonTextPaint.setTypeface(Typeface.MONOSPACE);
        mBalloonTextPaint.setTextAlign(Paint.Align.CENTER);
        mBalloonTextPaint.setFakeBoldText(true);
        setContentDescription(context.getString(R.string.directory_index_description));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // The letter balloon is drawn to the left of the strip, outside its bounds.
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).setClipChildren(false);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        // Screen readers cannot drag along the strip: next and previous letter stand in for it.
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, @Nullable android.os.Bundle arguments) {
        int step = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1
                : action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ? -1 : 0;
        if (step == 0 || mLetters.isEmpty()) return super.performAccessibilityAction(action, arguments);
        int index = Math.max(0, Math.min(mLetters.size() - 1, (mActiveIndex < 0 ? 0 : mActiveIndex + step)));
        selectIndex(index);
        setStateDescription(mLetters.get(index));
        return true;
    }

    private void selectIndex(int index) {
        if (index == mActiveIndex) return;
        mActiveIndex = index;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        if (mListener != null) mListener.onSectionSelected(mPositions.get(index));
        invalidate();
    }

    public void setOnSectionSelectedListener(@Nullable OnSectionSelectedListener listener) {
        mListener = listener;
    }

    /**
     * Sets the sections to show. Repeated section names (the list scroller adds a duplicate at the
     * end) are folded into the first one.
     */
    public void setSections(List<AlphabeticalAppsList.FastScrollSectionInfo> sections) {
        mLetters.clear();
        mPositions.clear();
        for (AlphabeticalAppsList.FastScrollSectionInfo info : sections) {
            String name = String.valueOf(info.sectionName);
            if (name.isEmpty() || mLetters.contains(name)) continue;
            mLetters.add(name);
            mPositions.add(info.position);
        }
        mActiveIndex = -1;
        setVisibility(mLetters.size() > 1 ? VISIBLE : INVISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int count = mLetters.size();
        if (count == 0) return;
        float density = getResources().getDisplayMetrics().density;
        float cell = (float) getHeight() / count;
        float size = Math.max(MIN_TEXT_SIZE_SP * density,
                Math.min(TEXT_SIZE_SP * density, cell * 0.8f));
        mTextPaint.setTextSize(size);
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        float cx = getWidth() / 2f;
        for (int i = 0; i < count; i++) {
            float top = i * cell;
            float baseline = top + cell / 2f - (fm.ascent + fm.descent) / 2f;
            if (i == mActiveIndex) {
                mTmpRect.set(0, top, getWidth(), top + cell);
                canvas.drawRect(mTmpRect, mActivePaint);
                mTextPaint.setColor(app.lawnchair.theme.LauncherGround.INSTANCE.onAccent(getContext(), mPhosphor));
                canvas.drawText(mLetters.get(i), cx, baseline, mTextPaint);
                mTextPaint.setColor(ColorUtils.setAlphaComponent(mPhosphor, 0xB0));
            } else {
                canvas.drawText(mLetters.get(i), cx, baseline, mTextPaint);
            }
        }
        if (mActiveIndex >= 0 && mActiveIndex < count) drawBalloon(canvas, mActiveIndex, cell, density);
    }

    /** The active letter, large, beside the strip: the finger covers the strip's own letter. */
    private void drawBalloon(Canvas canvas, int index, float cell, float density) {
        float size = BALLOON_DP * density;
        float cy = Math.max(size / 2f, Math.min(getHeight() - size / 2f, index * cell + cell / 2f));
        float right = -BALLOON_GAP_DP * density;
        mTmpRect.set(right - size, cy - size / 2f, right, cy + size / 2f);
        canvas.drawRoundRect(mTmpRect, 4 * density, 4 * density, mActivePaint);
        mBalloonTextPaint.setTextSize(BALLOON_TEXT_SP * density);
        mBalloonTextPaint.setColor(app.lawnchair.theme.LauncherGround.INSTANCE.onAccent(getContext(), mPhosphor));
        Paint.FontMetrics fm = mBalloonTextPaint.getFontMetrics();
        canvas.drawText(mLetters.get(index), mTmpRect.centerX(),
                mTmpRect.centerY() - (fm.ascent + fm.descent) / 2f, mBalloonTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int count = mLetters.size();
        if (count == 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                getParent().requestDisallowInterceptTouchEvent(true);
                int index = (int) (event.getY() / getHeight() * count);
                selectIndex(Math.max(0, Math.min(count - 1, index)));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mActiveIndex = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }
}
