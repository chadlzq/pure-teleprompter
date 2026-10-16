package com.teleprompter.pure.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.teleprompter.pure.FloatingPrompterManager;
import com.teleprompter.pure.R;
import com.teleprompter.pure.database.AppDatabase;
import com.teleprompter.pure.database.ScriptDao;
import com.teleprompter.pure.database.ScriptEntity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 核心主入口：我的台词本与草稿箱 (ScriptManagerActivity)
 * 完美还原原版 ManagerActivity 原生架构，专做减法纯净版：
 * 
 * 1. 【首发新手教程预置】：首次打开自动预置《新手口播提词指引》，即开即播
 * 2. 【高容错 TXT 导入】：支持 UTF-8 / GBK 编码自适应，异常拦截防护，杜绝闪退
 * 3. 【一键开播】：每张台词卡片直通「悬浮开播」与「全屏开播」
 * 4. 【独立偏好设置】：右上角独立齿轮进入系统设置，彻底告别悬浮窗上设置遮挡与闪退
 */
public class ScriptManagerActivity extends AppCompatActivity implements ScriptDraftAdapter.OnDraftActionListener {

    private RecyclerView mRvDrafts;
    private ScriptDraftAdapter mAdapter;
    private TextView mTvDraftsCount;
    private View mLlEmptyState;

