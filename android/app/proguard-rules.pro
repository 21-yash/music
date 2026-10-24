# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools proguard-defaults.txt

# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep any @Serializable annotated class and its serializer
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable class *;
}
-keep,includedescriptorclasses class *$$serializer { *; }

# Keep app data classes used with serialization
-keep,includedescriptorclasses class com.musicsportsapp.data.remote.dto.**$$serializer { *; }
-keepclassmembers class com.musicsportsapp.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.musicsportsapp.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.musicsportsapp.features.music.data.dto.**$$serializer { *; }
-keepclassmembers class com.musicsportsapp.features.music.data.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.musicsportsapp.features.music.data.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.musicsportsapp.features.music.jam.**$$serializer { *; }
-keepclassmembers class com.musicsportsapp.features.music.jam.** {
    *** Companion;
}
-keepclasseswithmembers class com.musicsportsapp.features.music.jam.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit & OkHttp
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Media3 & ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# WorkManager & Hilt Work
-keep class androidx.work.** { *; }
-keep class androidx.hilt.work.** { *; }

# Socket.IO & Engine.IO
-keep class io.socket.** { *; }
-dontwarn io.socket.**

# NewPipe Extractor
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-keep class org.jsoup.** { *; }
