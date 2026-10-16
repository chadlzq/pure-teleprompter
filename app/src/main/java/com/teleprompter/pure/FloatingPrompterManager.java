package com.teleprompter.pure;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.enums.SidePattern;
import com.lzf.easyfloat.interfaces.OnPermissionResult;
import com.teleprompter.pure.database.ScriptEntity;
import com.teleprompter.pure.ui.SmoothTextScrollView;

/**
 * 悬浮提词器全局管理器 (FloatingPrompterManager)
 * 极致纯净版：
 * 1. 顶部仅保留左上角关闭与右上角设置，无中间标题遮挡
 * 2. 底部全图标操作 (左对齐/居中/右对齐、重置首句、播放暂停)
 * 3. 丰富的文字调色盘 (含纯黑) 与背景底色调配
 */
public class FloatingPrompterManager {

    private static final String FLOAT_TAG = "TELEPROMPTER_FLOAT";
    private static volatile FloatingPrompterManager sInstance;

    private SmoothTextScrollView mScrollView;
    private View mRootFloatView;
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
        if (!EasyFloat.checkPermission(activity)) {
            EasyFloat.requestPermission(activity, new OnPermissionResult() {
                @Override
                public void permissionResult(boolean isOpen) {
                    if (isOpen) {
                        initAndShow(activity);
                    } else {
                        Toast.makeText(activity, "请授予悬浮窗权限，以便在录制视频时浮窗提词！", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } else {
            initAndShow(activity);
        }
    }

    private void initAndShow(Activity activity) {
        EasyFloat.with(activity.getApplicationContext())
                .setTag(FLOAT_TAG)
                .setShowPattern(ShowPattern.ALL_TIME)
                .setSidePattern(SidePattern.RESULT_HORIZONTAL)
                .setLayout(com.teleprompter.pure.R.layout.float_window_modern, view -> {
                    mRootFloatView = view;
                    bindViewEvents(activity, view);
                })
                .show();
    }

    private void bindViewEvents(Activity activity, View floatView) {
        mScrollView = floatView.findViewById(com.teleprompter.pure.R.id.text_scroll_view);
        mTvCountdownOverlay = floatView.findViewById(com.teleprompter.pure.R.id.tv_countdown_overlay);
        mBtnPlayPause = floatView.findViewById(com.teleprompter.pure.R.id.btn_play_pause);
        ImageView btnClose = floatView.findViewById(com.teleprompter.pure.R.id.btn_close);
        ImageView btnSettings = floatView.findViewById(com.teleprompter.pure.R.id.btn_settings);
        ImageView btnReset = floatView.findViewById(com.teleprompter.pure.R.id.btn_reset);

        ImageView tabLeft = floatView.findViewById(com.teleprompter.pure.R.id.tab_align_left);
        ImageView tabCenter = floatView.findViewById(com.teleprompter.pure.R.id.tab_align_center);
        ImageView tabRight = floatView.findViewById(com.teleprompter.pure.R.id.tab_align_right);

        // 初始化台词数据
        if (mCurrentScript != null) {
            mScrollView.setText(mCurrentScript.content);
            mScrollView.setScrollSpeed(mCurrentScript.scrollSpeed);
            mScrollView.setTextSize(mCurrentScript.fontSize);
            mScrollView.setTextAlignmentMode(mCurrentScript.alignment);
            mCurrentOpacity = mCurrentScript.opacity;
            applyBackgroundStyle();
        }

        // 默认居中对齐 UI 高亮
        updateAlignmentIconUI(tabLeft, tabCenter, tabRight, mScrollView.getCurrentAlignment());

        // 状态监听器
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

        // 播放/暂停
        mBtnPlayPause.setOnClickListener(v -> mScrollView.togglePlayPause());

        // 一键重置
        btnReset.setOnClickListener(v -> mScrollView.resetToStart(true));

        // 纯图标对齐切换
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

        // 左上角退出
        btnClose.setOnClickListener(v -> dismiss());

        // 右上角设置
        btnSettings.setOnClickListener(v -> showSettingsDialog(activity));
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
        if (mRootFloatView != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(dp2px(mRootFloatView.getContext(), 20));
            
            int alpha = (int) (mCurrentOpacity * 2.55f);
            int r = Color.red(mCurrentBgColor);
            int g = Color.green(mCurrentBgColor);
            int b = Color.blue(mCurrentBgColor);
            drawable.setColor(Color.argb(alpha, r, g, b));
            drawable.setStroke(dp2px(mRootFloatView.getContext(), 1), Color.argb(40, 255, 255, 255));

            mRootFloatView.setBackground(drawable);
        }
    }

    private int dp2px(android.content.Context ctx, float dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    public void dismiss() {
        if (mScrollView != null) {
            mScrollView.pauseScroll();
        }
        EasyFloat.dismiss(FLOAT_TAG);
    }
}