    private ScriptDao mScriptDao;
    private final ExecutorService mDbExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // 健壮安全的 SAF 文件选择器
    private final ActivityResultLauncher<String[]> mOpenDocLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri != null) {
                    importTxtFileSafely(uri);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_script_manager);

        mScriptDao = AppDatabase.getInstance(this).scriptDao();

        initViews();
        setupListeners();
        checkAndInitDefaultTutorial();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAllScripts();
    }

    private void initViews() {
        mRvDrafts = findViewById(R.id.rv_drafts);
        mTvDraftsCount = findViewById(R.id.tv_drafts_count);
        mLlEmptyState = findViewById(R.id.ll_empty_state);

        mAdapter = new ScriptDraftAdapter(this, this);
        mRvDrafts.setLayoutManager(new LinearLayoutManager(this));
        mRvDrafts.setAdapter(mAdapter);
    }

    private void setupListeners() {
        // 1. 右上角设置
        findViewById(R.id.iv_setting).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        // 2. 新建台词
        findViewById(R.id.card_new_draft).setOnClickListener(v -> {
            Intent intent = new Intent(this, ScriptEditorActivity.class);
            startActivity(intent);
        });

        // 3. 导入 TXT
        findViewById(R.id.card_import_txt).setOnClickListener(v -> {
            try {
                mOpenDocLauncher.launch(new String[]{"text/plain", "*/*"});
            } catch (Exception e) {
                Toast.makeText(this, "无法唤起系统文件选择器", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 首次安装预置新手教学文档
     */
    private void checkAndInitDefaultTutorial() {
        mDbExecutor.execute(() -> {
            List<ScriptEntity> existing = mScriptDao.getAllScripts();
            if (existing == null || existing.isEmpty()) {
                ScriptEntity tutorial = new ScriptEntity();
                tutorial.title = "【新手必看】极简悬浮提词器使用指南";
                tutorial.content = "欢迎使用极简悬浮提词器！这是一款100%纯离线、零广告、无任何联网通信权限的安全提词神器。\n\n"
                        + "【三秒快速上手】：\n"
                        + "1. 点击卡片右侧绿色的「悬浮开播」，即可在任意短视频、相机应用上方悬浮提词；\n"
                        + "2. 拖动悬浮窗顶部中央手柄，可随心移动位置（推荐放在前置摄像头正下方，眼神更聚焦自然）；\n"
                        + "3. 拖动悬浮窗右下角角标可自由缩放视窗大小；点击「-」可收起为小圆球，随时轻触唤回；\n"
                        + "4. 点击主页右上角「⚙ 设置」，可自由调节字号大小、字体颜色、背景透明度、滚动语速与分光镜镜像翻转；\n"
                        + "5. 点击上方「新建台词」或「导入 TXT」，即可将你的口播稿件随时保存与演练！\n\n"
                        + "祝你镜头感满满，口播一次过！";
                tutorial.wordCount = tutorial.content.replaceAll("\\s+", "").length();
                tutorial.estimatedSec = Math.max(5, tutorial.wordCount / 4);
                tutorial.updateTime = System.currentTimeMillis();
                mScriptDao.insertScript(tutorial);

                loadAllScripts();
            }
        });
    }

    private void loadAllScripts() {
        mDbExecutor.execute(() -> {
            List<ScriptEntity> list = mScriptDao.getAllScripts();
            mMainHandler.post(() -> {
                mAdapter.setScripts(list);
                int count = (list != null) ? list.size() : 0;
                mTvDraftsCount.setText(count + " 篇");
                mLlEmptyState.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                mRvDrafts.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
            });
        });
    }

    /**
     * 健壮的 TXT 文本导入，自动识别 UTF-8 与 GBK 编码，百分百防崩溃
     */
    private void importTxtFileSafely(Uri uri) {
        mDbExecutor.execute(() -> {
            InputStream is = null;
            ByteArrayOutputStream bos = null;
            try {
                String fileName = "导入台词";
                Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            String displayName = cursor.getString(nameIndex);
                            if (!TextUtils.isEmpty(displayName)) {
                                fileName = displayName;
                            }
                        }
                    }
                    cursor.close();
                }

                // 检查后缀
                if (fileName.toLowerCase().endsWith(".txt")) {
                    fileName = fileName.substring(0, fileName.length() - 4);
                }

                is = getContentResolver().openInputStream(uri);
                if (is == null) {
                    showToastOnMain("读取文件失败，请重试");
                    return;
                }

                bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                int totalRead = 0;
                int maxBytes = 2 * 1024 * 1024; // 限制最大 2MB，防止内存溢出

                while ((len = is.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                    totalRead += len;
                    if (totalRead > maxBytes) {
                        break;
                    }
                }

                byte[] fileBytes = bos.toByteArray();
                if (fileBytes.length == 0) {
                    showToastOnMain("所选文件内容为空");
                    return;
                }

                // 优先以 UTF-8 解码，如果包含乱码则回退至 GB18030/GBK
                String content = new String(fileBytes, StandardCharsets.UTF_8);
                if (content.contains("\uFFFD")) {
                    try {
                        content = new String(fileBytes, Charset.forName("GB18030"));
                    } catch (Exception ignored) {
                        content = new String(fileBytes, Charset.forName("GBK"));
                    }
                }

                content = content.trim();
                if (TextUtils.isEmpty(content)) {
                    showToastOnMain("文档正文为空");
                    return;
                }

                // 入库保存
                ScriptEntity newScript = new ScriptEntity();
                newScript.title = fileName;
                newScript.content = content;
                newScript.wordCount = content.replaceAll("\\s+", "").length();
                newScript.estimatedSec = Math.max(5, newScript.wordCount / 4);
                newScript.updateTime = System.currentTimeMillis();

                long newId = mScriptDao.insertScript(newScript);
                newScript.id = newId;

                final String finalTitle = fileName;
                mMainHandler.post(() -> {
                    Toast.makeText(this, "成功导入台词：《" + finalTitle + "》", Toast.LENGTH_SHORT).show();
                    loadAllScripts();
                });

            } catch (Exception e) {
                showToastOnMain("导入失败：请选择标准 .txt 格式文本");
            } finally {
                try {
                    if (is != null) is.close();
                    if (bos != null) bos.close();
                } catch (Exception ignored) {}
            }
        });
    }

    private void showToastOnMain(String msg) {
        mMainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onEdit(ScriptEntity script) {
        Intent intent = new Intent(this, ScriptEditorActivity.class);
        intent.putExtra(ScriptEditorActivity.EXTRA_SCRIPT_ID, script.id);
        startActivity(intent);
    }

    @Override
    public void onLaunchFloat(ScriptEntity script) {
        if (script == null || TextUtils.isEmpty(script.content)) {
            Toast.makeText(this, "台词内容为空，请先编辑", Toast.LENGTH_SHORT).show();
            return;
        }
        FloatingPrompterManager.getInstance().showPrompter(this, script);
        Toast.makeText(this, "已开启悬浮提词器", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLaunchBoard(ScriptEntity script) {
        if (script == null || TextUtils.isEmpty(script.content)) {
            Toast.makeText(this, "台词内容为空，请先编辑", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PromptBoardActivity.class);
        intent.putExtra(PromptBoardActivity.EXTRA_SCRIPT_ID, script.id);
        startActivity(intent);
    }

    @Override
    public void onDelete(ScriptEntity script) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除台词《" + (TextUtils.isEmpty(script.title) ? "未命名" : script.title) + "》吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    mDbExecutor.execute(() -> {
                        mScriptDao.deleteById(script.id);
                        mMainHandler.post(() -> {
                            Toast.makeText(this, "台词已删除", Toast.LENGTH_SHORT).show();
                            loadAllScripts();
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDbExecutor.shutdown();
    }
}
