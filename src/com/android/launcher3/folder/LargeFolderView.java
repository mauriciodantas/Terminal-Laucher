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

package com.android.launcher3.folder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import app.lawnchair.theme.ThemeProvider;
import app.lawnchair.util.LawnchairUtilsKt;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The content of a large folder: a folder that spans several workspace cells and shows its apps
 * directly on the home screen instead of a small preview.
 *
 * Tapping an app launches it. Tapping the panel or the title opens the regular folder, and a long
 * press anywhere drags the whole folder.
 */
public class LargeFolderView extends ViewGroup {

    private static final float CORNER_RADIUS_DP = 6f;
    private static final float STROKE_WIDTH_DP = 1f;
    private static final float PADDING_DP = 8f;
    private static final float TITLE_HEIGHT_DP = 24f;
    /** Icons shrink down to this fraction of their size before a +N counter is used. */
    private static final float MIN_SLOT_FRACTION = 0.6f;

    private final FolderIcon mFolderIcon;
    private final ActivityContext mActivity;
    private final TextView mTitle;
    private final TextView mMore;
    private final List<BubbleTextView> mIcons = new ArrayList<>();
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mPanel = new RectF();

    private final int mPadding;
    private final int mTitleHeight;
    private final float mCornerRadius;

    private final int mTextColor;
    private int mTotalApps;
    private int mColumns = 1;
    private int mRows = 1;
    private float mIconScale = 1f;
    private boolean mHasHiddenApps;
    private final ImageButton mSizeButton;

    public LargeFolderView(Context context, FolderIcon folderIcon, ActivityContext activity) {
        super(context);
        mFolderIcon = folderIcon;
        mActivity = activity;
        setWillNotDraw(false);
        setClipChildren(false);

        float density = context.getResources().getDisplayMetrics().density;
        mPadding = Math.round(PADDING_DP * density);
        mTitleHeight = Math.round(TITLE_HEIGHT_DP * density);
        mCornerRadius = CORNER_RADIUS_DP * density;

        int textColor = ThemeProvider.INSTANCE.get(context).getPhosphorColor();
        mTextColor = textColor;

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(STROKE_WIDTH_DP * density);
        mStrokePaint.setColor(ColorUtils.setAlphaComponent(textColor, 170));
        mFillPaint.setStyle(Paint.Style.FILL);

        mTitle = new TextView(context);
        mTitle.setTextColor(textColor);
        mTitle.setTypeface(Typeface.MONOSPACE);
        mTitle.setSingleLine(true);
        mTitle.setEllipsize(TextUtils.TruncateAt.END);
        mTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                activity.getDeviceProfile().folderLabelTextSizePx);
        mTitle.setLetterSpacing(0.06f);
        addView(mTitle);

