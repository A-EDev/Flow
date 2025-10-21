# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ============================================================================
# CRITICAL: Core Android and Kotlin Rules
# ============================================================================

# Preserve the line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Keep all annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ============================================================================
# Application Classes - Keep everything to avoid crashes
# ============================================================================

# Keep entire application package to prevent any reflection issues
-keep class com.flow.youtube.** { *; }
-keepclassmembers class com.flow.youtube.** { *; }
-keepnames class com.flow.youtube.** { *; }

# Keep Application class
-keep class com.flow.youtube.FlowApplication { *; }

# ============================================================================
# NewPipe Extractor - CRITICAL for YouTube content fetching
# ============================================================================

# Keep ALL NewPipe classes (critical for release builds)
-keep class org.schabi.newpipe.** { *; }
-keep interface org.schabi.newpipe.** { *; }
-keepclassmembers class org.schabi.newpipe.** { *; }
-keepnames class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**

# Keep NewPipe Downloader implementation
-keep class com.flow.youtube.data.repository.NewPipeDownloader { *; }
-keepclassmembers class com.flow.youtube.data.repository.NewPipeDownloader { *; }

# Keep ServiceList and all service-related classes
-keep class org.schabi.newpipe.extractor.ServiceList { *; }
-keep class org.schabi.newpipe.extractor.ServiceList$* { *; }
-keep class org.schabi.newpipe.extractor.StreamingService { *; }
-keep class org.schabi.newpipe.extractor.services.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.services.** { *; }

# Keep YouTube service implementation
-keep class org.schabi.newpipe.extractor.services.youtube.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.services.youtube.** { *; }

# Keep all extractor base classes and implementations
-keep class * extends org.schabi.newpipe.extractor.Extractor { *; }
-keep class * extends org.schabi.newpipe.extractor.Info { *; }
-keep class org.schabi.newpipe.extractor.stream.** { *; }
-keep class org.schabi.newpipe.extractor.playlist.** { *; }
-keep class org.schabi.newpipe.extractor.search.** { *; }
-keep class org.schabi.newpipe.extractor.kiosk.** { *; }
-keep class org.schabi.newpipe.extractor.channel.** { *; }
-keep class org.schabi.newpipe.extractor.comments.** { *; }

# Keep NewPipe exceptions
-keep class org.schabi.newpipe.extractor.exceptions.** { *; }

# Keep NewPipe downloader classes
-keep class org.schabi.newpipe.extractor.downloader.** { *; }

# Keep NewPipe utils
-keep class org.schabi.newpipe.extractor.utils.** { *; }
-keep class org.schabi.newpipe.extractor.localization.** { *; }

# ============================================================================
# Jetpack Compose - Keep UI components
# ============================================================================

-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Material3 components
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }

# Keep Compose runtime
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }

# ============================================================================
# ExoPlayer / Media3 - Keep media playback classes
# ============================================================================

-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class com.google.android.exoplayer2.** { *; }
-keepclassmembers class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# ============================================================================
# Gson - Keep serialization/deserialization classes
# ============================================================================

-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data model classes
-keep class com.flow.youtube.**.models.** { *; }
-keep class com.flow.youtube.**.data.** { *; }
-keepclassmembers class com.flow.youtube.**.models.** { *; }
-keepclassmembers class com.flow.youtube.**.data.** { *; }

# ============================================================================
# DataStore - Keep preferences
# ============================================================================

-keep class androidx.datastore.*.** { *; }
-keepclassmembers class androidx.datastore.*.** { *; }
-dontwarn androidx.datastore.**

# ============================================================================
# Coil - Keep image loading classes
# ============================================================================

-keep class coil.** { *; }
-keep interface coil.** { *; }
-keepclassmembers class coil.** { *; }
-dontwarn coil.**

# ============================================================================
# Navigation Component
# ============================================================================

-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ============================================================================
# Standard Android Rules
# ============================================================================

# Keep all serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Activity methods
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# ============================================================================
# Optimization Settings
# ============================================================================

# Don't optimize away null checks
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(java.lang.Object);
    public static void checkNotNull(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
}

# Remove logging in release (optional - comment out if you want logs)
# -assumenosideeffects class android.util.Log {
#     public static *** d(...);
#     public static *** v(...);
#     public static *** i(...);
# }

# ============================================================================
# OkHttp and Networking (if NewPipe uses it internally)
# ============================================================================

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ============================================================================
# Mozilla Rhino (JavaScript engine used by NewPipe)
# ============================================================================

-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**
-dontwarn javax.script.**

# Keep Rhino classes if present but don't fail if missing
-keep class org.mozilla.javascript.** { *; }

# ============================================================================
# End of ProGuard Rules
# ============================================================================
