# Flutter and plugins publish their own consumer rules. Keep model metadata used
# by JSON/native bridges while still allowing R8 to optimize the release build.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
