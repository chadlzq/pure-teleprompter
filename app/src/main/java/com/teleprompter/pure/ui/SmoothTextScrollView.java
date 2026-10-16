package com.teleprompter.pure.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 极简平滑滚屏控件 (SmoothTextScrollView)
 * 提取自成熟提词器 App 并重构，无任何冗余海外依赖，完美适配中国大陆 Android 各种机型。
 * 
 * 核心特性：
 * 1. 恒定速度平滑滚动 (基于 ValueAnimator / 16ms 步进)
 * 2. 【台词手动滑动定位】：支持随时手势滑屏跳转任意台词，松手后无缝续播或暂停就位
 * 3. 【一键重置归零】：支持平滑或瞬间回到台词首句 (Reset to Beginning)
 * 4. 【专属细节】：支持动态切换文字对齐方式 (左对齐 / 居中对齐 / 右对齐)
 * 5. 【新增】：光学分光镜镜像模式 (水平翻转 setMirror)
 * 6. 【新增】：高对比度文字调色板 (白 / 黄 / 绿 / 蓝)
 * 7. 【新增】：3-2-1 启动倒计时联动控制
 */
public class SmoothTextScrollView extends ScrollView {

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    public interface OnPrompterStateListener {
        void onPlayStateChanged(boolean isPlaying);
        void onScrollPositionChanged(int currentY, int maxY);
        void onReset();
        void onCountdownTick(int remainingSeconds);
        void onCountdownFinished();
    }

    private TextView mInnerTextView;
    private ValueAnimator mScrollAnimator;
    private OnPrompterStateListener mStateListener;
    private CountDownTimer mCountDownTimer;
    
    // 控制参数
    private int mScrollSpeed = 30; // 滚动速度 (像素/秒)
    private boolean mIsPlaying = false;
    private boolean mIsUserTouching = false;
    private int mCurrentAlignment = ALIGN_LEFT;
    private boolean mIsMirror = false; // 是否开启光学分光镜镜像模式
    private int mCountdownSeconds = 3;  // 默认 3 秒倒计时 (0 为关闭倒计时)
    private boolean mIsCountingDown = false;

    // 手势识别辅助 (区分是轻触点击还是滑动拖拽)
    private float mTouchDownY = 0f;
    private float mTouchDownX = 0f;
    private int mTouchSlop;
    private boolean mHasMoved = false;

    public SmoothTextScrollView(Context context) {
        this(context, null);
    }

    public SmoothTextScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SmoothTextScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mInnerTextView = new TextView(context);
        mInnerTextView.setTextColor(Color.WHITE);
        mInnerTextView.setTextSize(22f); // 默认 22sp
        mInnerTextView.setLineSpacing(12f, 1.2f); // 增大行距，便于口播跟读
        
