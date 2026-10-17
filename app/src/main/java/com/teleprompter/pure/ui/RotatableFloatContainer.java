package com.teleprompter.pure.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.teleprompter.pure.R;

/**
 * 专为悬浮提词器打造的自适应无损旋转容器 (RotatableFloatContainer)
 * 彻底解决原生 Android View 在 setRotation(90°/270°) 时出现的一系列问题：
 * 1. 视窗边界裁剪与按键遮挡：通过布局边界精确补偿 (-diff, +diff)，视窗与 WindowManager 尺寸 100% 贴合，零黑边零裁切
 * 2. 触控坐标映射失真：将旋转后物理边界严格约束在 View 坐标系内，保证所有按键触控事件百发百中
 * 3. 完美兼容最小化悬浮球与卡片动态拉伸
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
        requestLayout();
        invalidate();
    }

    public float getChildRotation() {
        return mRotationAngle;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View floatBall = findViewById(R.id.float_ball_container);
        if (floatBall != null && floatBall.getVisibility() == VISIBLE) {
            measureChild(floatBall, widthMeasureSpec, heightMeasureSpec);
            setMeasuredDimension(floatBall.getMeasuredWidth(), floatBall.getMeasuredHeight());
            return;
        }

        View prompterCard = findViewById(R.id.prompter_card_container);
        if (prompterCard == null || prompterCard.getVisibility() == GONE) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // 以卡片自身设置的宽高度进行测量，杜绝因父级约束导致的缩放挤压
        ViewGroup.LayoutParams lp = prompterCard.getLayoutParams();
        int childWidthSpec = (lp != null && lp.width > 0)
                ? MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                : widthMeasureSpec;
        int childHeightSpec = (lp != null && lp.height > 0)
                ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                : heightMeasureSpec;

        prompterCard.measure(childWidthSpec, childHeightSpec);
        int cardW = prompterCard.getMeasuredWidth();
        int cardH = prompterCard.getMeasuredHeight();

        boolean isRotated = Math.abs(((int) mRotationAngle) % 180) == 90;
        if (isRotated) {
            // 旋转 90 或 270 度时，窗口测量尺寸为对调后的宽高
            setMeasuredDimension(cardH, cardW);
        } else {
            setMeasuredDimension(cardW, cardH);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View floatBall = findViewById(R.id.float_ball_container);
        if (floatBall != null && floatBall.getVisibility() == VISIBLE) {
            floatBall.layout(0, 0, floatBall.getMeasuredWidth(), floatBall.getMeasuredHeight());
            return;
        }

        View prompterCard = findViewById(R.id.prompter_card_container);
        if (prompterCard == null || prompterCard.getVisibility() == GONE) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }

        int cardW = prompterCard.getMeasuredWidth();
        int cardH = prompterCard.getMeasuredHeight();

        // 基础布局始终在 (0, 0, cardW, cardH)
        prompterCard.layout(0, 0, cardW, cardH);

        // 设置旋转轴心为卡片几何中心
        prompterCard.setPivotX(cardW / 2.0f);
        prompterCard.setPivotY(cardH / 2.0f);

        boolean isRotated = Math.abs(((int) mRotationAngle) % 180) == 90;
        if (isRotated) {
            // 关键几何位移补偿：旋转后包围盒与容器原点对齐
            float diff = (cardW - cardH) / 2.0f;
            prompterCard.setTranslationX(-diff);
            prompterCard.setTranslationY(diff);
        } else {
            prompterCard.setTranslationX(0f);
            prompterCard.setTranslationY(0f);
        }

        prompterCard.setRotation(mRotationAngle);
    }
}
