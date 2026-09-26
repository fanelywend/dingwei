# 保留 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dingwei.gpsmock.** {
    *** Companion;
}
-keepclasseswithmembers class com.dingwei.gpsmock.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**
