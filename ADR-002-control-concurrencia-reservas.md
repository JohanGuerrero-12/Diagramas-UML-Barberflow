# ADR-002: Impedir citas superpuestas con validación en el servicio y restricción de exclusión en PostgreSQL

- **Estado:** Aceptado
- **Fecha:** 25/11/2026
- **Decisores:** equipo de desarrollo de BarberFlow

## Contexto y problema

La regla RN-01 establece que un profesional no puede tener dos citas activas superpuestas. En el proceso manual actual esto ocurre una o dos veces por semana, sobre todo en fines de semana, porque dos personas toman el mismo turno por canales distintos (Anexo 1 del informe del Parcial 1).

En el sistema, dos clientes pueden confirmar la misma franja al mismo tiempo: ambas solicitudes pasarían una validación en el servicio y quedarían registradas (condición de carrera).

**Problema:** ¿cómo garantizar la integridad de la agenda bajo concurrencia sin degradar el rendimiento de RNF-03?

## Alternativas evaluadas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Solo validación en el servicio (consulta y luego inserción) | Simple de implementar. | No es segura: entre la consulta y la inserción otra transacción puede reservar la misma franja. |
| Bloqueo pesimista (`SELECT ... FOR UPDATE`) | Serializa las reservas de un profesional. | Genera esperas y riesgo de interbloqueos; reduce la concurrencia. |
| Bloqueo optimista (`@Version`) | Sin bloqueos en base de datos. | No aplica: se insertan filas nuevas y no existe una fila compartida que versionar. |
| Bloqueo distribuido en caché (por ejemplo Redis) | Útil con muchas réplicas. | Agrega infraestructura y un punto de falla adicionales. |
| Validación en el servicio + restricción `EXCLUDE` en PostgreSQL (elegida) | La base de datos garantiza la regla aunque la aplicación falle; no usa bloqueos explícitos. | Acopla el diseño a PostgreSQL (extensión `btree_gist`). |

## Decisión tomada

Se combinan dos barreras. **(1)** El servicio valida horario laboral, fechas pasadas y solapamientos dentro de una transacción (`@Transactional`) para dar una respuesta temprana y un mensaje claro al usuario. **(2)** La base de datos aplica una restricción de exclusión sobre el rango de tiempo de cada cita activa, que actúa como última barrera.

Si la restricción se viola, la excepción `DataIntegrityViolationException` se traduce en `ConflictoAgendaException` y el `@RestControllerAdvice` responde **409 Conflict**; las violaciones de reglas de negocio responden **422 Unprocessable Entity**.

```sql
ALTER TABLE cita ADD CONSTRAINT cita_sin_solapamiento
    EXCLUDE USING gist (
        id_profesional WITH =,
        tsrange(fecha_hora_inicio, fecha_hora_fin) WITH &&
    ) WHERE (estado IN ('PROGRAMADA','CONFIRMADA','EN_ATENCION'));
```

## Consecuencias

**Positivas**

- La integridad de la agenda no depende de que todas las rutas de código validen correctamente (RNF-07).
- No hay bloqueos explícitos, por lo que el rendimiento se mantiene (RNF-03).
- Funciona igual con varias réplicas del backend (RNF-04).
- Las citas canceladas o finalizadas liberan la franja automáticamente, porque la restricción solo cubre los estados activos.

**Negativas (trade-offs)**

- Dependencia de PostgreSQL: migrar de motor exige rediseñar esta regla.
- La restricción no se puede expresar en JPA; vive en la migración SQL (Flyway) y debe mantenerse allí.
- Hay que traducir correctamente el error de la base de datos a HTTP 409.
- El bloqueo temporal de cinco minutos descrito en RF-07 no queda resuelto por esta decisión y requiere un mecanismo adicional.
