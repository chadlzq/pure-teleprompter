package com.teleprompter.pure.ui;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

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
 * 3. 3-2-1 准备倒计时
 * 4. 光学分光镜镜像翻转 (一键左右翻转)
 * 5. 手势自由滑词定位与一键回到首句
 */
public class PromptBoardActivity extends AppCompatActivity {

    public static final String EXTRA_SCRIPT_ID = "extra_script_id";

    private SmoothTextScrollView mScrollView;
    private TextView mTvTitle;
    private TextView mTvCountdown;
    private TextView mBtnMirror;
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
        setContentView(com.teleprompter.pure.R.layout.activity_prompt_board);

        mScriptId = getIntent().getLongExtra(EXTRA_SCRIPT_ID, 0L);

        initViews();
        setupListeners();
        loadScript();
    }

    private void initViews() {
        mScrollView = findViewById(com.teleprompter.pure.R.id.board_scroll_view);
        mTvTitle = findViewById(com.teleprompter.pure.R.id.tv_board_title);
        mTvCountdown = findViewById(com.teleprompter.pure.R.id.tv_board_countdown);
        mBtnMirror = findViewById(com.teleprompter.pure.R.id.btn_toggle_mirror);
        mBtnReset = findViewById(com.teleprompter.pure.R.id.btn_board_reset);
        mBtnPlayPause = findViewById(com.teleprompter.pure.R.id.btn_board_play_pause);
        mBtnBack = findViewById(com.teleprompter.pure.R.id.btn_board_back);

        mTabLeft = findViewById(com.teleprompter.pure.R.id.board_tab_left);
        mTabCenter = findViewById(com.teleprompter.pure.R.id.board_tab_center);
        mTabRight = findViewById(com.teleprompter.pure.R.id.board_tab_right);

        // 默认居中对齐、3秒倒计时
        mScrollView.setTextAlignmentMode(SmoothTextScrollView.ALIGN_CENTER);
        mScrollView.setCountdownSeconds(3);
    }

    private void setupListeners() {
        mBtnBack.setOnClickListener(v -> finish());

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
                mBtnPlayPause.setImageResource(isPlaying ? com.teleprompter.pure.R.drawable.ic_pause : com.teleprompter.pure.R.drawable.ic_play);
            }

            @Override
            public void onScrollPositionChanged(int currentY, int maxY) {}

            @Override
            public void onReset() {
                mBtnPlayPause.setImageResource(com.teleprompter.pure.R.drawable.ic_play);
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

    private void setAlignmentUI(int mode) {
        mScrollView.setTextAlignmentMode(mode);
        mTabLeft.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_LEFT ? com.teleprompter.pure.R.drawable.bg_segment_active_pill : 0);
        mTabCenter.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_CENTER ? com.teleprompter.pure.R.drawable.bg_segment_active_pill : 0);
        mTabRight.setBackgroundResource(mode == SmoothTextScrollView.ALIGN_RIGHT ? com.teleprompter.pure.R.drawable.bg_segment_active_pill : 0);
    }

    private void loadScript() {
        if (mScriptId > 0) {
            Executors.newSingleThreadExecutor().execute(() -> {
                mScript = AppDatabase.getInstance(this).scriptDao().getScriptById(mScriptId);
                if (mScript != null) {
                    runOnUiThread(() -> {
                        mTvTitle.setText(mScript.title);
                        mScrollView.setText(mScript.content);
                        mScrollView.setScrollSpeed(mScript.scrollSpeed);
                        mScrollView.setTextSize(mScript.fontSize);
                    });
                }
            });
        }
    }
}
