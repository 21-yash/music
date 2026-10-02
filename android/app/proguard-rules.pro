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

# Keep app data classes used with serialization
-keep,includedescriptorclasses class com.musicsportsapp.data.remote.dto.**$$serializer { *; }
-keepclassmembers class com.musicsportsapp.data.remote.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.musicsportsapp.data.remote.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
