# ADR-003: Autenticar con JWT sin estado, contraseñas con BCrypt y control de acceso por roles

- **Estado:** Aceptado
- **Fecha:** 25/11/2026
- **Decisores:** equipo de desarrollo de BarberFlow

## Contexto y problema

El sistema tiene seis roles (cliente, profesional, recepcionista, propietario, responsable de inventario y administrador de plataforma) y atiende a varios establecimientos cuyos datos deben permanecer aislados (RF-02).

La API REST es consumida por una SPA/PWA en React. RNF-01 exige HTTPS, contraseñas con hash adaptativo, bloqueo tras cinco intentos fallidos y protección frente a OWASP Top 10; RNF-04 exige poder escalar horizontalmente la capa de aplicación.

**Problema:** ¿qué esquema de autenticación y autorización cumple RNF-01 sin impedir el escalamiento horizontal?

## Alternativas evaluadas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Sesión de servidor con cookie (`JSESSIONID`) | Revocación inmediata y modelo conocido. | Requiere sesiones compartidas o *sticky sessions* al escalar; exige protección CSRF. |
| JWT sin estado (elegida) | No guarda sesión en el servidor; escala con réplicas y encaja con una SPA. | Revocar un token antes de su vencimiento es difícil. |
| OAuth2 / OIDC con proveedor externo (Keycloak, Auth0, Google) | Delega la gestión de identidad y la seguridad. | Agrega infraestructura, costo y una dependencia externa para el alcance del proyecto. |
| Claves de API | Muy simples. | No identifican usuarios finales ni sus roles. |

## Decisión tomada

Se usa **Spring Security** con un filtro (`JwtAuthenticationFilter`) que valida la firma y el vencimiento del token en cada petición. El token incluye la identidad del usuario, su rol y su establecimiento.

Las contraseñas se almacenan con **BCrypt**; tras cinco intentos fallidos la cuenta se bloquea temporalmente. La autorización se aplica por rol en la configuración de seguridad, y cada consulta se filtra por establecimiento para mantener el aislamiento entre negocios. Toda comunicación viaja por HTTPS (TLS 1.2 o superior) terminado en el proxy Nginx.

## Consecuencias

**Positivas**

- El backend no guarda sesión, por lo que se puede replicar detrás del proxy (RNF-04).
- Encaja de forma natural con la SPA/PWA y con clientes móviles futuros.
- Un único punto de validación (el filtro) protege todos los módulos (RNF-01).
- No depende de un servicio de identidad externo.

**Negativas (trade-offs)**

- Un token robado es válido hasta que vence; se mitiga con vencimiento corto y renovación mediante *refresh token*.
- El almacenamiento del token en el navegador exige cuidado frente a XSS.
- La clave de firma es un secreto crítico que debe gestionarse fuera del código (variables de entorno).
- El aislamiento por establecimiento debe aplicarse en cada consulta: olvidarlo en una sola puede exponer datos de otro negocio.
