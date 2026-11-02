# Reglas ProGuard/R8 de TuBolsillo.
#
# Release usa R8 (minify + shrink). Estas reglas cubren las dependencias que
# referencian clases de anotaciones que no llegan al classpath de runtime.

# Tink (EncryptedSharedPreferences) referencia anotaciones de errorprone solo
# para el compilador: no existen en runtime y no hacen falta.
-dontwarn com.google.errorprone.annotations.**

# kotlinx.serialization: los DTO del respaldo se generan con el plugin, pero las
# entidades Room se serializan por nombre de propiedad al exportar.
-keepclassmembers class dev.tubolsillo.app.core.db.entity.** {
    *** Companion;
}
-keepclasseswithmembers class dev.tubolsillo.app.core.db.entity.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class dev.tubolsillo.app.domain.usecase.**$$serializer {
    *** INSTANCE;
}
-keepnames class dev.tubolsillo.app.domain.usecase.**$$serializer

# Koin: los ViewModels se resuelven por constructor; conserva sus nombres para
# que los mensajes de error sigan siendo legibles.
-keepnames class dev.tubolsillo.app.ui.**.*ViewModel

# MediaPipe LLM (modelos locales) referencia anotaciones de protobuf que no
# vienen en el artefacto: no se usan en tiempo de ejecución.
-dontwarn com.google.protobuf.**
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
# Solo se usa texto (no visión/audio): las clases de imagen de MediaPipe no
# están en el artefacto y no se tocan.
-dontwarn com.google.mediapipe.framework.**
-dontwarn com.google.mediapipe.tasks.**
