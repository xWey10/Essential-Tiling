# The module is loaded by LSPosed, and its native contracts use names and reflection.
# Preserve all project classes/members; shrink unused AndroidX/Material library code.
-keep class moe.xWey10.tiling.** { *; }
-keepattributes InnerClasses,EnclosingMethod,Signature,*Annotation*
-dontwarn de.robv.android.xposed.**
