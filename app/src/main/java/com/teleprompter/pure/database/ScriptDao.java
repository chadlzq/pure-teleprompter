package com.teleprompter.pure.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 本地数据操作接口 (ScriptDao)
 * 纯本地单表极简增删改查
 */
@Dao
public interface ScriptDao {

    @Query("SELECT * FROM tb_scripts ORDER BY updateTime DESC")
    List<ScriptEntity> getAllScripts();

    @Query("SELECT * FROM tb_scripts WHERE id = :scriptId LIMIT 1")
    ScriptEntity getScriptById(long scriptId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertScript(ScriptEntity script);

    @Update
    void updateScript(ScriptEntity script);

    @Delete
    void deleteScript(ScriptEntity script);

    @Query("DELETE FROM tb_scripts WHERE id = :scriptId")
    void deleteById(long scriptId);
}
