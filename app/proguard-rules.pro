# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep all Compose classes
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep Material3 classes
-keep class androidx.compose.material3.** { *; }

# Keep Application class
-keep class com.flow.youtube.FlowApplication { *; }
-keep class com.flow.youtube.** { *; }

# Keep NewPipeExtractor classes - CRITICAL for release builds
-keep class org.schabi.newpipe.** { *; }
-keep interface org.schabi.newpipe.** { *; }
-keepclassmembers class org.schabi.newpipe.** { *; }
-keepnames class org.schabi.newpipe.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-dontwarn org.schabi.newpipe.**

# Keep NewPipe Downloader
-keep class com.flow.youtube.data.repository.NewPipeDownloader { *; }
-keepclassmembers class com.flow.youtube.data.repository.NewPipeDownloader { *; }

# Keep NewPipe ServiceList - Essential for YouTube service access
-keep class org.schabi.newpipe.extractor.ServiceList { *; }
-keep class org.schabi.newpipe.extractor.ServiceList$* { *; }

# Keep StreamingService and related classes
-keep class org.schabi.newpipe.extractor.StreamingService { *; }
-keep class org.schabi.newpipe.extractor.services.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.services.** { *; }

# Keep YouTube service classes
-keep class org.schabi.newpipe.extractor.services.youtube.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.services.youtube.** { *; }

# Keep extractors and info classes
-keep class * extends org.schabi.newpipe.extractor.Extractor { *; }
-keep class * extends org.schabi.newpipe.extractor.Info { *; }
-keep class org.schabi.newpipe.extractor.stream.** { *; }
-keep class org.schabi.newpipe.extractor.playlist.** { *; }
-keep class org.schabi.newpipe.extractor.search.** { *; }

# Keep NewPipe exceptions
-keep class org.schabi.newpipe.extractor.exceptions.** { *; }

# Keep ExoPlayer classes
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Gson classes and prevent obfuscation of data classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes (adjust package name if needed)
-keep class com.flow.youtube.**.models.** { *; }
-keep class com.flow.youtube.**.data.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep DataStore
-keep class androidx.datastore.*.** { *; }

# Keep Coil for image loading
-keep class coil.** { *; }
-keep interface coil.** { *; }
-keepclassmembers class coil.** { *; }

# Keep Navigation Compose
-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }

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
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
