# SPDX-FileCopyrightText: 2025 Flow
# SPDX-License-Identifier: GPL-3.0-or-later
# Based on NewPipe's ProGuard configuration

# https://developer.android.com/build/shrink-code

## Helps debug release versions - keeps class/method names readable
-dontobfuscate

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.schabi.newpipe.extractor.** { *; }

## Rules for Rhino and Rhino Engine (JavaScript engine used by NewPipe)
-keep class org.mozilla.javascript.* { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**

## Rules for ExoPlayer / Media3
-keep class com.google.android.exoplayer2.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn com.google.android.exoplayer2.**
-dontwarn androidx.media3.**

## Keep application classes
-keep class com.flow.youtube.** { *; }
-keep class com.flow.youtube.FlowApplication { *; }

## Rules for Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

## Rules for Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

## Keep data model classes
-keep class com.flow.youtube.**.models.** { *; }
-keep class com.flow.youtube.**.data.** { *; }

## Rules for Kotlin
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

## Rules for DataStore
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

## Rules for Coil image loading
-keep class coil.** { *; }
-dontwarn coil.**

## Rules for Navigation
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

## Standard Android rules
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

## Rules for OkHttp (used internally by NewPipe)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

## Java Beans (not available on Android)
-dontwarn java.beans.**
