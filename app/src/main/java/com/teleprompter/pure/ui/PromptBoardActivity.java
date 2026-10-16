package com.teleprompter.pure.ui;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.teleprompter.pure.PrompterPreferences;
import com.teleprompter.pure.R;
import com.teleprompter.pure.database.AppDatabase;
import com.teleprompter.pure.database.ScriptEntity;

import java.util.concurrent.Executors;

/**
 * 全屏演播室提词大板 (PromptBoardActivity)
 * 专为讲座、直播机位、分光镜外挂大屏打造
 * 
 * 核心特性：
 * 1. 纯黑 OLED 节能抗反光背景
 * 2. 屏幕常亮 (FLAG_KEEP_SCREEN_ON) 绝不灭屏
 * 3. 视觉黄金参考线精准垂直居中，首句启动即居中对齐
 * 4. 光学分光镜镜像翻转 (一键左右翻转)
 * 5. 一键屏幕旋转 (横竖屏快速切换)
 * 6. 手势自由滑词定位与一键回到首句
 */
public class PromptBoardActivity extends AppCompatActivity {

    public static final String EXTRA_SCRIPT_ID = "extra_script_id";

    private SmoothTextScrollView mScrollView;
    private TextView mTvTitle;
    private TextView mTvCountdown;
    private TextView mBtnMirror;
    private TextView mBtnRotate;
    private ImageView mBtnReset;
    private ImageView mBtnPlayPause;
    private View mBtnBack;

    private TextView mTabLeft, mTabCenter, mTabRight;

    private long mScriptId = 0L;
    private ScriptEntity mScript;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_prompt_board);

        mScriptId = getIntent().getLongExtra(EXTRA_SCRIPT_ID, 0L);

        initViews();
        setupListeners();
        loadScript();
    }

    private void initViews() {
        mScrollView = findViewById(R.id.board_scroll_view);
        mTvTitle = findViewById(R.id.tv_board_title);
        mTvCountdown = findViewById(R.id.tv_board_countdown);
        mBtnMirror = findViewById(R.id.btn_toggle_mirror);
        mBtnRotate = findViewById(R.id.btn_toggle_orientation);
        mBtnReset = findViewById(R.id.btn_board_reset);
        mBtnPlayPause = findViewById(R.id.btn_board_play_pause);
        mBtnBack = findViewById(R.id.btn_board_back);

        mTabLeft = findViewById(R.id.board_tab_left);
        mTabCenter = findViewById(R.id.board_tab_center);
        mTabRight = findViewById(R.id.board_tab_right);

        // 默认居中对齐
        mScrollView.setTextAlignmentMode(SmoothTextScrollView.ALIGN_CENTER);

        // 确保播放/暂停按钮图标为醒目纯黑，绝不空白
        mBtnPlayPause.setColorFilter(Color.BLACK);

        // 读取全局偏好配置
        int speed = PrompterPreferences.getScrollSpeed(this);
        float fontSize = PrompterPreferences.getFontSize(this);
        int textColor = PrompterPreferences.getTextColor(this);
        int countdown = PrompterPreferences.getCountdownSec(this);
        boolean mirror = PrompterPreferences.isMirror(this);

        mScrollView.setScrollSpeed(speed);
        mScrollView.setTextSize(fontSize);
        mScrollView.setTextColor(textColor);
        mScrollView.setCountdownSeconds(countdown);
        mScrollView.setMirror(mirror);

        mBtnMirror.setTextColor(mirror ? 0xFF38EF7D : 0xFFFFFFFF);
    }

    private void setupListeners() {
        mBtnBack.setOnClickListener(v -> finish());

        // 旋转屏幕 (横竖屏快速无感切换)
        mBtnRotate.setOnClickListener(v -> toggleScreenOrientation());

        // 镜像翻转
        mBtnMirror.setOnClickListener(v -> {
            mScrollView.toggleMirror();
            boolean isMirror = mScrollView.isMirror();
            mBtnMirror.setTextColor(isMirror ? 0xFF38EF7D : 0xFFFFFFFF);
            Toast.makeText(this, isMirror ? "已开启分光镜镜像翻转" : "已恢复正常显示", Toast.LENGTH_SHORT).show();
        });

        // 重置首句
        mBtnReset.setOnClickListener(v -> mScrollView.resetToStart(true));

        // 播放/暂停
        mBtnPlayPause.setOnClickListener(v -> mScrollView.togglePlayPause());

        // 监听提词器状态（倒计时显示、播放键图标同步）
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
                mTvCountdown.setVisibility(View.GONE);
            }

            @Override
            public void onCountdownTick(int remainingSeconds) {
                mTvCountdown.setVisibility(View.VISIBLE);
                mTvCountdown.setText(String.valueOf(remainingSeconds));
            }

            @Override
            public void onCountdownFinished() {
                mTvCountdown.setVisibility(View.GONE);
            }
        });

        // 对齐方式切换
        mTabLeft.setOnClickListener(v -> setAlignmentUI(SmoothTextScrollView.ALIGN_LEFT));
        mTabCenter.setOnClickListener(v -> setAlignmentUI(SmoothTextScrollView.ALIGN_CENTER));
        mTabRight.setOnClickListener(v -> setAlignmentUI(SmoothTextScrollView.ALIGN_RIGHT));
    }

    private void toggleScreenOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            Toast.makeText(this, "已旋转为竖屏显示", Toast.LENGTH_SHORT).show();
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            Toast.makeText(this, "已旋转为横屏显示", Toast.LENGTH_SHORT).show();
        }
    }

    private void setAlignmentUI(int mode) {
        mScrollView.setTextAlignmentMode(mode);
        mTabLeft.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_LEFT ? R.drawable.bg_segment_active_pill : 0);
        mTabCenter.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_CENTER ? R.drawable.bg_segment_active_pill : 0);
        mTabRight.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_RIGHT ? R.drawable.bg_segment_active_pill : 0);
    }

    private void loadScript() {
        if (mScriptId > 0) {
            Executors.newSingleThreadExecutor().execute(() -> {
                mScript = AppDatabase.getInstance(this).scriptDao().getScriptById(mScriptId);
                if (mScript != null) {
                    runOnUiThread(() -> {
                        mTvTitle.setText(mScript.title);
                        mScrollView.setText(mScript.content);
                    });
                }
            });
        }
    }
}
