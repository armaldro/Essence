# Project-specific ProGuard/R8 rules go here.
# See https://developer.android.com/build/shrink-code

# The Claude SDK maps JSON with Jackson through reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.anthropic.**
-dontwarn java.beans.**
-dontwarn org.slf4j.**
# Optional classes the SDK's dependencies reference but never load on Android.
-dontwarn com.google.auto.value.AutoValue
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType
