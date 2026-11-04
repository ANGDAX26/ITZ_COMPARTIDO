# ITZ-Compartido (app Android, MVP)

App de viajes compartidos para la comunidad del Instituto Tecnológico de Zacatepec, generada a partir del documento
*ITZ-Compartido — Documentación para el cliente* (IEEE 830, UML, factibilidad).
Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 35.

## Cómo abrirla
1. Android Studio Ladybug (2024.2) o más reciente, con JDK 17.
2. File > Open > carpeta `ITZ-Compartido`. Usa JDK 17 o 21 para Gradle (no JDK 25). Espera el Gradle Sync (descarga Gradle 8.13 y dependencias).
3. Run en un emulador o teléfono con Android 8.0+.
4. Para el APK: Build > Build Bundle(s) / APK(s) > Build APK(s).

## Cuentas de demostración (contraseña `123456`, admin `admin123`)
| Rol | Correo |
|---|---|
| Pasajera | sofia@zacatepec.tecnm.mx |
| Conductor validado | carlos@zacatepec.tecnm.mx |
| Conductora sin validar | mariana@zacatepec.tecnm.mx |
| Administrador | admin@zacatepec.tecnm.mx / `admin123` |

Flujo de demo sugerido: Sofía solicita lugar en un viaje de Carlos → cierra sesión → Carlos acepta e inicia el viaje →
ruta en vivo y SOS → finaliza (división del costo) → ambos califican. Con `admin` se valida a Mariana y se ven reportes y alertas.

## Trazabilidad con el documento
| Requisito | Dónde está |
|---|---|
| RF-01 registro con matrícula y correo institucional | `AuthScreen`, `ItzRepository.registrar` (dominio en `DOMINIO_ITZ`) |
| RF-02 registro de conductor (vehículo, licencia) | pestaña Conducir, `registrarConductor` |
| RF-03 publicar viaje | pestaña Conducir, `publicarViaje` |
| RF-04 buscar y filtrar (zona, horario, calificación) | pestaña Buscar |
| RF-05 solicitar lugar / aceptar o rechazar | Buscar y Mis viajes |
| RF-06 ruta segura en mapa | `ViajeScreen` + `MapaSimulado`, `RutaSegura.calcular` |
| RF-07 ubicación en tiempo real | `ViajeScreen` (posición simulada cada 1 s) |
| RF-08 división del costo | `finalizarViaje` (entre conductor y pasajeros) |
| RF-09 calificar y comentar | `ViajeScreen` al finalizar |
| RF-10 botón SOS | `ViajeScreen`: alerta a administración + SMS al contacto de confianza |
| RF-11 administración | `AdminScreen` (validar documentos, suspender, reportes, alertas) |
| RF-12 notificaciones | `Notifier` (notificaciones locales) + pestaña Avisos |

## Base de datos local (Room)

Los cambios se guardan en `itz_compartido.db`, en el almacenamiento privado de Android,
y se cargan antes de habilitar las pantallas. Se conservan al cerrar, forzar la detención o
reiniciar la app. Cerrar sesión no borra datos; borrar el almacenamiento o desinstalar sí.
La sesión empieza cerrada al abrir un proceso nuevo.

- Tablas: `usuarios`, `viajes`, `solicitudes_viaje`, `pagos`, `calificaciones`,
  `notificaciones` y `alertas_sos`.
- Los usuarios incluyen el vehículo actual y el contacto de confianza. Los viajes incluyen
  los puntos y zonas de la ruta simulada. Las fechas y horas usan formato ISO; las listas de ruta, JSON.
- Las cuentas y viajes de demostración se insertan únicamente cuando la base está vacía.
  Los identificadores se recuperan a partir del máximo guardado para evitar duplicados tras reiniciar.
- Las escrituras corren fuera del hilo principal y se serializan. Aceptar una solicitud guarda
  el asiento, la solicitud y el aviso en una transacción; finalizar guarda el pago y el estado del viaje
  en otra. La interfaz y las notificaciones se actualizan después de confirmar la escritura.
- Hay claves foráneas, correo/matrícula únicos y una clave compuesta para impedir reseñas duplicadas.
  Las contraseñas se guardan como hashes PBKDF2-HMAC-SHA256 con sal individual.
- El esquema de Room se exporta en `app/schemas`. Los próximos cambios de versión deben incluir
  una migración; no se habilita borrado automático de la base ante cambios de esquema.

El PDF adjunto es un diseño conceptual más amplio. Esta primera versión implementa la persistencia
de las funciones existentes del MVP. Conserva sus IDs enteros, un vehículo/contacto por usuario,
hora de salida sin fecha y un resumen de pago por viaje. No implementa todavía las 13 tablas
normalizadas, UUIDs, pagos individuales, paradas geográficas reales, auditoría, cola de sincronización
ni WorkManager. Para sincronizar entre dispositivos hacen falta un backend, autenticación remota
y definir cómo resolver conflictos de reservas; los datos actuales son locales a cada instalación.

Para inspeccionarla: ejecuta la app en Android Studio y abre **App Inspection > Database Inspector**.
Prueba manual: registra un usuario, actualiza su perfil, fuerza la detención y vuelve a abrir la app;
inicia sesión con esa cuenta y comprueba los datos.

Pruebas de integración con Robolectric (sin emulador): `gradle :app:testDebugUnitTest`.
Verifican registro/login, perfil y vehículo, competencia por el último asiento, ciclo del viaje,
calificaciones, SOS, lectura desde otra conexión y recuperación ante un fallo real de SQLite.
Usan una base con nombre separado de la base normal de la app.

## Qué es simulado en este MVP (y por qué)
- **Backend:** no hay servidor ni intercambio de datos entre teléfonos; la persistencia es local con Room.
- **Mapa y GPS:** el mapa es un dibujo con la ruta y un vehículo que avanza solo. Para producción: Google Maps Compose u osmdroid + FusedLocationProvider, y calcular la ruta con una API de rutas (requiere API key).
- **Notificaciones push:** son locales; para push reales con la app cerrada hace falta Firebase Cloud Messaging.
- **Validación de identidad:** solo comprueba formato y dominio del correo. La validación real contra el ITZ requiere convenio/API institucional (como indica la factibilidad operativa). Revisa que `DOMINIO_ITZ` sea el correcto.
- **Seguridad (RNF-01/07):** autenticación local con hash de contraseña. En producción se requiere autenticación y autorización en servidor, HTTPS/TLS y el aviso de privacidad correspondiente.
- Solo Android (el documento menciona también iOS, RNF-06).
