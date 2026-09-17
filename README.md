# Agenda Inteligente Android — versión 2.0

Proyecto Android nativo en Kotlin + Jetpack Compose basado en las capturas proporcionadas.

## Funciones

- Calendario mensual real basado en `YearMonth`.
- Selección de fecha.
- Indicador de días que tienen eventos.
- Alta y edición de eventos.
- Eliminación de eventos.
- Campo de fecha `AAAA-MM-DD`.
- Opción de recordatorio en eventos.
- Pantalla de notas con búsqueda.
- Alta y edición de notas.
- Eliminar y fijar/desfijar notas.
- Persistencia local mediante SharedPreferences + JSON.
- Modo oscuro desde Configuración.
- Canal de notificaciones preparado y permiso POST_NOTIFICATIONS declarado.
- Diseño adaptado a teléfonos y basado visualmente en las capturas.

## Abrir

Abrir la carpeta en Android Studio y ejecutar la aplicación.

## Siguiente etapa para producción

Para recordatorios exactos se recomienda conectar los eventos con AlarmManager/WorkManager y mostrar NotificationCompat cuando llegue la fecha/hora. También se puede integrar el calendario del teléfono, sincronización en nube, cuentas de usuario y Room si se requiere una base de datos relacional.
