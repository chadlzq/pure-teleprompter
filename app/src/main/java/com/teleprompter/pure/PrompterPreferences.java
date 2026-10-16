package com.teleprompter.pure;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 全局提词器配置参数持久化中心 (PrompterPreferences)
 * 纯本地 SharedPreferences 存储，无隐私风险，多界面即时同步
 */
public class PrompterPreferences {

    private static final String PREF_NAME = "teleprompter_pure_settings";

    public static final String KEY_FONT_SIZE = "key_font_size";
    public static final String KEY_TEXT_COLOR = "key_text_color";
    public static final String KEY_BG_COLOR = "key_bg_color";
    public static final String KEY_BG_OPACITY = "key_bg_opacity";
    public static final String KEY_SCROLL_SPEED = "key_scroll_speed";
    public static final String KEY_COUNTDOWN_SEC = "key_countdown_sec";
    public static final String KEY_MIRROR = "key_mirror";

    // 默认值规范
    public static final float DEFAULT_FONT_SIZE = 24.0f;
    public static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF; // 纯白
    public static final int DEFAULT_BG_COLOR = 0xFF12141A;   // 经典黑沉浸
    public static final int DEFAULT_BG_OPACITY = 75;         // 75%
    public static final int DEFAULT_SCROLL_SPEED = 55;       // 默认速度适度加快，适配正常口播节奏
    public static final int DEFAULT_COUNTDOWN_SEC = 0;       // 321倒计时默认关闭 (0s)
    public static final boolean DEFAULT_MIRROR = false;      // 镜像默认关闭

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static float getFontSize(Context context) {
        return getPrefs(context).getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }

    public static void setFontSize(Context context, float size) {
        getPrefs(context).edit().putFloat(KEY_FONT_SIZE, size).apply();
    }

    public static int getTextColor(Context context) {
        return getPrefs(context).getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR);
    }

    public static void setTextColor(Context context, int color) {
        getPrefs(context).edit().putInt(KEY_TEXT_COLOR, color).apply();
    }

    public static int getBgColor(Context context) {
        return getPrefs(context).getInt(KEY_BG_COLOR, DEFAULT_BG_COLOR);
    }

    public static void setBgColor(Context context, int color) {
        getPrefs(context).edit().putInt(KEY_BG_COLOR, color).apply();
    }

    public static int getBgOpacity(Context context) {
        return getPrefs(context).getInt(KEY_BG_OPACITY, DEFAULT_BG_OPACITY);
    }

    public static void setBgOpacity(Context context, int opacity) {
        getPrefs(context).edit().putInt(KEY_BG_OPACITY, opacity).apply();
    }

    public static int getScrollSpeed(Context context) {
        return getPrefs(context).getInt(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED);
    }

    public static void setScrollSpeed(Context context, int speed) {
        getPrefs(context).edit().putInt(KEY_SCROLL_SPEED, speed).apply();
    }

    public static int getCountdownSec(Context context) {
        return getPrefs(context).getInt(KEY_COUNTDOWN_SEC, DEFAULT_COUNTDOWN_SEC);
    }

    public static void setCountdownSec(Context context, int sec) {
        getPrefs(context).edit().putInt(KEY_COUNTDOWN_SEC, sec).apply();
    }

    public static boolean isMirror(Context context) {
        return getPrefs(context).getBoolean(KEY_MIRROR, DEFAULT_MIRROR);
    }

    public static void setMirror(Context context, boolean mirror) {
        getPrefs(context).edit().putBoolean(KEY_MIRROR, mirror).apply();
    }

    /**
     * 一键重置所有设置为默认值
     */
    public static void resetDefaults(Context context) {
        getPrefs(context).edit().clear().apply();
    }
}
