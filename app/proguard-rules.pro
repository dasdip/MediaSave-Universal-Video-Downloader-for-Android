-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

-keep class com.materialtube.downloader.** { *; }
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.** { *; }
-keep class androidx.datastore.** { *; }

-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

-dontwarn com.yausername.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.**
-dontwarn okhttp3.**
-dontwarn okio.**
