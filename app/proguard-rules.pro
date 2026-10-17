# App classes (models are (de)serialized with kotlinx.serialization, receiver is referenced from the manifest).
-keep class de.mirrafit.app.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