        // 默认左对齐
        setTextAlignmentMode(ALIGN_LEFT);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int paddingVertical = dp2px(120);
        int paddingHorizontal = dp2px(16);
        mInnerTextView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        addView(mInnerTextView, lp);
    }

    public void setOnPrompterStateListener(OnPrompterStateListener listener) {
        this.mStateListener = listener;
    }

    /**
     * 【进阶功能 1】：光学分光镜镜像翻转模式
     * 水平翻转文字，配合专业提词器分光玻璃反射使用
     */
    public void setMirror(boolean mirror) {
        this.mIsMirror = mirror;
        if (mInnerTextView != null) {
            mInnerTextView.setScaleX(mirror ? -1.0f : 1.0f);
        }
    }

    public boolean isMirror() {
        return mIsMirror;
    }

    public void toggleMirror() {
        setMirror(!mIsMirror);
    }

    /**
     * 【进阶功能 2】：文字颜色切换 (高对比度调色板)
     */
    public void setTextColor(int color) {
        if (mInnerTextView != null) {
            mInnerTextView.setTextColor(color);
        }
    }

    /**
     * 【进阶功能 3】：设置倒计时秒数 (0: 关闭, 3: 3秒, 5: 5秒)
     */
    public void setCountdownSeconds(int seconds) {
        this.mCountdownSeconds = Math.max(0, seconds);
    }

    public int getCountdownSeconds() {
        return mCountdownSeconds;
    }

    public boolean isCountingDown() {
        return mIsCountingDown;
    }

    /**
     * 设置文字对齐方式 (左 / 中 / 右)
     */
    public void setTextAlignmentMode(int alignmentMode) {
        mCurrentAlignment = alignmentMode;
        if (mInnerTextView == null) return;

        switch (alignmentMode) {
            case ALIGN_CENTER:
                mInnerTextView.setGravity(Gravity.CENTER_HORIZONTAL);
                break;
            case ALIGN_RIGHT:
                mInnerTextView.setGravity(Gravity.END);
                break;
            case ALIGN_LEFT:
            default:
                mInnerTextView.setGravity(Gravity.START);
                break;
        }
    }

    public int toggleNextAlignment() {
        int nextMode = (mCurrentAlignment + 1) % 3;
        setTextAlignmentMode(nextMode);
        return nextMode;
    }

    public int getCurrentAlignment() {
        return mCurrentAlignment;
    }

    public void setText(CharSequence text) {
        if (mInnerTextView != null) {
            mInnerTextView.setText(text);
            resetToStart(false);
        }
    }

    public void setTextSize(float sp) {
        if (mInnerTextView != null) {
            mInnerTextView.setTextSize(sp);
        }
    }

    public void setScrollSpeed(int speed) {
        mScrollSpeed = Math.max(5, speed);
        if (mIsPlaying && !mIsUserTouching && !mIsCountingDown) {
            startScrollInternal();
        }
    }

    public int getScrollSpeed() {
        return mScrollSpeed;
    }

    /**
     * 外部启动滚屏入口：智能判断是否先执行 3-2-1 倒计时
     */
    public void startScroll() {
        if (mCountdownSeconds > 0 && !mIsPlaying && getScrollY() == 0) {
            startCountdownThenScroll();
        } else {
            startScrollInternal();
        }
    }

    /**
     * 执行 3-2-1 倒计时
     */
    private void startCountdownThenScroll() {
        cancelCountdown();
        mIsCountingDown = true;
        if (mStateListener != null) {
            mStateListener.onPlayStateChanged(true);
        }

        mCountDownTimer = new CountDownTimer((mCountdownSeconds * 1000L) + 200, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int) Math.round(millisUntilFinished / 1000.0);
                if (sec > 0 && mStateListener != null) {
                    mStateListener.onCountdownTick(sec);
                }
            }

            @Override
            public void onFinish() {
                mIsCountingDown = false;
                if (mStateListener != null) {
                    mStateListener.onCountdownFinished();
                }
                startScrollInternal();
            }
        }.start();
    }

    public void cancelCountdown() {
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            mCountDownTimer = null;
        }
        mIsCountingDown = false;
        if (mStateListener != null) {
            mStateListener.onCountdownFinished();
        }
    }

    /**
     * 底层匀速平滑滚屏
     */
    private void startScrollInternal() {
        mIsPlaying = true;
        if (mScrollAnimator != null) {
            mScrollAnimator.cancel();
        }

        int currentY = getScrollY();
        int totalHeight = mInnerTextView.getHeight() - getHeight();
        if (totalHeight <= 0) return;

        int remainingDistance = totalHeight - currentY;
        if (remainingDistance <= 0) return;

        long duration = (long) ((remainingDistance / (float) mScrollSpeed) * 1000);

        mScrollAnimator = ValueAnimator.ofInt(currentY, totalHeight);
        mScrollAnimator.setDuration(Math.max(500, duration));
        mScrollAnimator.setInterpolator(new LinearInterpolator());
        mScrollAnimator.addUpdateListener(animation -> {
            if (!mIsUserTouching) {
                int y = (int) animation.getAnimatedValue();
                scrollTo(0, y);
                if (mStateListener != null) {
                    mStateListener.onScrollPositionChanged(y, totalHeight);
                }
            }
        });
        mScrollAnimator.start();

        if (mStateListener != null) {
            mStateListener.onPlayStateChanged(true);
        }
    }

    public void pauseScroll() {
        cancelCountdown();
        mIsPlaying = false;
        if (mScrollAnimator != null) {
            mScrollAnimator.cancel();
        }
        if (mStateListener != null) {
            mStateListener.onPlayStateChanged(false);
        }
    }

    public boolean togglePlayPause() {
        if (mIsPlaying || mIsCountingDown) {
            pauseScroll();
        } else {
            startScroll();
        }
        return mIsPlaying;
    }

    public boolean isPlaying() {
        return mIsPlaying || mIsCountingDown;
    }

    /**
     * 一键重置回到首句
     */
    public void resetToStart(boolean smooth) {
        pauseScroll();
        if (smooth && getScrollY() > 0) {
            ValueAnimator resetAnim = ValueAnimator.ofInt(getScrollY(), 0);
            resetAnim.setDuration(300);
            resetAnim.setInterpolator(new DecelerateInterpolator());
            resetAnim.addUpdateListener(a -> scrollTo(0, (int) a.getAnimatedValue()));
            resetAnim.start();
        } else {
            scrollTo(0, 0);
        }

        if (mStateListener != null) {
            mStateListener.onReset();
            mStateListener.onScrollPositionChanged(0, getMaxScrollAmount());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsUserTouching = true;
                mTouchDownX = ev.getX();
                mTouchDownY = ev.getY();
                mHasMoved = false;
                if (mIsCountingDown) {
                    cancelCountdown();
                }
                if (mScrollAnimator != null && mScrollAnimator.isRunning()) {
                    mScrollAnimator.cancel();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - mTouchDownX);
                float dy = Math.abs(ev.getY() - mTouchDownY);
                if (dx > mTouchSlop || dy > mTouchSlop) {
                    mHasMoved = true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsUserTouching = false;
                if (!mHasMoved) {
                    togglePlayPause();
                } else {
                    if (mIsPlaying) {
                        startScrollInternal();
                    } else {
                        if (mStateListener != null) {
                            mStateListener.onScrollPositionChanged(getScrollY(), getMaxScrollAmount());
                        }
                    }
                }
                break;
        }
        return super.onTouchEvent(ev);
    }

    public TextView getInnerTextView() {
        return mInnerTextView;
    }

    private int dp2px(float dp) {
        float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
