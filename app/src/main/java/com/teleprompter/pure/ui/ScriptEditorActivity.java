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
import com.teleprompter.pure.database.AppDatabase;
import com.teleprompter.pure.database.ScriptDao;
import com.teleprompter.pure.database.ScriptEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 极简台词编辑器 (ScriptEditorActivity)
 * 
 * 核心功能：
 * 1. 标题与正文专注创作输入
 * 2. 实时字数统计与口播语速耗时预估 (每秒4字)
 * 3. 一键从系统剪贴板粘贴
 * 4. 【新增】：一键导入本地 TXT 文本文件 (自动提取标题与全文)
 * 5. 防丢失本地自动保存 (退出页面或输入停顿 800ms 自动静默落库 Room SQLite)
 * 6. 【新增】：支持一键呼出悬浮窗提词 或 一键进入全屏演播大板 (PromptBoardActivity)
 */
public class ScriptEditorActivity extends AppCompatActivity {

    public static final String EXTRA_SCRIPT_ID = "extra_script_id";

    private EditText mEtTitle;
    private EditText mEtContent;
    private TextView mTvStatsChip;
    private TextView mTvSaveStatus;
    private View mBtnPaste;
    private View mBtnClear;
    private View mBtnImportTxt;
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

    // 系统文件选择器 (SAF: 支持 Android 8~14+ 全机型免权限安全读取)
    private final ActivityResultLauncher<String[]> mOpenFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importTxtFromUri(uri);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.teleprompter.pure.R.layout.activity_script_editor);

        mScriptDao = AppDatabase.getInstance(this).scriptDao();
        mScriptId = getIntent().getLongExtra(EXTRA_SCRIPT_ID, 0L);

        initViews();
        setupListeners();
        loadScriptData();
    }

    private void initViews() {
        mEtTitle = findViewById(com.teleprompter.pure.R.id.et_script_title);
        mEtContent = findViewById(com.teleprompter.pure.R.id.et_script_content);
        mTvStatsChip = findViewById(com.teleprompter.pure.R.id.tv_stats_chip);
        mTvSaveStatus = findViewById(com.teleprompter.pure.R.id.tv_save_status);
        mBtnPaste = findViewById(com.teleprompter.pure.R.id.btn_paste_clipboard);
        mBtnClear = findViewById(com.teleprompter.pure.R.id.btn_clear_content);
        mBtnImportTxt = findViewById(com.teleprompter.pure.R.id.btn_import_txt);
        mBtnLaunchPrompter = findViewById(com.teleprompter.pure.R.id.btn_launch_prompter);
        mBtnLaunchBoard = findViewById(com.teleprompter.pure.R.id.btn_launch_board);
        mBtnBack = findViewById(com.teleprompter.pure.R.id.btn_back);
    }

    private void setupListeners() {
        mBtnBack.setOnClickListener(v -> finish());

        // 正文监听
        mEtContent.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mHasUnsavedChanges = true;
                updateStats(s != null ? s.toString() : "");
                scheduleAutoSave();
            }
        });

        // 标题变动监听
        mEtTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

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
            Toast.makeText(this, "台词已清空", Toast.LENGTH_SHORT).show();
        });

        // 【新增】：一键导入 TXT 文本
        mBtnImportTxt.setOnClickListener(v -> {
            mOpenFileLauncher.launch(new String[]{"text/plain", "text/*", "*/*"});
        });

        // 开启悬浮窗提词
        mBtnLaunchPrompter.setOnClickListener(v -> {
            saveScriptSync();
            if (mCurrentScript != null && !TextUtils.isEmpty(mCurrentScript.content)) {
                FloatingPrompterManager.getInstance().showPrompter(this, mCurrentScript);
                Toast.makeText(this, "已开启悬浮提词器", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "请先输入台词内容", Toast.LENGTH_SHORT).show();
            }
        });

        // 【新增】：一键进入全屏提词大板
        mBtnLaunchBoard.setOnClickListener(v -> {
            saveScriptSync();
            if (mCurrentScript != null && !TextUtils.isEmpty(mCurrentScript.content)) {
                Intent intent = new Intent(this, PromptBoardActivity.class);
                intent.putExtra(PromptBoardActivity.EXTRA_SCRIPT_ID, mCurrentScript.id);
                startActivity(intent);
            } else {
                Toast.makeText(this, "请先输入台词内容", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 【TXT 导入实现】：从 URI 流中安全解析文本与文件名
     */
    private void importTxtFromUri(Uri uri) {
        mDbExecutor.execute(() -> {
            try {
                String fileName = "导入台词";
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                    cursor.close();
                }

                if (fileName.endsWith(".txt")) {
                    fileName = fileName.substring(0, fileName.length() - 4);
                }

                // 读取正文 (优先 UTF-8)
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                final String finalTitle = fileName;
                final String finalContent = sb.toString().trim();

                mMainHandler.post(() -> {
                    if (TextUtils.isEmpty(mEtTitle.getText())) {
                        mEtTitle.setText(finalTitle);
                    }
                    mEtContent.setText(finalContent);
                    Toast.makeText(this, "成功导入文档: " + finalTitle, Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                mMainHandler.post(() -> Toast.makeText(this, "导入失败，请检查文件格式", Toast.LENGTH_SHORT).show());
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
                        mTvSaveStatus.setText("已载入本地台词");
                    });
                }
            });
        } else {
            mCurrentScript = new ScriptEntity();
            updateStats("");
        }
    }

    private void updateStats(String text) {
        int charCount = text.replaceAll("\\s+", "").length();
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
                    String autoTitle = clean.length() > 12 ? clean.substring(0, 12) + "..." : clean;
                    mEtTitle.setText(autoTitle);
                }

                Toast.makeText(this, "已粘贴剪贴板台词", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void scheduleAutoSave() {
        mAutoSaveHandler.removeCallbacks(mAutoSaveRunnable);
        mAutoSaveHandler.postDelayed(mAutoSaveRunnable, 800);
        mTvSaveStatus.setText("正在保存...");
    }

    private final Runnable mAutoSaveRunnable = this::saveScriptAsync;

    private void saveScriptAsync() {
        if (!mHasUnsavedChanges) return;

        String title = mEtTitle.getText().toString().trim();
        String content = mEtContent.getText().toString();

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(content)) {
            mTvSaveStatus.setText("无内容无需保存");
            return;
        }

        if (TextUtils.isEmpty(title)) {
            title = "未命名台词";
        }

        if (mCurrentScript == null) {
            mCurrentScript = new ScriptEntity();
        }

        mCurrentScript.title = title;
        mCurrentScript.content = content;
        mCurrentScript.wordCount = content.replaceAll("\\s+", "").length();
        mCurrentScript.estimatedSec = (int) Math.ceil(mCurrentScript.wordCount / 4.0);
        mCurrentScript.updateTime = System.currentTimeMillis();

        final ScriptEntity toSave = mCurrentScript;
        mDbExecutor.execute(() -> {
            if (toSave.id > 0) {
                mScriptDao.updateScript(toSave);
            } else {
                long newId = mScriptDao.insertScript(toSave);
                toSave.id = newId;
                mScriptId = newId;
            }
            mHasUnsavedChanges = false;
            mMainHandler.post(() -> mTvSaveStatus.setText("已自动保存到本地数据库"));
        });
    }

    private void saveScriptSync() {
        saveScriptAsync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveScriptSync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAutoSaveHandler.removeCallbacksAndMessages(null);
        mDbExecutor.shutdown();
    }
}
