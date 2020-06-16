# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Serializable
-keep public class * implements java.io.Serializable {*;}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepattributes *Annotation*

#### com.proguard.annotation.NotProguard
-keep interface com.proguard.annotation.NotProguard { *; }
-keep @com.proguard.annotation.NotProguard class * {*;}
-keep @com.proguard.annotation.NotProguard interface * {*;}
-keep @com.proguard.annotation.NotProguard enum * { *; }
-keepclassmembers class * {
    @com.proguard.annotation.NotProguard <methods>;
    @com.proguard.annotation.NotProguard <fields>;
}

#proguard class
-keep class * implements com.proguard.annotation.IPublic{
    public *;
}
-keepclassmembers class * implements com.proguard.annotation.IMembers{
   *;
}
-keep class * implements com.proguard.annotation.IClassName { }
