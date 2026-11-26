# ADR-001: Adoptar un Monolito Modular con capas internas como estilo arquitectónico

- **Estado:** Aceptado
- **Fecha:** 25/11/2026
- **Decisores:** equipo de desarrollo de BarberFlow

## Contexto y problema

BarberFlow integra nueve módulos de negocio (autenticación, citas y disponibilidad, pagos, comisiones, inventario, fidelización, reportes, notificaciones y administración de establecimientos) que comparten datos y deben operar de forma coherente.

El proyecto lo desarrolla un equipo de dos personas, se despliega en un único servidor VPS con Docker y debe cumplir RNF-03 (2 s para el 95 % de las solicitudes con 200 usuarios concurrentes), RNF-07 (operaciones atómicas entre pago, comisión, transacción, inventario y puntos) y RNF-04 (crecer sin reescribir).

**Problema:** ¿qué estilo arquitectónico permite cumplir estos atributos de calidad con la complejidad operativa que el equipo puede sostener?

## Alternativas evaluadas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Arquitectura en capas simple (propuesta del Parcial 1) | Es conocida y rápida de construir. | No define fronteras entre módulos; el acoplamiento crece con cada funcionalidad. |
| Monolito modular (elegida) | Una sola unidad desplegable, transacciones ACID locales y módulos con fronteras claras. | Exige disciplina para no cruzar fronteras entre módulos; escala como una sola unidad. |
| Arquitectura hexagonal / Clean Architecture pura | Aísla totalmente el dominio de la infraestructura. | Requiere puertos, adaptadores y mapeos adicionales: demasiada ceremonia para dos desarrolladores en un semestre. |
| Microservicios | Escalado y despliegue independientes por servicio. | Transacciones distribuidas, observabilidad y despliegue complejos; innecesarios para 200 usuarios concurrentes. |

## Decisión tomada

Se adopta un **monolito modular**: un único backend Spring Boot dividido en módulos de negocio. Dentro de cada módulo se mantiene la separación en capas `controller → service → repository → domain`, con DTOs, inyección de dependencias por constructor y manejo centralizado de errores (`@RestControllerAdvice`).

El frontend React (PWA) se despliega como contenedor independiente y consume la API REST.

## Consecuencias

**Positivas**

- Las operaciones críticas (pago, comisión, inventario y puntos) se ejecutan en una sola transacción local (RNF-07).
- Las llamadas entre módulos son en memoria, sin latencia de red (RNF-03).
- Un solo repositorio y un solo despliegue reducen la carga operativa del equipo.
- Los módulos aislados permiten extraer uno a servicio independiente si el crecimiento lo exige (RNF-04).

**Negativas (trade-offs)**

- La disciplina de módulos depende del equipo: el compilador no impide que un módulo use clases internas de otro.
- Toda la aplicación escala y se despliega como una unidad.
- Un error grave en un módulo puede afectar a todo el backend.
