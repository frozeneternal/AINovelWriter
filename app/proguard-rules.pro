# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.ainovel.app.data.remote.dto.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn org.slf4j.**
