-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.aad.** { *; }

-keep class com.xiang.ai.todoentry.ai.** { *; }
-keep class com.xiang.ai.todoentry.graph.** { *; }
-keep class com.xiang.ai.todoentry.settings.AppSettings { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

-dontwarn com.google.auto.value.AutoValue
-dontwarn edu.umd.cs.findbugs.annotations.NonNull
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
