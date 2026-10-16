package com.teleprompter.pure.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 台词本数据实体 (ScriptEntity)
 * 纯本地存储，无任何网络依赖，性能极高
 */
@Entity(tableName = "tb_scripts")
public class ScriptEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;         // 台词标题
    public String content;       // 台词正文
    public int wordCount;        // 字数统计
    public int estimatedSec;     // 预估口播耗时 (字数 / 4 = 秒数)

    // 个性化排版参数 (每篇台词独立记忆)
    public int scrollSpeed = 30;           // 滚动速度 (10 ~ 100)
    public float fontSize = 22f;          // 字体大小 (16 ~ 36sp)
    public int alignment = 1;             // 0: 左对齐, 1: 居中对齐, 2: 右对齐 (默认居中)
    public int opacity = 75;              // 悬浮窗背景透明度 (0 ~ 100)
    public long updateTime = System.currentTimeMillis();

    public ScriptEntity() {}

    public ScriptEntity(String title, String content) {
        this.title = title;
        this.content = content;
        this.wordCount = (content != null) ? content.length() : 0;
        // 按照普通人正常口播语速 240字/分 (每秒约 4 字) 计算
        this.estimatedSec = Math.max(5, this.wordCount / 4);
    }
}
