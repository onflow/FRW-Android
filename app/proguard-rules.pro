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

# Keep Web3j
-keep class org.web3j.abi.** { *; }
-keep class org.web3j.protocol.** { *; }
-keep class org.web3j.abi.datatypes.** { *; }
-keep class org.web3j.abi.datatypes.generated.** { *; }
-keep class org.web3j.abi.datatypes.Type { *; }
-keep class org.web3j.protocol.core.methods.request.Transaction { *; }

-keepclassmembers class * {
    @org.web3j.abi.TypeReference <fields>;
}

-keep public class * extends org.web3j.abi.TypeReference

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

# SvgModel
-keep class com.flowfoundation.wallet.utils.image.SvgModel
-keep class com.flowfoundation.wallet.utils.image.SvgModelLoader
-keep class com.flowfoundation.wallet.utils.image.SvgDataFetcher
-keep class com.flowfoundation.wallet.utils.image.SvgModelLoaderFactory
-keep class com.flowfoundation.wallet.utils.image.SvgGlideModule extends com.bumptech.glide.module.AppGlideModule { *; }

# GoogleDrive
-keep class com.google.** { *;}
-keep interface com.google.** { *;}
-dontwarn com.google.**

-dontwarn com.google.common.collect.MinMaxPriorityQueue
-keep class * extends com.google.api.client.json.GenericJson {
*;
}
-keep class com.google.api.services.drive.** {
*;
}

## @Serializable data class
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

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

# barcode scanner
-keep,includedescriptorclasses class com.journeyapps.barcodescanner.** { *; }
-keepclassmembers class com.journeyapps.barcodescanner.** { *; }

# BouncyCastleProvider
-keep class org.bouncycastle.** {*;}

-keep class com.flowfoundation.wallet.widgets.webview.JsInterface.** {*;}
-keep class com.flowfoundation.wallet.widgets.webview.JavascriptKt.** {*;}

-keep class com.reown.** {*;}
-keep class net.sqlcipher.** {*;}
-keep class org.trustwallet.core.** { *; }

-keep public enum com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency.** {*;}

-keep public enum com.flowfoundation.wallet.page.profile.subpage.currency.model.Currency$** {
  **[] $VALUES;
  public *;
}

-keep class com.translized.translized_ota.** { *; }

-keep class com.zackratos.ultimatebarx.ultimatebarx.** { *; }
-keep public class * extends androidx.fragment.app.Fragment { *; }

-keep class org.tdf.rlp.** { *; }
-keep class com.flowfoundation.wallet.widgets.webview.fcl.AccountProof {
    @org.tdf.rlp.RLP *;
}

# flow kmm
-keep class org.onflow.flow.infrastructure.** { *; }
-keep class org.onflow.flow.models.** { *; }
-keep enum org.onflow.flow.** { *; }

-dontwarn java.lang.management.RuntimeMXBean
-dontwarn com.google.ar.sceneform.animation.AnimationEngine
-dontwarn com.google.ar.sceneform.animation.AnimationLibraryLoader
-dontwarn com.google.ar.sceneform.assets.Loader
-dontwarn com.google.ar.sceneform.assets.ModelData
-dontwarn com.google.devtools.build.android.desugar.runtime.ThrowableExtension
-dontwarn com.google.protobuf.nano.CodedOutputByteBufferNano
-dontwarn com.google.protobuf.nano.MessageNano
-dontwarn com.jcraft.jzlib.Deflater
-dontwarn com.jcraft.jzlib.Inflater
-dontwarn com.jcraft.jzlib.JZlib$WrapperType
-dontwarn com.jcraft.jzlib.JZlib
-dontwarn com.ning.compress.BufferRecycler
-dontwarn com.ning.compress.lzf.ChunkDecoder
-dontwarn com.ning.compress.lzf.ChunkEncoder
-dontwarn com.ning.compress.lzf.LZFChunk
-dontwarn com.ning.compress.lzf.LZFEncoder
-dontwarn com.oracle.svm.core.annotate.Delete
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn io.grpc.internal.AbstractManagedChannelImplBuilder
-dontwarn io.grpc.internal.AbstractServerImplBuilder
-dontwarn jakarta.xml.bind.annotation.adapters.XmlAdapter
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.ThreadMXBean
-dontwarn javax.naming.NamingEnumeration
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn javax.naming.directory.SearchControls
-dontwarn javax.naming.directory.SearchResult
-dontwarn lombok.NonNull
-dontwarn lzma.sdk.ICodeProgress
-dontwarn lzma.sdk.lzma.Encoder
-dontwarn net.jpountz.lz4.LZ4Compressor
-dontwarn net.jpountz.lz4.LZ4Exception
-dontwarn net.jpountz.lz4.LZ4FastDecompressor
-dontwarn net.jpountz.xxhash.XXHash32
-dontwarn net.jpountz.xxhash.XXHashFactory
-dontwarn org.eclipse.jetty.alpn.ALPN$ClientProvider
-dontwarn org.eclipse.jetty.alpn.ALPN$Provider
-dontwarn org.eclipse.jetty.alpn.ALPN$ServerProvider
-dontwarn org.eclipse.jetty.alpn.ALPN
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego
-dontwarn org.jboss.marshalling.ByteInput
-dontwarn org.jboss.marshalling.ByteOutput
-dontwarn org.jboss.marshalling.Marshaller
-dontwarn org.jboss.marshalling.MarshallerFactory
-dontwarn org.jboss.marshalling.MarshallingConfiguration
-dontwarn org.jboss.marshalling.Unmarshaller
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn com.squareup.okhttp.**
-dontwarn com.google.apphosting.**
-dontwarn com.google.appengine.**
-dontwarn com.google.protos.cloud.sql.**
-dontwarn com.google.cloud.sql.**
-dontwarn javax.activation.**
-dontwarn javax.mail.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**

# Fix for R8 missing classes from gRPC/Netty
-dontwarn com.ning.compress.lzf.util.ChunkDecoderFactory
-dontwarn com.ning.compress.lzf.util.ChunkEncoderFactory
-dontwarn net.jpountz.lz4.LZ4Factory
-dontwarn sun.security.x509.AlgorithmId
-dontwarn sun.security.x509.CertificateAlgorithmId
-dontwarn sun.security.x509.CertificateIssuerName
-dontwarn sun.security.x509.CertificateSerialNumber
-dontwarn sun.security.x509.CertificateSubjectName
-dontwarn sun.security.x509.CertificateValidity
-dontwarn sun.security.x509.CertificateVersion
-dontwarn sun.security.x509.CertificateX509Key
-dontwarn sun.security.x509.X500Name
-dontwarn sun.security.x509.X509CertImpl
-dontwarn sun.security.x509.X509CertInfo