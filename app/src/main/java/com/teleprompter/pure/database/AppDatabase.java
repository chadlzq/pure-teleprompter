package com.teleprompter.pure.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 本地 Room 数据库单例 (AppDatabase)
 */
@Database(entities = {ScriptEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DB_NAME = "teleprompter_local.db";
    private static volatile AppDatabase sInstance;

    public abstract ScriptDao scriptDao();

    public static AppDatabase getInstance(Context context) {
        if (sInstance == null) {
            synchronized (AppDatabase.class) {
                if (sInstance == null) {
                    sInstance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DB_NAME
                    ).allowMainThreadQueries() // 极简单表，允许轻量主线程读取
                     .fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return sInstance;
    }
}
