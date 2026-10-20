# Shizuku v13 creates this Binder implementation in another process using
# ClassLoader + getConstructor(Context). It is not an Android manifest Service.
# Keep only this entry point; Binder overrides and their callees remain optimizable.
# https://github.com/RikkaApps/Shizuku-API/blob/master/demo/src/main/java/rikka/shizuku/demo/service/UserService.java
-keep,allowoptimization class dev.galaxy.toolbox.diagnostics.DiagnosticUserService {
    public <init>(android.content.Context);
}

# Manifest components / RemoteViews resources are rooted by AGP/AAPT. Shizuku's
# BinderContainer Parcelable rules and AndroidX rules come from their dependencies.
# Do not blanket-keep Compose, Backdrop, all application classes, or all ZIP codecs.

# Commons Compress's optional Zstandard/XZ codecs are not installed. The report
# importer explicitly rejects every ZIP entry method except STORED (0) and
# DEFLATE (8) before getInputStream; ZIP64 changes headers, not the compression.
# Suppress only these absent optional types, not application or library errors.
-dontwarn com.github.luben.zstd.ZstdInputStream
-dontwarn org.tukaani.xz.MemoryLimitException
-dontwarn org.tukaani.xz.SingleXZInputStream
-dontwarn org.tukaani.xz.XZInputStream
