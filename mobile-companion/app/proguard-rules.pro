# kotlinx.serialization: keep generated serializers for the sync protobuf models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class mihon.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class mihon.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class mihon.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.slf4j.**
