# R8 rules for release builds (full mode). Libraries ship their own consumer
# rules, so this file stays nearly empty. Add a rule for one class only after a
# release build shows the crash; never a package-wide -keep.

# Keep line numbers for readable crash reports; the mapping file de-obfuscates.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
