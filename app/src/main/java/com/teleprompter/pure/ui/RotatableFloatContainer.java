package com.teleprompter.pure.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * 专为悬浮提词器设计的自适应旋转容器 (RotatableFloatContainer)
 * 解决原生 Android View 在 setRotation(90) 时，WindowManager 窗口不重新测量导致底部按键被裁切、位置偏移的问题
 * 
 * 核心机制：
 * 当旋转为 90° 或 270° 时，在 onMeasure 中动态对调测量宽高 (Width <-> Height)
 * 并将内部提词卡片精确居中，保证旋转后悬浮窗窗口尺寸自动对齐，所有按键均完全可见且触控区域精确映射！
 */
public class RotatableFloatContainer extends FrameLayout {

    private float mRotationAngle = 0f;

    public RotatableFloatContainer(Context context) {
        super(context);
        init();
    }

    public RotatableFloatContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RotatableFloatContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setChildRotation(float angle) {
        this.mRotationAngle = angle;
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child != null) {
            child.setRotation(angle);
        }
        requestLayout();
        invalidate();
    }

    public float getChildRotation() {
        return mRotationAngle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null || child.getVisibility() == GONE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // 测量子卡片 (mPrompterCard)
        measureChild(child, widthMeasureSpec, heightMeasureSpec);
        int childW = child.getMeasuredWidth();
        int childH = child.getMeasuredHeight();

        boolean isRotated = Math.abs(mRotationAngle % 180) == 90;
        if (isRotated) {
            // 旋转 90 或 270 度时，将自身（及 WindowManager 悬浮窗口）宽高对调
            setMeasuredDimension(childH, childW);
        } else {
            setMeasuredDimension(childW, childH);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View child = getChildCount() > 0 ? getChildAt(0) : null;
        if (child == null || child.getVisibility() == GONE) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }

        int containerW = right - left;
        int containerH = bottom - top;
        int childW = child.getMeasuredWidth();
        int childH = child.getMeasuredHeight();

        // 精确居中摆放子卡片
        int childLeft = (containerW - childW) / 2;
        int childTop = (containerH - childH) / 2;
        child.layout(childLeft, childTop, childLeft + childW, childTop + childH);

        // 设置旋转轴心为卡片物理正中心
        child.setPivotX(childW / 2.0f);
        child.setPivotY(childH / 2.0f);
        child.setRotation(mRotationAngle);
    }
}
