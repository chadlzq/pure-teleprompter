package com.teleprompter.pure;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
 * 3. 【自由拉伸缩放】：右下角支持随时动态拖动调整宽高，自动记忆尺寸
 * 4. 【一键悬浮球收折】：录制准备时一键收缩至 48dp 晶体悬浮球贴边待命，轻触瞬间复原
 * 5. 【系统级设置解耦】：设置移至 APP 内部独立页面，彻底根除 Token 异常与遮挡
 * 6. 【100% 离线纯净】：零联网权限，保护隐私安全
 */
public class FloatingPrompterManager {

    private static final String FLOAT_TAG = "PURE_TELEPROMPTER_FLOAT";
    private static final String PREF_NAME = "prompter_window_prefs";
    private static final String KEY_WIDTH = "prompter_width";
    private static final String KEY_HEIGHT = "prompter_height";

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
        ImageView btnMinimize = rootFloatView.findViewById(R.id.btn_minimize);
        ImageView btnReset = rootFloatView.findViewById(R.id.btn_reset);
        ImageView btnScaleCorner = rootFloatView.findViewById(R.id.btn_scale_corner);

        ImageView tabLeft = rootFloatView.findViewById(R.id.tab_align_left);
        ImageView tabCenter = rootFloatView.findViewById(R.id.tab_align_center);
        ImageView tabRight = rootFloatView.findViewById(R.id.tab_align_right);

        // 1. 恢复用户保存的窗口尺寸或默认尺寸
        restoreSavedWindowDimensions(activity);

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
            }

            @Override
            public void onScrollPositionChanged(int currentY, int maxY) {}

            @Override
            public void onReset() {
                mBtnPlayPause.setImageResource(R.drawable.ic_play);
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

        // 7. 右下角无感拉伸缩放把手 (Corner Resize Handle)
        setupResizeCornerListener(activity, btnScaleCorner);

        // 8. 一键最小化折叠成 48dp 悬浮球
        btnMinimize.setOnClickListener(v -> minimizeToFloatBall());

        // 9. 悬浮球拖拽移动与轻触展开
        setupFloatBallTouchListener(activity);

        // 10. 播放/暂停
        mBtnPlayPause.setOnClickListener(v -> mScrollView.togglePlayPause());

        // 11. 一键重置回到首句
        btnReset.setOnClickListener(v -> mScrollView.resetToStart(true));

        // 12. 纯图标对齐切换
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

        // 13. 左上角退出
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
     * 右下角窗口拉伸逻辑
     */
    private void setupResizeCornerListener(Activity activity, View scaleCorner) {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        int minWidth = dp2px(activity, 220);
        int minHeight = dp2px(activity, 160);
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
                        initialW = mPrompterCard.getWidth();
                        initialH = mPrompterCard.getHeight();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;

                        int targetW = (int) (initialW + dx);
                        int targetH = (int) (initialH + dy);

                        targetW = Math.max(minWidth, Math.min(maxWidth, targetW));
                        targetH = Math.max(minHeight, Math.min(maxHeight, targetH));

                        ViewGroup.LayoutParams lp = mPrompterCard.getLayoutParams();
                        lp.width = targetW;
                        lp.height = targetH;
                        mPrompterCard.setLayoutParams(lp);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        saveWindowDimensions(activity, mPrompterCard.getWidth(), mPrompterCard.getHeight());
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
        }
    }

    public void expandFromFloatBall() {
        if (mPrompterCard != null && mFloatBallContainer != null) {
            mFloatBallContainer.setVisibility(View.GONE);
            mPrompterCard.setVisibility(View.VISIBLE);
        }
    }

    private void restoreSavedWindowDimensions(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedW = sp.getInt(KEY_WIDTH, dp2px(context, 320));
        int savedH = sp.getInt(KEY_HEIGHT, dp2px(context, 240));

        ViewGroup.LayoutParams lp = mPrompterCard.getLayoutParams();
        lp.width = savedW;
        lp.height = savedH;
        mPrompterCard.setLayoutParams(lp);
    }

    private void saveWindowDimensions(Context context, int width, int height) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_WIDTH, width).putInt(KEY_HEIGHT, height).apply();
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
