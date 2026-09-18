# R8 — release. Rien de secret ici : les règles suivantes évitent seulement
# que l'obfuscation ne casse la réflexion utilisée par la pile réseau.

# Retrofit : les interfaces et leurs annotations sont lues par réflexion.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OkHttp / Okio / Conscrypt : avertissements sans impact.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization : recherche de serializers par réflexion.
-keepattributes *Annotation*, RuntimeVisibleAnnotations
-keep,includedescriptorclasses class school.greenwood.plus.**$$serializer { *; }
-keepclassmembers class school.greenwood.plus.** {
    *** Companion;
}
-keepclasseswithmembers class school.greenwood.plus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
