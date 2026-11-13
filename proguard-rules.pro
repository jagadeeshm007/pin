# R8 rules for release builds (full mode). Libraries ship their own consumer
# rules, so this file stays nearly empty. Add a rule for one class only after a
# release build shows the crash. No package-wide -keep for our code; third-party JNI
# libraries may need one, with the reason written next to it.

# Keep line numbers for readable crash reports; the mapping file de-obfuscates.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# sherpa-onnx (ADR-0009): its native code reads config fields and calls methods by name
# through JNI, and the AAR ships no consumer rules. Scoped to the third-party package.
-keep class com.k2fsa.sherpa.onnx.** { *; }
