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

-dontwarn com.lxj.xpopup.widget.**
-keep class com.lxj.xpopup.widget.**{*;}

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# Prevent proguard from stripping interface information from TypeAdapter, TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class com.google.gson.reflect.TypeToken {*;}

## @Serializable data class
-keep class com.example.** {
    @kotlinx.serialization.Serializable *;
}

# Prevent R8 from leaving Data object members always null
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
##---------------End: proguard configuration for Gson  ----------
-keep class wallet.core.** {*;}
-keep class com.nftco.flow.** {*;}
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleAnnotations, RuntimeInvisibleParameterAnnotations
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @com.nftco.flow.sdk.cadence.JsonCadenceConversion *;
}
-keepclassmembers class * {
    @kotlin.Metadata *;
}
-keep class kotlin.reflect.** { *; }

-keep class kotlin.reflect.jvm.internal.** { *; }
-keepclassmembers class * {
    @com.nftco.flow.sdk.cadence.JsonCadenceConversion <methods>;
}
-keepclassmembers class * {
    @com.nftco.flow.sdk.cadence.JsonCadenceConversion <fields>;
}
-keepclassmembers class ** {
    @com.nftco.flow.sdk.cadence.JsonCadenceConversion *;
}

-keepnames class kotlin.** { *; }
-keep class kotlin.reflect.jvm.internal.impl.metadata.** { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

-keep class com.nftco.flow.sdk.cadence.JsonCadenceConverter
-keep class * implements com.nftco.flow.sdk.cadence.JsonCadenceConverter { *; }

-keep @interface com.nftco.flow.sdk.cadence.JsonCadenceConversion
-keep class * {
    @com.nftco.flow.sdk.cadence.JsonCadenceConversion *;
}
-keep class com.nftco.flow.sdk.cadence.** { *; }
-keepclassmembers class com.nftco.flow.sdk.cadence.** {
    *;
    <init>(...);
    <clinit>(...);
}
-keep enum com.nftco.flow.sdk.cadence.** { *; }
-keep class com.nftco.flow.sdk.cadence.JsonCadenceConverter { *; }
-keep class com.nftco.flow.sdk.cadence.JsonCadenceMarshalling { *; }
-keep class com.nftco.flow.sdk.cadence.JsonCadenceBuilder { *; }
-keep class com.nftco.flow.sdk.cadence.JsonCadenceParser { *; }


-keep class com.just.agentweb.** {*;}
-dontwarn com.just.agentweb.**

-keep class com.flowfoundation.wallet.database.WebviewRecord {*;}
-keep class com.flowfoundation.wallet.manager.cadence.CadenceScript { *; }
-keep class com.flowfoundation.wallet.manager.cadence.CadenceScripts { *; }
-keep class com.flowfoundation.wallet.manager.cadence.CadenceScriptData { *; }
-keep class com.flowfoundation.wallet.manager.cadence.CadenceScriptResponse { *; }

# google drive
-keepclassmembers class * {
  @com.google.api.client.util.Key <fields>;
}

# BouncyCastleProvider
-keep class org.bouncycastle.** {*;}

-keep class com.flowfoundation.wallet.widgets.webview.JsInterface.** {*;}
-keep class com.flowfoundation.wallet.widgets.webview.JavascriptKt.** {*;}

-keep class com.walletconnect.** {*;}
-keep class net.sqlcipher.** {*;}
-keep class org.trustwallet.core.** { *; }

-keep public enum com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency.** {*;}

-keep public enum com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency$** {
  **[] $VALUES;
  public *;
}

-keep class com.translized.translized_ota.** { *; }