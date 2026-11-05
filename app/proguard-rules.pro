# Upstream models are populated reflectively by Jackson.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Optional Commons Compress codecs absent from the existing runtime (ZIP payloads only).
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.XZInputStream
