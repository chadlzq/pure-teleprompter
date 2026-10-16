package com.teleprompter.pure;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.lzf.easyfloat.EasyFloat;
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
 * 1. 【手势物理隔离】：EasyFloat 关闭全局阻断式拖拽，仅对顶部专设把手 (Top Drag Handle) 响应窗口移动，
 *    彻底释放台词滚动区，实现与抖音/系统相册同级别的丝滑手势滑屏与选句。
 * 2. 【自由拉伸缩放】：右下角搭载无感拉伸角标 (Corner Resize Grip)，支持随时拖动动态调整浮窗宽高，
 *    自动记忆用户习惯尺寸。
 * 3. 【一键悬浮球收折】：录制前准备时一键收缩至 48dp 晶体悬浮球贴边待命，轻触瞬间复原展开。
 * 4. 【去文字纯图标】：左上角极简退出，顶部去标题纯净通透，底部全图标触控。
 * 5. 【离线安全零网络】：100% 离线，无任何广告、VIP、网络通信权限，完全合规。
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

        EasyFloat.with(activity.getApplicationContext())
                .setTag(FLOAT_TAG)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setSidePattern(SidePattern.DEFAULT)
                .setDragEnable(false) // 关键：关闭 EasyFloat 全局抢占手势，由专用把手独立负责拖动
                .setImmersionStatusBar(true)
                .setLayout(com.teleprompter.pure.R.layout.float_window_modern, new OnInvokeView() {
                    @Override
                    public void invoke(View view) {
                        mRootFloatView = view;
                        bindViewEvents(activity, view);
                    }
                })
                .show();
    }

    private void bindViewEvents(Activity activity, View rootFloatView) {
        mPrompterCard = rootFloatView.findViewById(com.teleprompter.pure.R.id.prompter_card_container);
        mFloatBallContainer = rootFloatView.findViewById(com.teleprompter.pure.R.id.float_ball_container);
        mScrollView = rootFloatView.findViewById(com.teleprompter.pure.R.id.text_scroll_view);
        mTvCountdownOverlay = rootFloatView.findViewById(com.teleprompter.pure.R.id.tv_countdown_overlay);
        mBtnPlayPause = rootFloatView.findViewById(com.teleprompter.pure.R.id.btn_play_pause);

        View headerDragArea = rootFloatView.findViewById(com.teleprompter.pure.R.id.header_drag_area);
        View dragTouchWrapper = rootFloatView.findViewById(com.teleprompter.pure.R.id.drag_handle_touch_wrapper);
        ImageView btnClose = rootFloatView.findViewById(com.teleprompter.pure.R.id.btn_close);
        ImageView btnMinimize = rootFloatView.findViewById(com.teleprompter.pure.R.id.btn_minimize);
        ImageView btnSettings = rootFloatView.findViewById(com.teleprompter.pure.R.id.btn_settings);
        ImageView btnReset = rootFloatView.findViewById(com.teleprompter.pure.R.id.btn_reset);
        ImageView btnScaleCorner = rootFloatView.findViewById(com.teleprompter.pure.R.id.btn_scale_corner);

        ImageView tabLeft = rootFloatView.findViewById(com.teleprompter.pure.R.id.tab_align_left);
        ImageView tabCenter = rootFloatView.findViewById(com.teleprompter.pure.R.id.tab_align_center);
        ImageView tabRight = rootFloatView.findViewById(com.teleprompter.pure.R.id.tab_align_right);

        // 1. 恢复用户保存的窗口尺寸或默认尺寸
        restoreSavedWindowDimensions(activity);

        // 2. 初始化台词数据
        if (mCurrentScript != null) {
            mScrollView.setText(mCurrentScript.content);
            mScrollView.setScrollSpeed(mCurrentScript.scrollSpeed);
            mScrollView.setTextSize(mCurrentScript.fontSize);
            mScrollView.setTextAlignmentMode(mCurrentScript.alignment);
            mCurrentOpacity = mCurrentScript.opacity;
            applyBackgroundStyle();
        }

        // 3. 初始对齐 UI 高亮
        updateAlignmentIconUI(tabLeft, tabCenter, tabRight, mScrollView.getCurrentAlignment());

        // 4. 状态监听器
        mScrollView.setOnPrompterStateListener(new SmoothTextScrollView.OnPrompterStateListener() {
            @Override
            public void onPlayStateChanged(boolean isPlaying) {
                mBtnPlayPause.setImageResource(isPlaying ? com.teleprompter.pure.R.drawable.ic_pause : com.teleprompter.pure.R.drawable.ic_play);
            }

            @Override
            public void onScrollPositionChanged(int currentY, int maxY) {}

            @Override
            public void onReset() {
                mBtnPlayPause.setImageResource(com.teleprompter.pure.R.drawable.ic_play);
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

        // 5. 【核心交互 1】：顶部把手原生 WindowManager 极速平滑拖拽
        setupWindowDragListener(activity, rootFloatView, headerDragArea, dragTouchWrapper);

        // 6. 【核心交互 2】：右下角无感拉伸缩放把手 (Corner Resize Handle)
        setupResizeCornerListener(activity, btnScaleCorner);

        // 7. 【核心交互 3】：一键最小化折叠成 48dp 悬浮球
        btnMinimize.setOnClickListener(v -> minimizeToFloatBall());

        // 8. 【核心交互 4】：悬浮球拖拽移动与轻触展开
        setupFloatBallTouchListener(activity, rootFloatView);

        // 9. 播放/暂停
        mBtnPlayPause.setOnClickListener(v -> mScrollView.togglePlayPause());

        // 10. 一键重置回到首句
        btnReset.setOnClickListener(v -> mScrollView.resetToStart(true));

        // 11. 纯图标对齐切换
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

        // 12. 左上角退出
        btnClose.setOnClickListener(v -> dismiss());

        // 13. 右上角设置
        btnSettings.setOnClickListener(v -> showSettingsDialog(activity));
    }

    /**
     * 原生 WindowManager 顶部手柄平滑拖拽，零手势竞争
     */
    private void setupWindowDragListener(Activity activity, View rootFloatView, View headerArea, View dragTouchWrapper) {
        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private float startTouchX, startTouchY;
            private int startWindowX, startWindowY;
            private WindowManager wm;
            private WindowManager.LayoutParams wmParams;
            private View windowRoot;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTouchX = event.getRawX();
                        startTouchY = event.getRawY();
                        windowRoot = rootFloatView.getRootView();
                        if (windowRoot != null && windowRoot.getLayoutParams() instanceof WindowManager.LayoutParams) {
                            wmParams = (WindowManager.LayoutParams) windowRoot.getLayoutParams();
                            startWindowX = wmParams.x;
                            startWindowY = wmParams.y;
                            wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        if (wm != null && wmParams != null && windowRoot != null) {
                            float dx = event.getRawX() - startTouchX;
                            float dy = event.getRawY() - startTouchY;
                            wmParams.x = (int) (startWindowX + dx);
                            wmParams.y = (int) (startWindowY + dy);
                            wm.updateViewLayout(windowRoot, wmParams);
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
        dragTouchWrapper.setOnTouchListener(dragListener);
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
                        // 保存用户缩放后的尺寸
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
    private void setupFloatBallTouchListener(Activity activity, View rootFloatView) {
        int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();

        mFloatBallContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startTouchX, startTouchY;
            private int startWindowX, startWindowY;
            private boolean isDragged = false;
            private WindowManager wm;
            private WindowManager.LayoutParams wmParams;
            private View windowRoot;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startTouchX = event.getRawX();
                        startTouchY = event.getRawY();
                        isDragged = false;
                        windowRoot = rootFloatView.getRootView();
                        if (windowRoot != null && windowRoot.getLayoutParams() instanceof WindowManager.LayoutParams) {
                            wmParams = (WindowManager.LayoutParams) windowRoot.getLayoutParams();
                            startWindowX = wmParams.x;
                            startWindowY = wmParams.y;
                            wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startTouchX;
                        float dy = event.getRawY() - startTouchY;
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            isDragged = true;
                        }
                        if (isDragged && wm != null && wmParams != null && windowRoot != null) {
                            wmParams.x = (int) (startWindowX + dx);
                            wmParams.y = (int) (startWindowY + dy);
                            wm.updateViewLayout(windowRoot, wmParams);
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

    /**
     * 最小化为悬浮球
     */
    public void minimizeToFloatBall() {
        if (mPrompterCard != null && mFloatBallContainer != null) {
            mPrompterCard.setVisibility(View.GONE);
            mFloatBallContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 从悬浮球恢复展开
     */
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
        l.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_LEFT ? com.teleprompter.pure.R.drawable.bg_segment_active_pill : 0);
        l.setColorFilter(mode == SmoothTextScrollView.ALIGN_LEFT ? Color.WHITE : 0x80FFFFFF);

        c.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_CENTER ? com.teleprompter.pure.R.drawable.bg_segment_active_pill : 0);
        c.setColorFilter(mode == SmoothTextScrollView.ALIGN_CENTER ? Color.WHITE : 0x80FFFFFF);

        r.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_RIGHT ? com.teleprompter.pure.R.drawable.bg_segment_active_pill : 0);
        r.setColorFilter(mode == SmoothTextScrollView.ALIGN_RIGHT ? Color.WHITE : 0x80FFFFFF);
    }

    private void showSettingsDialog(Activity activity) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View dialogView = LayoutInflater.from(activity).inflate(com.teleprompter.pure.R.layout.dialog_prompter_settings, null);
        dialog.setContentView(dialogView);

        // 1. 文字颜色 (含黑、白、黄、绿、青、橙、粉)
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_white).setOnClickListener(v -> mScrollView.setTextColor(Color.WHITE));
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_black).setOnClickListener(v -> mScrollView.setTextColor(Color.BLACK));
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_yellow).setOnClickListener(v -> mScrollView.setTextColor(0xFFFFEE58));
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_green).setOnClickListener(v -> mScrollView.setTextColor(0xFF38EF7D));
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_cyan).setOnClickListener(v -> mScrollView.setTextColor(0xFF00E5FF));
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_orange).setOnClickListener(v -> mScrollView.setTextColor(0xFFFF9100));
        dialogView.findViewById(com.teleprompter.pure.R.id.color_text_pink).setOnClickListener(v -> mScrollView.setTextColor(0xFFFF4081));

        // 2. 背景底色
        dialogView.findViewById(com.teleprompter.pure.R.id.color_bg_dark).setOnClickListener(v -> { mCurrentBgColor = 0xFF12141A; applyBackgroundStyle(); });
        dialogView.findViewById(com.teleprompter.pure.R.id.color_bg_white).setOnClickListener(v -> { mCurrentBgColor = 0xFFFFFFFF; applyBackgroundStyle(); });
        dialogView.findViewById(com.teleprompter.pure.R.id.color_bg_navy).setOnClickListener(v -> { mCurrentBgColor = 0xFF0D1B2A; applyBackgroundStyle(); });
        dialogView.findViewById(com.teleprompter.pure.R.id.color_bg_green).setOnClickListener(v -> { mCurrentBgColor = 0xFF0C1E14; applyBackgroundStyle(); });
        dialogView.findViewById(com.teleprompter.pure.R.id.color_bg_gray).setOnClickListener(v -> { mCurrentBgColor = 0xFF282A36; applyBackgroundStyle(); });

        // 3. 透明度
        SeekBar sbOpacity = dialogView.findViewById(com.teleprompter.pure.R.id.sb_opacity);
        TextView tvOpacity = dialogView.findViewById(com.teleprompter.pure.R.id.tv_opacity_val);
        sbOpacity.setProgress(mCurrentOpacity);
        tvOpacity.setText(mCurrentOpacity + "%");
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) {
                mCurrentOpacity = p;
                tvOpacity.setText(p + "%");
                applyBackgroundStyle();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 4. 速度
        SeekBar sbSpeed = dialogView.findViewById(com.teleprompter.pure.R.id.sb_speed);
        TextView tvSpeed = dialogView.findViewById(com.teleprompter.pure.R.id.tv_speed_val);
        sbSpeed.setProgress(mScrollView.getScrollSpeed());
        tvSpeed.setText(String.valueOf(mScrollView.getScrollSpeed()));
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean b) {
                mScrollView.setScrollSpeed(p);
                tvSpeed.setText(String.valueOf(p));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 5. 镜像翻转
        SwitchCompat switchMirror = dialogView.findViewById(com.teleprompter.pure.R.id.switch_mirror);
        switchMirror.setChecked(mScrollView.isMirror());
        switchMirror.setOnCheckedChangeListener((btn, isChecked) -> mScrollView.setMirror(isChecked));

        // 6. 倒计时
        SwitchCompat switchCountdown = dialogView.findViewById(com.teleprompter.pure.R.id.switch_countdown);
        switchCountdown.setChecked(mScrollView.getCountdownSeconds() > 0);
        switchCountdown.setOnCheckedChangeListener((btn, isChecked) -> mScrollView.setCountdownSeconds(isChecked ? 3 : 0));

        dialog.show();
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

            // 微弱半透明描边，强化晶体感
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
