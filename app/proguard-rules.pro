# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /path/to/sdk/tools/proguard/proguard-android-optimize.txt

# Google Media3 (ExoPlayer) Rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Glide Rules
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# FFmpeg-Kit Rules
-keep class com.arthenica.ffmpegkit.** { *; }

# Prevent removing model classes used in UI
-keep class com.app.mediaplayer.VideoModel { *; }
-keep class com.app.mediaplayer.FolderModel { *; }
-keep class com.app.mediaplayer.VaultModel { *; }
