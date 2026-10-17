package com.teleprompter.pure;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.core.FloatingWindowHelper;
import com.lzf.easyfloat.core.FloatingWindowManager;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.enums.SidePattern;
import com.lzf.easyfloat.interfaces.OnInvokeView;
import com.lzf.easyfloat.interfaces.OnPermissionResult;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.teleprompter.pure.database.ScriptEntity;
import com.teleprompter.pure.ui.SmoothTextScrollView;

/**
 * 极致纯净悬浮提词器全局管理器 (FloatingPrompterManager)
 * 工业级双路线深度结合版：
 * 
 * 1. 【原生顶级微交互】：居中黄金拍摄视线预置，关闭强磁吸贴边，自由全屏悬停
 * 2. 【无感拖拽手柄】：顶部手柄独立接管窗口平移，台词区纯净滚动不抢手势
 * 3. 【自由拉伸缩放】：右下角支持随时动态拖动调整宽高，角标固定防裁切隐藏
 * 4. 【一键复位居中】：头部预置一键复位按钮，防止窗口变形或失真
 * 5. 【清爽默认状态】：启动悬浮窗始终保持纯净默认尺寸与位置，绝不持久化异常尺寸
 * 6. 【一键悬浮球收折】：录制准备时一键收缩至 48dp 晶体悬浮球贴边待命，轻触瞬间复原
 * 7. 【100% 离线纯净】：零联网权限，保护隐私安全
 */
public class FloatingPrompterManager {

    private static final String FLOAT_TAG = "PURE_TELEPROMPTER_FLOAT";
    private static final int DEFAULT_WIDTH_DP = 320;
    private static final int DEFAULT_HEIGHT_DP = 240;

    private static volatile FloatingPrompterManager sInstance;

    private SmoothTextScrollView mScrollView;
    private View mRootFloatView;
    private ConstraintLayout mPrompterCard;
    private LinearLayout mFloatBallContainer;
    private TextView mTvCountdownOverlay;
    private ImageView mBtnPlayPause;

    private ScriptEntity mCurrentScript;
    private int mCurrentBgColor = 0xFF12141A;
    private int mCurrentOpacity = 75;
    private int mBaseWidth = 0;
    private int mBaseHeight = 0;

    private FloatingPrompterManager() {}

    public static FloatingPrompterManager getInstance() {
        if (sInstance == null) {
            synchronized (FloatingPrompterManager.class) {
                if (sInstance == null) {
                    sInstance = new FloatingPrompterManager();
                }
            }
        }
        return sInstance;
    }

