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

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

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

    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
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
                index = Math.max(0, Math.min(count - 1, index));
                if (index != mActiveIndex) {
                    mActiveIndex = index;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (mListener != null) {
                        mListener.onSectionSelected(mPositions.get(index));
                    }
                    invalidate();
                }
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
