package com.teleprompter.pure.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.teleprompter.pure.PrompterPreferences;
import com.teleprompter.pure.R;

/**
 * 专设应用内设置页面 (SettingsActivity)
 * 解决悬浮窗上打开设置会遮挡且抛出 WindowToken 异常的问题
 * 所有参数统一写入 PrompterPreferences，全局自动生效
 */
public class SettingsActivity extends AppCompatActivity {

    private FrameLayout mPreviewCard;
    private TextView mTvPreviewText;

    private SeekBar mSbFontSize;
    private TextView mTvFontSizeVal;

    private SeekBar mSbOpacity;
    private TextView mTvOpacityVal;

    private SeekBar mSbSpeed;
    private TextView mTvSpeedVal;

    private SwitchCompat mSwitchCountdown;
    private SwitchCompat mSwitchMirror;

    private int mCurrentTextColor;
    private int mCurrentBgColor;
    private int mCurrentOpacity;
    private float mCurrentFontSize;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        loadCurrentSettings();
        setupListeners();
    }

    private void initViews() {
        mPreviewCard = findViewById(R.id.preview_card_container);
        mTvPreviewText = findViewById(R.id.tv_preview_text);

        mSbFontSize = findViewById(R.id.sb_font_size);
        mTvFontSizeVal = findViewById(R.id.tv_font_size_val);

        mSbOpacity = findViewById(R.id.sb_opacity);
        mTvOpacityVal = findViewById(R.id.tv_opacity_val);

        mSbSpeed = findViewById(R.id.sb_speed);
        mTvSpeedVal = findViewById(R.id.tv_speed_val);

        mSwitchCountdown = findViewById(R.id.switch_countdown);
        mSwitchMirror = findViewById(R.id.switch_mirror);
    }

    private void loadCurrentSettings() {
        mCurrentFontSize = PrompterPreferences.getFontSize(this);
        mCurrentTextColor = PrompterPreferences.getTextColor(this);
        mCurrentBgColor = PrompterPreferences.getBgColor(this);
        mCurrentOpacity = PrompterPreferences.getBgOpacity(this);
        int speed = PrompterPreferences.getScrollSpeed(this);
        int countdownSec = PrompterPreferences.getCountdownSec(this);
        boolean mirror = PrompterPreferences.isMirror(this);

        // 初始化字号
        mSbFontSize.setProgress((int) mCurrentFontSize);
        mTvFontSizeVal.setText(((int) mCurrentFontSize) + " sp");

        // 初始化透明度
        mSbOpacity.setProgress(mCurrentOpacity);
        mTvOpacityVal.setText(mCurrentOpacity + "%");

        // 初始化速度
        mSbSpeed.setProgress(speed);
        mTvSpeedVal.setText(speed + " px/s");

        // 初始化开关
        mSwitchCountdown.setChecked(countdownSec > 0);
        mSwitchMirror.setChecked(mirror);

        // 更新预览卡片
        updatePreview();
    }

    private void setupListeners() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 恢复默认
        findViewById(R.id.btn_reset_defaults).setOnClickListener(v -> {
            PrompterPreferences.resetDefaults(this);
            loadCurrentSettings();
            Toast.makeText(this, "已恢复初始默认配置", Toast.LENGTH_SHORT).show();
        });

        // 字号调整
        mSbFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = Math.max(16, progress);
                mCurrentFontSize = size;
                mTvFontSizeVal.setText(size + " sp");
                PrompterPreferences.setFontSize(SettingsActivity.this, size);
                updatePreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 文字颜色按钮组
        findViewById(R.id.color_txt_white).setOnClickListener(v -> selectTextColor(Color.WHITE));
        findViewById(R.id.color_txt_black).setOnClickListener(v -> selectTextColor(Color.BLACK));
        findViewById(R.id.color_txt_yellow).setOnClickListener(v -> selectTextColor(0xFFFFEE58));
        findViewById(R.id.color_txt_green).setOnClickListener(v -> selectTextColor(0xFF38EF7D));
        findViewById(R.id.color_txt_cyan).setOnClickListener(v -> selectTextColor(0xFF00E5FF));
        findViewById(R.id.color_txt_orange).setOnClickListener(v -> selectTextColor(0xFFFF9100));
        findViewById(R.id.color_txt_pink).setOnClickListener(v -> selectTextColor(0xFFFF4081));

        // 背景颜色按钮组
        findViewById(R.id.color_bg_dark).setOnClickListener(v -> selectBgColor(0xFF12141A));
        findViewById(R.id.color_bg_white).setOnClickListener(v -> selectBgColor(0xFFFFFFFF));
        findViewById(R.id.color_bg_navy).setOnClickListener(v -> selectBgColor(0xFF0D1B2A));
        findViewById(R.id.color_bg_green).setOnClickListener(v -> selectBgColor(0xFF0C1E14));
        findViewById(R.id.color_bg_gray).setOnClickListener(v -> selectBgColor(0xFF282A36));

        // 透明度调整
        mSbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentOpacity = progress;
                mTvOpacityVal.setText(progress + "%");
                PrompterPreferences.setBgOpacity(SettingsActivity.this, progress);
                updatePreview();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 速度调整
        mSbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int speed = Math.max(10, progress);
                mTvSpeedVal.setText(speed + " px/s");
                PrompterPreferences.setScrollSpeed(SettingsActivity.this, speed);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 倒计时开关
        mSwitchCountdown.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrompterPreferences.setCountdownSec(this, isChecked ? 3 : 0);
        });

        // 镜像翻转开关
        mSwitchMirror.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PrompterPreferences.setMirror(this, isChecked);
            updatePreview();
        });

        // 联系作者 (发邮件至 ichad@qq.com)
        findViewById(R.id.item_contact).setOnClickListener(v -> sendEmailToAuthor());

        // 隐私政策与免责声明
        findViewById(R.id.item_privacy).setOnClickListener(v -> showPrivacyDialog());

        // 关于软件版本
        findViewById(R.id.item_about).setOnClickListener(v -> showAboutDialog());
    }

    private void sendEmailToAuthor() {
        String recipient = "ichad@qq.com";
        String subject = "【极简提词器】与作者沟通交流";
        String deviceInfo = "\n\n---------------------\n设备机型：" + Build.MANUFACTURER + " " + Build.MODEL +
                "\n系统版本：Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")" +
                "\n应用版本：v1.1.0\n---------------------\n";

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + recipient));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, "您好！我想向您交流反馈：" + deviceInfo);

        try {
            startActivity(Intent.createChooser(intent, "选择邮件客户端联系作者"));
        } catch (Exception e) {
            copyToClipboard(recipient);
            Toast.makeText(this, "作者邮箱已复制：" + recipient, Toast.LENGTH_LONG).show();
        }
    }

    private void showPrivacyDialog() {
        new AlertDialog.Builder(this)
                .setTitle("隐私政策与安全规范声明")
                .setMessage("【极简提词器 · 100% 纯本地离线承诺】\n\n" +
                        "1. 绝不联网：本应用安装包未申请任何 INTERNET 网络权限，从底层硬件级别杜绝数据外泄可能。\n\n" +
                        "2. 本地私有存储：您创建、编辑、导入的所有台词文稿，仅加密保存在手机本地 Room SQLite 数据库中，卸载应用即同步销毁，绝无云端备份。\n\n" +
                        "3. 零数据采集：不获取手机设备号 (IMEI/OAID/Android ID)、不读取位置与通讯录、不嵌入任何第三方广告或统计分析 SDK。\n\n" +
                        "4. 必要权限说明：仅需系统悬浮窗权限（用于在录像或直播软件上层浮窗显示提示词），以及导入标准 TXT 文本时的系统文件访问权限。\n\n" +
                        "符合国内应用商店（酷安、华为、小米、OPPO、vivo 等）上架合规要求。")
                .setPositiveButton("我已了解", null)
                .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于 极简提词器")
                .setMessage("极简提词器 (Pure Teleprompter)\n" +
                        "版本：v1.1.0\n" +
                        "开源许可：Apache-2.0 License\n\n" +
                        "为个人创作者、短视频口播、演播主持打造的专注利器：\n" +
                        "• 纯本地离线单机，永无广告与会员套路\n" +
                        "• 全屏大字演播板 + 悬浮窗自适应旋转\n" +
                        "• 光学分光镜水平镜像翻转支持\n" +
                        "• 流畅恒速平滑跟读引擎\n\n" +
                        "作者邮箱：ichad@qq.com")
                .setPositiveButton("确定", null)
                .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("pure_teleprompter", text);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void selectTextColor(int color) {
        mCurrentTextColor = color;
        PrompterPreferences.setTextColor(this, color);
        updatePreview();
    }

    private void selectBgColor(int color) {
        mCurrentBgColor = color;
        PrompterPreferences.setBgColor(this, color);
        updatePreview();
    }

    private void updatePreview() {
        if (mTvPreviewText != null) {
            mTvPreviewText.setTextSize(mCurrentFontSize);
            mTvPreviewText.setTextColor(mCurrentTextColor);
            mTvPreviewText.setScaleX(PrompterPreferences.isMirror(this) ? -1f : 1f);
        }

        if (mPreviewCard != null) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(dp2px(16));

            int alpha = (int) ((mCurrentOpacity / 100.0f) * 255);
            int r = Color.red(mCurrentBgColor);
            int g = Color.green(mCurrentBgColor);
            int b = Color.blue(mCurrentBgColor);
            drawable.setColor(Color.argb(alpha, r, g, b));
            drawable.setStroke(dp2px(1), Color.argb(60, 255, 255, 255));

            mPreviewCard.setBackground(drawable);
        }
    }

    private int dp2px(float dp) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }
}