    public void showPrompter(Activity activity, ScriptEntity script) {
        this.mCurrentScript = script;
        if (!PermissionUtils.checkPermission(activity)) {
            PermissionUtils.requestPermission(activity, new OnPermissionResult() {
                @Override
                public void permissionResult(boolean isOpen) {
                    if (isOpen) {
                        initAndShow(activity);
                    } else {
                        Toast.makeText(activity, "请授予悬浮窗权限，以便在拍摄时进行浮窗提词！", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            initAndShow(activity);
        }
    }

    private void initAndShow(Activity activity) {
        // 先关闭旧的实例以防重复
        if (EasyFloat.isShow(FLOAT_TAG)) {
            EasyFloat.dismiss(FLOAT_TAG);
        }

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        // 初始位置：水平居中，距离屏幕顶部 15% (紧靠前置摄像头下方黄金跟视点)
        int initialY = (int) (dm.heightPixels * 0.15f);

        EasyFloat.with(activity.getApplicationContext())
                .setTag(FLOAT_TAG)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setSidePattern(SidePattern.DEFAULT) // 关闭磁吸边缘弹跳，完全自由随心悬停
                .setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, initialY)
                .setDragEnable(false) // 关闭全局阻断手势，由专用手柄独立接管
                .setImmersionStatusBar(true)
                .setLayout(R.layout.float_window_modern, new OnInvokeView() {
                    @Override
                    public void invoke(View view) {
                        mRootFloatView = view;
                        bindViewEvents(activity, view);
                    }
                })
                .show();
    }

    private void bindViewEvents(Activity activity, View rootFloatView) {
        mPrompterCard = rootFloatView.findViewById(R.id.prompter_card_container);
        mFloatBallContainer = rootFloatView.findViewById(R.id.float_ball_container);
        mScrollView = rootFloatView.findViewById(R.id.text_scroll_view);
        mTvCountdownOverlay = rootFloatView.findViewById(R.id.tv_countdown_overlay);
        mBtnPlayPause = rootFloatView.findViewById(R.id.btn_play_pause);

        View headerDragArea = rootFloatView.findViewById(R.id.header_drag_area);
        View dragTouchWrapper = rootFloatView.findViewById(R.id.drag_handle_touch_wrapper);
        ImageView btnClose = rootFloatView.findViewById(R.id.btn_close);
        ImageView btnResetWindow = rootFloatView.findViewById(R.id.btn_reset_window);
        ImageView btnMinimize = rootFloatView.findViewById(R.id.btn_minimize);
        ImageView btnMirror = rootFloatView.findViewById(R.id.btn_mirror);
        ImageView btnReset = rootFloatView.findViewById(R.id.btn_reset);
        ImageView btnScaleCorner = rootFloatView.findViewById(R.id.btn_scale_corner);

        ImageView tabLeft = rootFloatView.findViewById(R.id.tab_align_left);
        ImageView tabCenter = rootFloatView.findViewById(R.id.tab_align_center);
        ImageView tabRight = rootFloatView.findViewById(R.id.tab_align_right);

        // 确保播放/暂停按钮图标为醒目纯黑，绝不空白
        mBtnPlayPause.setColorFilter(Color.BLACK);

        // 1. 严格使用纯净默认尺寸，绝不持久化异常尺寸与位置
        resetWindowDimensionsToDefault(activity);

        // 2. 从全局配置 PrompterPreferences 读取偏好设定
        int prefSpeed = PrompterPreferences.getScrollSpeed(activity);
        float prefFontSize = PrompterPreferences.getFontSize(activity);
        int prefTextColor = PrompterPreferences.getTextColor(activity);
        mCurrentBgColor = PrompterPreferences.getBgColor(activity);
        mCurrentOpacity = PrompterPreferences.getBgOpacity(activity);
        int prefCountdown = PrompterPreferences.getCountdownSec(activity);
        boolean prefMirror = PrompterPreferences.isMirror(activity);

        // 3. 初始化台词数据
        if (mCurrentScript != null) {
            mScrollView.setText(mCurrentScript.content);
            mScrollView.setScrollSpeed(prefSpeed);
            mScrollView.setTextSize(prefFontSize);
            mScrollView.setTextColor(prefTextColor);
            mScrollView.setCountdownSeconds(prefCountdown);
            mScrollView.setMirror(prefMirror);
            mScrollView.setTextAlignmentMode(mCurrentScript.alignment);
            applyBackgroundStyle();
        }

        // 4. 初始对齐 UI 高亮
        updateAlignmentIconUI(tabLeft, tabCenter, tabRight, mScrollView.getCurrentAlignment());

        // 5. 状态监听器
        mScrollView.setOnPrompterStateListener(new SmoothTextScrollView.OnPrompterStateListener() {
            @Override
            public void onPlayStateChanged(boolean isPlaying) {
                mBtnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                mBtnPlayPause.setColorFilter(Color.BLACK);
            }

            @Override
            public void onScrollPositionChanged(int currentY, int maxY) {}

            @Override
            public void onReset() {
                mBtnPlayPause.setImageResource(R.drawable.ic_play);
                mBtnPlayPause.setColorFilter(Color.BLACK);
                mTvCountdownOverlay.setVisibility(View.GONE);
            }

            @Override
            public void onCountdownTick(int remainingSeconds) {
                mTvCountdownOverlay.setVisibility(View.VISIBLE);
                mTvCountdownOverlay.setText(String.valueOf(remainingSeconds));
            }

            @Override
            public void onCountdownFinished() {
                mTvCountdownOverlay.setVisibility(View.GONE);
            }
        });

        // 6. 顶部把手原生 WindowManager / EasyFloat 平滑极速拖拽
        setupWindowDragListener(headerDragArea, dragTouchWrapper);

        // 7. 顶部一键复位默认尺寸与居中位置
        if (btnResetWindow != null) {
            btnResetWindow.setOnClickListener(v -> resetWindowToDefaults(activity));
        }

        // 8. 右下角无感拉伸缩放把手 (Corner Resize Handle)
        setupResizeCornerListener(activity, btnScaleCorner);

        // 9. 一键最小化折叠成 48dp 悬浮球
        btnMinimize.setOnClickListener(v -> minimizeToFloatBall());

        // 10. 悬浮球拖拽移动与轻触展开
        setupFloatBallTouchListener(activity);

        // 11. 播放/暂停
        mBtnPlayPause.setOnClickListener(v -> mScrollView.togglePlayPause());

        // 12. 左右镜像翻转按键 (纯图标 🪞，分光镜与自拍镜像核心功能)
        if (btnMirror != null) {
            btnMirror.setColorFilter(mScrollView.isMirror() ? 0xFF38EF7D : Color.WHITE);
            btnMirror.setOnClickListener(v -> {
                mScrollView.toggleMirror();
                boolean isMirror = mScrollView.isMirror();
                PrompterPreferences.setMirror(activity, isMirror);
                btnMirror.setColorFilter(isMirror ? 0xFF38EF7D : Color.WHITE);
                Toast.makeText(activity, isMirror ? "已开启左右镜像翻转 (分光镜模式)" : "已恢复正常显示", Toast.LENGTH_SHORT).show();
            });
        }

        // 13. 一键重置回到首句
        btnReset.setOnClickListener(v -> mScrollView.resetToStart(true));

        // 14. 纯图标对齐切换
        tabLeft.setOnClickListener(v -> {
            mScrollView.setTextAlignmentMode(SmoothTextScrollView.ALIGN_LEFT);
            updateAlignmentIconUI(tabLeft, tabCenter, tabRight, SmoothTextScrollView.ALIGN_LEFT);
        });
        tabCenter.setOnClickListener(v -> {
            mScrollView.setTextAlignmentMode(SmoothTextScrollView.ALIGN_CENTER);
            updateAlignmentIconUI(tabLeft, tabCenter, tabRight, SmoothTextScrollView.ALIGN_CENTER);
        });
        tabRight.setOnClickListener(v -> {
            mScrollView.setTextAlignmentMode(SmoothTextScrollView.ALIGN_RIGHT);
            updateAlignmentIconUI(tabLeft, tabCenter, tabRight, SmoothTextScrollView.ALIGN_RIGHT);
        });

        // 15. 左上角退出
        btnClose.setOnClickListener(v -> dismiss());
    }

    /**
     * 原生顶部把手平滑拖动：调用 EasyFloat.updateFloat，无上下文 WindowToken 冲突
     */
    private void setupWindowDragListener(View headerArea, View dragTouchWrapper) {
        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private float startRawX, startRawY;
            private int startWindowX, startWindowY;
            private FloatingWindowHelper helper;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        helper = FloatingWindowManager.INSTANCE.getHelper(FLOAT_TAG);
                        if (helper != null && helper.getParams() != null) {
                            startWindowX = helper.getParams().x;
                            startWindowY = helper.getParams().y;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (helper != null && helper.getParams() != null) {
                            float dx = event.getRawX() - startRawX;
                            float dy = event.getRawY() - startRawY;
                            int targetX = (int) (startWindowX + dx);
                            int targetY = (int) (startWindowY + dy);
                            EasyFloat.updateFloat(FLOAT_TAG, targetX, targetY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        };

        headerArea.setOnTouchListener(dragListener);
        if (dragTouchWrapper != null) {
            dragTouchWrapper.setOnTouchListener(dragListener);
        }
    }

    /**
     * 右下角窗口拉伸逻辑：实时通知 WindowManager 容器大小，绝不隐藏缩放角标
     */
    private void setupResizeCornerListener(Activity activity, View scaleCorner) {
        if (scaleCorner == null) return;
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        int minWidth = dp2px(activity, 280);
        int minHeight = dp2px(activity, 200);
        int maxWidth = screenWidth - dp2px(activity, 16);
        int maxHeight = screenHeight - dp2px(activity, 60);

        scaleCorner.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private int initialW, initialH;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        initialW = mBaseWidth > 0 ? mBaseWidth : mPrompterCard.getWidth();
                        initialH = mBaseHeight > 0 ? mBaseHeight : mPrompterCard.getHeight();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;

                        int targetW = (int) (initialW + dx);
                        int targetH = (int) (initialH + dy);

                        targetW = Math.max(minWidth, Math.min(maxWidth, targetW));
                        targetH = Math.max(minHeight, Math.min(maxHeight, targetH));

                        mBaseWidth = targetW;
                        mBaseHeight = targetH;

                        ViewGroup.LayoutParams lp = mPrompterCard.getLayoutParams();
                        lp.width = targetW;
                        lp.height = targetH;
                        mPrompterCard.setLayoutParams(lp);

                        EasyFloat.updateFloat(FLOAT_TAG, -1, -1, targetW, targetH);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 零持久化：每次重新打开应用均呈现清爽默认态
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * 悬浮球手势监听：区分“拖拽移动”与“轻触点击展开”
     */
    private void setupFloatBallTouchListener(Activity activity) {
        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();

        mFloatBallContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startTouchX, startTouchY;
            private int startWindowX, startWindowY;
            private boolean isDragged = false;
            private FloatingWindowHelper helper;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTouchX = event.getRawX();
                        startTouchY = event.getRawY();
                        isDragged = false;
                        helper = FloatingWindowManager.INSTANCE.getHelper(FLOAT_TAG);
                        if (helper != null && helper.getParams() != null) {
                            startWindowX = helper.getParams().x;
                            startWindowY = helper.getParams().y;
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startTouchX;
                        float dy = event.getRawY() - startTouchY;
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            isDragged = true;
                        }
                        if (isDragged && helper != null && helper.getParams() != null) {
                            int targetX = (int) (startWindowX + dx);
                            int targetY = (int) (startWindowY + dy);
                            EasyFloat.updateFloat(FLOAT_TAG, targetX, targetY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragged) {
                            // 轻触点击：展开为提词大视窗
                            expandFromFloatBall();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        return true;
                }
                return false;
            }
        });
    }

    public void minimizeToFloatBall() {
        if (mPrompterCard != null && mFloatBallContainer != null) {
            mPrompterCard.setVisibility(View.GONE);
            mFloatBallContainer.setVisibility(View.VISIBLE);
            int ballSize = dp2px(mFloatBallContainer.getContext(), 48);
            EasyFloat.updateFloat(FLOAT_TAG, -1, -1, ballSize, ballSize);
        }
    }

    public void expandFromFloatBall() {
        if (mPrompterCard != null && mFloatBallContainer != null) {
            mFloatBallContainer.setVisibility(View.GONE);
            mPrompterCard.setVisibility(View.VISIBLE);
            EasyFloat.updateFloat(FLOAT_TAG, -1, -1, mBaseWidth, mBaseHeight);
        }
    }

    /**
     * 重置悬浮窗宽高尺寸为初始默认 320dp x 240dp
     */
    private void resetWindowDimensionsToDefault(Context context) {
        mBaseWidth = dp2px(context, DEFAULT_WIDTH_DP);
        mBaseHeight = dp2px(context, DEFAULT_HEIGHT_DP);

        if (mPrompterCard != null) {
            ViewGroup.LayoutParams lp = mPrompterCard.getLayoutParams();
            lp.width = mBaseWidth;
            lp.height = mBaseHeight;
            mPrompterCard.setLayoutParams(lp);
        }
    }

    /**
     * 一键复位悬浮窗尺寸与位置状态至纯净默认态 (防变形、防遮挡、防迷失)
     */
    public void resetWindowToDefaults(Activity activity) {
        resetWindowDimensionsToDefault(activity);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int defaultX = (dm.widthPixels - mBaseWidth) / 2;
        int defaultY = (int) (dm.heightPixels * 0.15f);

        EasyFloat.updateFloat(FLOAT_TAG, defaultX, defaultY, mBaseWidth, mBaseHeight);

        Toast.makeText(activity, "视窗尺寸与位置已恢复默认居中", Toast.LENGTH_SHORT).show();
    }

    private void updateAlignmentIconUI(ImageView l, ImageView c, ImageView r, int mode) {
        l.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_LEFT ? R.drawable.bg_segment_active_pill : 0);
        l.setColorFilter(mode == SmoothTextScrollView.ALIGN_LEFT ? Color.WHITE : 0x80FFFFFF);

        c.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_CENTER ? R.drawable.bg_segment_active_pill : 0);
        c.setColorFilter(mode == SmoothTextScrollView.ALIGN_CENTER ? Color.WHITE : 0x80FFFFFF);

        r.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_RIGHT ? R.drawable.bg_segment_active_pill : 0);
        r.setColorFilter(mode == SmoothTextScrollView.ALIGN_RIGHT ? Color.WHITE : 0x80FFFFFF);
    }

    private void applyBackgroundStyle() {
        if (mPrompterCard != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(dp2px(mPrompterCard.getContext(), 16));

            int alpha = (int) ((mCurrentOpacity / 100.0f) * 255);
            int r = Color.red(mCurrentBgColor);
            int g = Color.green(mCurrentBgColor);
            int b = Color.blue(mCurrentBgColor);
            drawable.setColor(Color.argb(alpha, r, g, b));

            // 微弱半透明描边，强化晶体质感
            drawable.setStroke(dp2px(mPrompterCard.getContext(), 1), Color.argb(40, 255, 255, 255));
            mPrompterCard.setBackground(drawable);
        }
    }

    public void dismiss() {
        if (mScrollView != null) {
            mScrollView.pauseScroll();
        }
        EasyFloat.dismiss(FLOAT_TAG);
        mRootFloatView = null;
        mPrompterCard = null;
        mFloatBallContainer = null;
        mScrollView = null;
    }

    private int dp2px(Context context, float dp) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
