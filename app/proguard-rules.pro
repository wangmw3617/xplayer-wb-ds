# libmpv / mpv-android-lib
-keep class is.xyz.mpv.** { *; }
-dontwarn is.xyz.mpv.**

# Media3 FFmpeg 扩展（通过反射加载）
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-dontwarn androidx.media3.decoder.ffmpeg.**

# Media3 Session
-keep class androidx.media3.session.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
