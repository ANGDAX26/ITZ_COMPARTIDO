# ITZ-Compartido (app Android, MVP)

App de viajes compartidos para la comunidad del Instituto Tecnológico de Zacatepec, generada a partir del documento
*ITZ-Compartido — Documentación para el cliente* (IEEE 830, UML, factibilidad).
Kotlin + Jetpack Compose (Material 3), minSdk 26, targetSdk 35.

## Cómo abrirla
1. Android Studio Ladybug (2024.2) o más reciente, con JDK 17.
2. File > Open > carpeta `ITZ-Compartido`. Espera el Gradle Sync (descarga Gradle 8.9 y dependencias).
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

## Qué es simulado en este MVP (y por qué)
- **Backend:** `ItzRepository` guarda todo en memoria (se pierde al cerrar la app). Mantiene la misma API que tendría un servidor, así que se puede sustituir por Retrofit + Node.js/Firebase sin tocar las pantallas.
- **Mapa y GPS:** el mapa es un dibujo con la ruta y un vehículo que avanza solo. Para producción: Google Maps Compose u osmdroid + FusedLocationProvider, y calcular la ruta con una API de rutas (requiere API key).
- **Notificaciones push:** son locales; para push reales con la app cerrada hace falta Firebase Cloud Messaging.
- **Validación de identidad:** solo comprueba formato y dominio del correo. La validación real contra el ITZ requiere convenio/API institucional (como indica la factibilidad operativa). Revisa que `DOMINIO_ITZ` sea el correcto.
- **Seguridad (RNF-01/07):** contraseñas en texto plano solo por ser demo. En producción: hash en servidor, HTTPS/TLS y aviso de privacidad conforme a la LFPDPPP.
- Solo Android (el documento menciona también iOS, RNF-06).
