# Preserve Room entity class names while allowing member optimization.
-keep @androidx.room.Entity class *

# Preserve MediaLibrarySession callback implementations for framework dispatch.
-keepclassmembers class * implements androidx.media3.session.MediaLibraryService$MediaLibrarySession$Callback {
    *;
}
