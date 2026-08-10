-keep class org.lsposed.hiddenapibypass.** { *; }
-keepclassmembers class android.media.audiofx.AudioEffect {
    <init>(java.util.UUID, java.util.UUID, int, int);
    int setParameter(int, byte[]);
    int getParameter(int, byte[]);
}
