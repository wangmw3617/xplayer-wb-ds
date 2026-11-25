# 保留崩溃堆栈文件名与行号便于定位线上问题
-keepattributes SourceFile,LineNumberTable

# libmpv / mpv-android-lib
-keep class is.xyz.mpv.** { *; }
-dontwarn is.xyz.mpv.**

# Media3 FFmpeg / HLS / DASH / Extractors / Session（含反射加载的类）
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**
-keep class androidx.media3.exoplayer.hls.** { *; }
-dontwarn androidx.media3.exoplayer.hls.**
-keep class androidx.media3.exoplayer.dash.** { *; }
-dontwarn androidx.media3.exoplayer.dash.**
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.session.**

# Room 实体、投影、类型转换与数据库
-keep class * extends androidx.room.RoomDatabase
-keep class com.xplayer.app.data.local.** { *; }

# DataStore 与业务枚举通过 .name / enumValueOf 序列化，防止 R8 枚举拆箱或重命名
-keepclassmembers enum com.xplayer.app.data.datastore.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.xplayer.app.data.local.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.xplayer.app.player.engine.EngineType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers enum com.xplayer.app.player.pip.PlayerCommand {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
