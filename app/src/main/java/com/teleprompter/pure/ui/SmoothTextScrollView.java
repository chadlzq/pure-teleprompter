package com.teleprompter.pure.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 极致平滑提词器滚动控件 (SmoothTextScrollView)
 * 融合成熟工业级提词器交互核心：
 * 1. 【60FPS 无感匀速滚屏】：基于 16ms 步进累加器 (Frame-paced Accumulator)，杜绝掉帧与速度畸变
 * 2. 【零手势冲突拖拽跟读】：用户随时可用手指上下拖动台词快速跳转，松手后无缝顺延跟读
 * 3. 【一键重置归零】：支持瞬时或减速平滑回到台词首句
 * 4. 【分光镜镜像翻转】：支持水平镜像模式 (setMirror)，适配专业分光提词玻璃
 * 5. 【高对比度文字与行间距】：专为真人口播跟读优化的文字大行距与颜色调配
 * 6. 【3-2-1 启动倒计时联动】
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
    private OnPrompterStateListener mStateListener;
    private CountDownTimer mCountDownTimer;

    // 控制参数
    private int mScrollSpeed = 30; // 滚动速度 (像素/秒)
    private boolean mIsPlaying = false;
    private boolean mIsUserTouching = false;
    private int mCurrentAlignment = ALIGN_LEFT;
    private boolean mIsMirror = false;
    private int mCountdownSeconds = 3; // 默认 3 秒倒计时
    private boolean mIsCountingDown = false;

    // 16ms 步进滚动引擎
    private static final int FRAME_INTERVAL_MS = 16;
    private float mAccumulatedPixels = 0f;
    private final Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsPlaying || mIsCountingDown) {
                return;
            }

            int maxY = getMaxScrollY();
            if (maxY <= 0 || getScrollY() >= maxY) {
                // 已滚动到底部，自动暂停
                pauseScroll();
                return;
            }

            if (!mIsUserTouching) {
                // 计算当前 16ms 帧应滚动的浮点像素量
                float pixelsThisFrame = (mScrollSpeed * FRAME_INTERVAL_MS) / 1000f;
                mAccumulatedPixels += pixelsThisFrame;

                if (mAccumulatedPixels >= 1.0f) {
                    int dy = (int) mAccumulatedPixels;
                    mAccumulatedPixels -= dy;
                    scrollBy(0, dy);

                    if (mStateListener != null) {
                        mStateListener.onScrollPositionChanged(getScrollY(), maxY);
                    }
                }
            }

            postDelayed(this, FRAME_INTERVAL_MS);
        }
    };

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

        mInnerTextView = new TextView(context);
        mInnerTextView.setTextColor(Color.WHITE);
        mInnerTextView.setTextSize(22f); // 默认 22sp
        mInnerTextView.setLineSpacing(12f, 1.25f); // 增大行距，便于口播跟读
        
        // 默认左对齐
        setTextAlignmentMode(ALIGN_LEFT);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        
        // 上下增加足够 padding，确保首句和末句均能滚动到正中参考线
        int paddingVertical = dp2px(110);
        int paddingHorizontal = dp2px(14);
        mInnerTextView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);

        addView(mInnerTextView, lp);
    }

    public void setOnPrompterStateListener(OnPrompterStateListener listener) {
        this.mStateListener = listener;
    }

    /**
     * 光学分光镜水平镜像翻转
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
     * 文字颜色设置
     */
    public void setTextColor(int color) {
        if (mInnerTextView != null) {
            mInnerTextView.setTextColor(color);
        }
    }

    /**
     * 倒计时秒数
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
     * 设置文字对齐方式
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
    }

    public int getScrollSpeed() {
        return mScrollSpeed;
    }

    /**
     * 启动滚屏：在顶部时若开启倒计时则先倒计时
     */
    public void startScroll() {
        if (mCountdownSeconds > 0 && !mIsPlaying && getScrollY() == 0) {
            startCountdownThenScroll();
        } else {
            startScrollInternal();
        }
    }

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

    private void startScrollInternal() {
        mIsPlaying = true;
        mAccumulatedPixels = 0f;
        removeCallbacks(mScrollRunnable);
        post(mScrollRunnable);

        if (mStateListener != null) {
            mStateListener.onPlayStateChanged(true);
        }
    }

    public void pauseScroll() {
        cancelCountdown();
        mIsPlaying = false;
        removeCallbacks(mScrollRunnable);

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
     * 重置回到台词开头首句
     */
    public void resetToStart(boolean smooth) {
        pauseScroll();
        if (smooth && getScrollY() > 0) {
            ValueAnimator resetAnim = ValueAnimator.ofInt(getScrollY(), 0);
            resetAnim.setDuration(280);
            resetAnim.setInterpolator(new DecelerateInterpolator());
            resetAnim.addUpdateListener(a -> scrollTo(0, (int) a.getAnimatedValue()));
            resetAnim.start();
        } else {
            scrollTo(0, 0);
        }

        if (mStateListener != null) {
            mStateListener.onReset();
            mStateListener.onScrollPositionChanged(0, getMaxScrollY());
        }
    }

    /**
     * 跳到下一行 (Line Skip Forward)
     */
    public void skipNextLine() {
        int lineHeight = mInnerTextView.getLineHeight();
        int targetY = Math.min(getScrollY() + lineHeight, getMaxScrollY());
        smoothScrollTo(0, targetY);
    }

    /**
     * 跳到上一行 (Line Skip Backward)
     */
    public void skipPreviousLine() {
        int lineHeight = mInnerTextView.getLineHeight();
        int targetY = Math.max(getScrollY() - lineHeight, 0);
        smoothScrollTo(0, targetY);
    }

    public int getMaxScrollY() {
        if (mInnerTextView == null) return 0;
        return Math.max(0, mInnerTextView.getHeight() - getHeight());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mIsUserTouching = true;
                if (mIsCountingDown) {
                    cancelCountdown();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsUserTouching = false;
                if (mStateListener != null) {
                    mStateListener.onScrollPositionChanged(getScrollY(), getMaxScrollY());
                }
                break;
        }
        // 交由原生 ScrollView 负责流畅的手指滑动定位
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