        mMore = new TextView(context);
        mMore.setTextColor(textColor);
        mMore.setTypeface(Typeface.MONOSPACE);
        mMore.setGravity(Gravity.CENTER);
        mMore.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                activity.getDeviceProfile().folderLabelTextSizePx);
        addView(mMore);

        // The title opens the folder (to rename it); the button picks its size.
        mTitle.setOnClickListener(view -> mFolderIcon.openFolder());

        mSizeButton = new ImageButton(context);
        mSizeButton.setImageResource(R.drawable.ic_folder_resize);
        mSizeButton.setBackgroundColor(0);
        mSizeButton.setScaleType(ImageView.ScaleType.CENTER);
        mSizeButton.setContentDescription(context.getString(R.string.folder_resize_label));
        mSizeButton.setColorFilter(textColor);
        mSizeButton.setOnClickListener(view -> mFolderIcon.showSizeChooser(null));
        addView(mSizeButton);

        refreshColors();
    }

    /** True when some apps do not fit in the panel, so opening the folder shows more. */
    public boolean hasHiddenApps() {
        return mHasHiddenApps;
    }

    /** Re-reads the folder colors and opacity from the user preferences. */
    public void refreshColors() {
        Context context = getContext();
        int custom = LawnchairUtilsKt.getCustomFolderColor(context);
        int base = custom != 0
                ? custom
                : ColorUtils.blendARGB(app.lawnchair.theme.LauncherGround.INSTANCE.get(context), mTextColor, 0.10f);
        mFillPaint.setColor(ColorUtils.setAlphaComponent(base,
                LawnchairUtilsKt.getFolderBackgroundAlpha(context)));
        invalidate();
    }

    /** Rebuilds the app icons from the folder contents. */
    @SuppressLint("InflateParams")
    public void bind(FolderInfo info) {
        for (BubbleTextView icon : mIcons) {
            removeView(icon);
        }
        mIcons.clear();

        mTitle.setText(info.title == null || info.title.length() == 0
                ? "> " + getContext().getString(R.string.unnamed_folder).toUpperCase()
                : "> " + info.title.toString().toUpperCase());

        List<WorkspaceItemInfo> apps = info.getAppContents();
        mTotalApps = apps.size();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (WorkspaceItemInfo app : apps) {
            BubbleTextView icon = (BubbleTextView) inflater.inflate(
                    R.layout.folder_application, this, false);
            icon.applyFromWorkspaceItem(app);
            // Labels are hidden: drop the text so the icon is centered in its slot.
            icon.setText("");
            icon.setCenterVertically(true);
            icon.setOnClickListener(mActivity.getItemOnClickListener());
            // Dragging an app out of a large folder is not supported: a long press moves the folder.
            icon.setOnLongClickListener(v -> mFolderIcon.performLongClick());
            mIcons.add(icon);
            addView(icon);
        }
        requestLayout();
    }

    /** Size of the square slot an icon needs at full size (labels are hidden). */
    private int naturalSlot() {
        return Math.max(1, Math.round(mActivity.getDeviceProfile().folderChildIconSizePx * 1.3f));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);

        int natural = naturalSlot();
        LargeFolderMath.Fit fit = LargeFolderMath.fit(width, height, mPadding, mTitleHeight,
                natural, Math.round(natural * MIN_SLOT_FRACTION), mTotalApps);
        LargeFolderMath.Grid grid = fit.getGrid();
        mColumns = grid.getColumns();
        mRows = grid.getRows();
        mIconScale = Math.min(1f, fit.getSlot() / (float) natural);

        LargeFolderMath.Plan plan = LargeFolderMath.plan(mTotalApps, grid);
        boolean overflow = plan.getShowOverflowMarker();
        int shown = plan.getShown();
        mHasHiddenApps = plan.getHidden() > 0;

        mSizeButton.measure(MeasureSpec.makeMeasureSpec(mTitleHeight * 2, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mTitleHeight * 2, MeasureSpec.EXACTLY));
        mTitle.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, width - 2 * mPadding - mTitleHeight * 2), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mTitleHeight, MeasureSpec.EXACTLY));
        int slotSpec = MeasureSpec.makeMeasureSpec(natural, MeasureSpec.EXACTLY);
        for (int i = 0; i < mIcons.size(); i++) {
            BubbleTextView icon = mIcons.get(i);
            if (i < shown) {
                icon.setVisibility(VISIBLE);
                icon.measure(slotSpec, slotSpec);
                icon.setScaleX(mIconScale);
                icon.setScaleY(mIconScale);
            } else {
                icon.setVisibility(GONE);
            }
        }
        if (overflow) {
            mMore.setVisibility(VISIBLE);
            mMore.setText("+" + plan.getHidden());
            mMore.measure(slotSpec, slotSpec);
        } else {
            mMore.setVisibility(GONE);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;
        mTitle.layout(mPadding, mPadding, width - mPadding - mTitleHeight * 2, mPadding + mTitleHeight);
        int buttonSize = mTitleHeight * 2;
        mSizeButton.layout(width - buttonSize, mPadding + mTitleHeight / 2 - buttonSize / 2,
                width, mPadding + mTitleHeight / 2 + buttonSize / 2);

        int availW = Math.max(0, width - 2 * mPadding);
        int availH = Math.max(0, height - 2 * mPadding - mTitleHeight);
        int cellW = availW / mColumns;
        int cellH = Math.max(1, availH / mRows);
        int top = mPadding + mTitleHeight;

        int index = 0;
        for (BubbleTextView icon : mIcons) {
            if (icon.getVisibility() == GONE) continue;
            placeInCell(icon, index++, cellW, cellH, top);
        }
        if (mMore.getVisibility() != GONE) {
            placeInCell(mMore, index, cellW, cellH, top);
        }
    }

    /** Centers the child inside its cell of the grid. */
    private void placeInCell(View child, int index, int cellW, int cellH, int top) {
        int col = index % mColumns;
        int row = index / mColumns;
        int left = mPadding + col * cellW + (cellW - child.getMeasuredWidth()) / 2;
        int cellTop = top + row * cellH + (cellH - child.getMeasuredHeight()) / 2;
        child.layout(left, cellTop, left + child.getMeasuredWidth(),
                cellTop + child.getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mPanel.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(mPanel, mCornerRadius, mCornerRadius, mFillPaint);
        mPanel.inset(mStrokePaint.getStrokeWidth() / 2, mStrokePaint.getStrokeWidth() / 2);
        canvas.drawRoundRect(mPanel, mCornerRadius, mCornerRadius, mStrokePaint);
    }
}
