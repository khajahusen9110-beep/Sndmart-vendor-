# ProGuard & R8 Configuration for Vendor App Release

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin Reflection & Coroutines
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }

# Moshi Codegen & Serialization
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class *JsonAdapter { *; }
-keep class com.squareup.moshi.** { *; }

# App Models (Supabase tables and RPCs)
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }

# Retrofit & OkHttp
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.** { *; }
-keep interface com.example.data.network.SupabaseApi { *; }
-keep class com.example.data.network.** { *; }
-keep class okhttp3.** { *; }

# Firebase Messaging & Push Services
-keep class com.google.firebase.messaging.** { *; }
-keep class com.example.service.** { *; }

# ViewModels and UI States
-keep class com.example.ui.viewmodel.** { *; }
-keepclassmembers class com.example.ui.viewmodel.** { *; }

# Enum preservation
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

