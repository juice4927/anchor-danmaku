-keepattributes *Annotation*
-dontwarn org.brotli.dec.**

# Lifecycle 2.8 reflectively resolves Compose 1.6's legacy LocalLifecycleOwner.
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static androidx.compose.runtime.ProvidableCompositionLocal getLocalLifecycleOwner();
}
