# 极简悬浮提词器混淆规则
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keep class com.lzf.easyfloat.** { *; }
-dontwarn com.lzf.easyfloat.**
