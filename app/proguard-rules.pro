# R8 keeps. Add a rule only for a reflection-dependent entry point, and say why.

# kotlinx.serialization generates serializers looked up by name at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.siteskin.core.** {
    *** Companion;
}
-keepclasseswithmembers class dev.siteskin.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Flatten the package hierarchy in release builds (MASVS-RESILIENCE-1).
-repackageclasses ''
-allowaccessmodification
