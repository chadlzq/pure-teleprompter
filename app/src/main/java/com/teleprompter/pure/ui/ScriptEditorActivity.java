package com.teleprompter.pure.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.teleprompter.pure.FloatingPrompterManager;
import com.teleprompter.pure.PrompterPreferences;
import com.teleprompter.pure.R;
import com.teleprompter.pure.database.AppDatabase;
import com.teleprompter.pure.database.ScriptDao;
import com.teleprompter.pure.database.ScriptEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 极简台词编辑器 (ScriptEditorActivity)
 * 纯本地专注创作输入，防丢失双重落库保护，TXT 智能防乱码解析
 */
public class ScriptEditorActivity extends AppCompatActivity {

    public static final String EXTRA_SCRIPT_ID = "extra_script_id";

    private EditText mEtTitle;
    private EditText mEtContent;
    private TextView mTvStatsChip;
    private View mBtnPaste;
    private View mBtnClear;
    private View mBtnLaunchPrompter;
    private View mBtnLaunchBoard;
    private View mBtnBack;

    private long mScriptId = 0L;
    private ScriptEntity mCurrentScript;
    private ScriptDao mScriptDao;
    private final ExecutorService mDbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Handler mAutoSaveHandler = new Handler(Looper.getMainLooper());

    private boolean mHasUnsavedChanges = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_editor);

        mScriptDao = AppDatabase.getInstance(this).scriptDao();
        mScriptId = getIntent().getLongExtra(EXTRA_SCRIPT_ID, 0L);

        initViews();
        setupListeners();
        loadScriptData();
    }

    private void initViews() {
        mEtTitle = findViewById(R.id.et_script_title);
        mEtContent = findViewById(R.id.et_script_content);
        mTvStatsChip = findViewById(R.id.tv_stats_chip);
        mBtnPaste = findViewById(R.id.btn_paste_clipboard);
        mBtnClear = findViewById(R.id.btn_clear_content);
        mBtnLaunchPrompter = findViewById(R.id.btn_launch_prompter);
        mBtnLaunchBoard = findViewById(R.id.btn_launch_board);
        mBtnBack = findViewById(R.id.btn_back);
    }

    private void setupListeners() {
        // 返回前确保执行物理保存
        mBtnBack.setOnClickListener(v -> {
            saveScriptBlocking();
            finish();
        });

        // 正文监听
        mEtContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mHasUnsavedChanges = true;
                updateStats(s != null ? s.toString() : "");
                scheduleAutoSave();
            }
        });

        // 标题变动监听
        mEtTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mHasUnsavedChanges = true;
                scheduleAutoSave();
            }
        });

        // 一键粘贴
        mBtnPaste.setOnClickListener(v -> pasteFromClipboard());

        // 一键清空
        mBtnClear.setOnClickListener(v -> {
            mEtContent.setText("");
            mHasUnsavedChanges = true;
            scheduleAutoSave();
            Toast.makeText(this, "台词已清空", Toast.LENGTH_SHORT).show();
        });

        // 开启悬浮窗提词
        mBtnLaunchPrompter.setOnClickListener(v -> {
            saveScriptBlocking();
            if (mCurrentScript != null && !TextUtils.isEmpty(mCurrentScript.content)) {
                FloatingPrompterManager.getInstance().showPrompter(this, mCurrentScript);
                Toast.makeText(this, "已开启悬浮提词器", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请先输入台词内容", Toast.LENGTH_SHORT).show();
            }
        });

        // 一键进入全屏提词大板
        mBtnLaunchBoard.setOnClickListener(v -> {
            saveScriptBlocking();
            if (mCurrentScript != null && !TextUtils.isEmpty(mCurrentScript.content)) {
                Intent intent = new Intent(this, PromptBoardActivity.class);
                intent.putExtra(PromptBoardActivity.EXTRA_SCRIPT_ID, mCurrentScript.id);
                startActivity(intent);
            } else {
                Toast.makeText(this, "请先输入台词内容", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadScriptData() {
        if (mScriptId > 0) {
            mDbExecutor.execute(() -> {
                ScriptEntity script = mScriptDao.getScriptById(mScriptId);
                if (script != null) {
                    mCurrentScript = script;
                    mMainHandler.post(() -> {
                        mEtTitle.setText(script.title);
                        mEtContent.setText(script.content);
                        updateStats(script.content);
                        mHasUnsavedChanges = false;
                    });
                }
            });
        } else {
            mCurrentScript = new ScriptEntity();
            // 使用全局偏好速度与大小
            mCurrentScript.scrollSpeed = PrompterPreferences.getScrollSpeed(this);
            mCurrentScript.fontSize = PrompterPreferences.getFontSize(this);
            updateStats("");
        }
    }

    private void updateStats(String text) {
        int charCount = (text != null) ? text.replaceAll("\\s+", "").length() : 0;
        int estimatedSec = (int) Math.ceil(charCount / 4.0);

        String durationStr;
        if (estimatedSec < 60) {
            durationStr = estimatedSec + " 秒";
        } else {
            int minutes = estimatedSec / 60;
            int remainingSec = estimatedSec % 60;
            durationStr = minutes + " 分 " + (remainingSec > 0 ? remainingSec + " 秒" : "");
        }

        mTvStatsChip.setText(charCount + " 字 · 预估 " + durationStr);
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "剪贴板为空", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence pasteText = clip.getItemAt(0).coerceToText(this);
            if (!TextUtils.isEmpty(pasteText)) {
                mEtContent.setText(pasteText);

                if (TextUtils.isEmpty(mEtTitle.getText())) {
                    String clean = pasteText.toString().trim().replaceAll("\\s+", " ");
                    String autoTitle = clean.length() > 14 ? clean.substring(0, 14) + "..." : clean;
                    mEtTitle.setText(autoTitle);
                }

                mHasUnsavedChanges = true;
                scheduleAutoSave();
                Toast.makeText(this, "已粘贴剪贴板台词", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void scheduleAutoSave() {
        mAutoSaveHandler.removeCallbacks(mAutoSaveRunnable);
        mAutoSaveHandler.postDelayed(mAutoSaveRunnable, 600);
    }

    private final Runnable mAutoSaveRunnable = this::saveScriptDirectly;

    /**
     * 核心保存逻辑，保证落库一致性
     */
    private void saveScriptDirectly() {
        if (!mHasUnsavedChanges) return;

        String title = mEtTitle.getText().toString().trim();
        String content = mEtContent.getText().toString();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            return;
        }

        if (TextUtils.isEmpty(title)) {
            if (!TextUtils.isEmpty(content)) {
                String clean = content.trim().replaceAll("\\s+", " ");
                title = clean.length() > 14 ? clean.substring(0, 14) + "..." : clean;
            } else {
                title = "未命名台词";
            }
        }

        if (mCurrentScript == null) {
            mCurrentScript = new ScriptEntity();
        }

        mCurrentScript.title = title;
        mCurrentScript.content = content;
        mCurrentScript.wordCount = content.replaceAll("\\s+", "").length();
        mCurrentScript.estimatedSec = Math.max(5, (int) Math.ceil(mCurrentScript.wordCount / 4.0));
        mCurrentScript.updateTime = System.currentTimeMillis();

        final ScriptEntity toSave = mCurrentScript;
        mDbExecutor.execute(() -> {
            try {
                if (toSave.id > 0) {
                    mScriptDao.updateScript(toSave);
                } else {
                    long newId = mScriptDao.insertScript(toSave);
                    toSave.id = newId;
                    mScriptId = newId;
                }
                mHasUnsavedChanges = false;
            } catch (Exception ignored) {}
        });
    }

    /**
     * 页面关闭或切入后台时的阻塞同步持久化（带 500ms 超时保护）
     */
    private void saveScriptBlocking() {
        if (!mHasUnsavedChanges) return;

        String title = mEtTitle.getText().toString().trim();
        String content = mEtContent.getText().toString();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) return;

        if (TextUtils.isEmpty(title)) {
            if (!TextUtils.isEmpty(content)) {
                String clean = content.trim().replaceAll("\\s+", " ");
                title = clean.length() > 14 ? clean.substring(0, 14) + "..." : clean;
            } else {
                title = "未命名台词";
            }
        }

        if (mCurrentScript == null) {
            mCurrentScript = new ScriptEntity();
        }

        mCurrentScript.title = title;
        mCurrentScript.content = content;
        mCurrentScript.wordCount = content.replaceAll("\\s+", "").length();
        mCurrentScript.estimatedSec = Math.max(5, (int) Math.ceil(mCurrentScript.wordCount / 4.0));
        mCurrentScript.updateTime = System.currentTimeMillis();

        final ScriptEntity toSave = mCurrentScript;
        try {
            Future<?> future = mDbExecutor.submit(() -> {
                if (toSave.id > 0) {
                    mScriptDao.updateScript(toSave);
                } else {
                    long newId = mScriptDao.insertScript(toSave);
                    toSave.id = newId;
                    mScriptId = newId;
                }
                mHasUnsavedChanges = false;
            });
            future.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        saveScriptBlocking();
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveScriptBlocking();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAutoSaveHandler.removeCallbacksAndMessages(null);
        mDbExecutor.shutdown();
    }
}
