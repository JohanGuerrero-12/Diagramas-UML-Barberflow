# BarberFlow · Hito 2: Arquitectura, C4 y UML

Equipo: Cristian Johan Guerrero Mejia · Anderson Steven Guerrero Mejia

Los diagramas están escritos como código (Mermaid) en la carpeta `diagramas/`. Se visualizan directamente en GitHub o pegando el contenido de cada `.mmd` en https://mermaid.live para exportar PNG/SVG.

---

## 1. Estilo arquitectónico: Monolito Modular (con capas internas)

**Decisión.** BarberFlow se construye como **un único backend Spring Boot desplegable**, dividido en **módulos de negocio** (autenticación, citas, disponibilidad, pagos, comisiones, inventario, fidelización, reportes, notificaciones). Dentro de cada módulo se mantiene la separación en capas `controller → service → repository → domain`, con DTOs y un manejo centralizado de errores.

**Por qué responde a los RNF críticos:**

| RNF | Cómo lo resuelve el estilo |
|-----|----------------------------|
| **RNF-03 Rendimiento** (2 s con 200 usuarios concurrentes) | Las llamadas entre módulos son en memoria, sin latencia de red. Una consulta de disponibilidad es una sola consulta a PostgreSQL con índices. |
| **Concurrencia** (RF-07, RNF-07) | Una sola base de datos transaccional permite usar `@Transactional` y la restricción `EXCLUDE` de PostgreSQL para impedir citas superpuestas. Con microservicios esto exigiría transacciones distribuidas. |
| **RNF-01 Seguridad** | Un único punto de entrada (filtro JWT + RBAC) protege todos los módulos. El proxy Nginx termina HTTPS. |
| **RNF-07 Integridad** | Pago, comisión, transacción, inventario y puntos se ejecutan en **una misma transacción atómica**, algo natural en un monolito. |
| **RNF-04 Escalabilidad** | El backend es *stateless* (JWT sin sesión en servidor), por lo que se pueden levantar varias réplicas detrás del proxy. Si un módulo crece, se puede extraer después porque ya está aislado. |
| **Mantenibilidad** | Equipo de 2 personas: un solo repositorio, un solo despliegue y fronteras claras entre módulos, sin la complejidad operativa de microservicios. |

**Alternativas descartadas:** *Microservicios* (complejidad operativa y transacciones distribuidas innecesarias para 2 desarrolladores y 200 usuarios concurrentes) y *Arquitectura Hexagonal pura* (más ceremonia de puertos y adaptadores de la que el equipo puede sostener en este semestre). Esta decisión se formaliza en el ADR-001.

> Nota de coherencia: el informe del Parcial 1 describe "tres capas". Este hito lo refina: se mantiene la separación por capas, pero organizada por módulos de negocio (monolito modular).

---

## 2. Modelo C4

### Nivel 1 · Diagrama de Contexto
Muestra BarberFlow como una caja única, los 6 roles de usuario y los 2 sistemas externos.

```mermaid
flowchart TB
    cliente["<b>Cliente</b><br/>[Persona]<br/>Reserva citas, consulta su historial y puntos"]
    prof["<b>Profesional</b><br/>[Persona]<br/>Barbero o estilista: gestiona su agenda y ve sus comisiones"]
    recep["<b>Recepcionista</b><br/>[Persona]<br/>Reserva en nombre del cliente y registra pagos"]
    prop["<b>Propietario / Administrador</b><br/>[Persona]<br/>Configura el negocio y consulta reportes"]
    inv["<b>Responsable de inventario</b><br/>[Persona]<br/>Controla insumos y alertas de stock"]
    plat["<b>Administrador de plataforma</b><br/>[Persona]<br/>Administra los establecimientos suscritos"]

    bf["<b>BarberFlow</b><br/>[Sistema de software]<br/>Gestión integral de citas, pagos, comisiones,<br/>inventario y fidelización"]

    msg["<b>Proveedor de mensajería</b><br/>[Sistema externo]<br/>Envía correos y mensajes de texto"]
    pay["<b>Pasarela de pagos</b><br/>[Sistema externo]<br/>Procesa pagos electrónicos"]

    cliente -->|"Reserva y consulta"| bf
    prof -->|"Gestiona agenda y atención"| bf
    recep -->|"Agenda y cobra"| bf
    prop -->|"Configura y consulta reportes"| bf
    inv -->|"Registra entradas de insumos"| bf
    plat -->|"Administra establecimientos"| bf

    bf -->|"Envía confirmaciones y recordatorios<br/>[HTTPS / API REST]"| msg
    bf -->|"Solicita cobros electrónicos<br/>[HTTPS / API REST]"| pay

    classDef persona fill:#08427b,stroke:#052e56,color:#fff
    classDef sistema fill:#1168bd,stroke:#0b4884,color:#fff
    classDef externo fill:#999,stroke:#6b6b6b,color:#fff
    class cliente,prof,recep,prop,inv,plat persona
    class bf sistema
    class msg,pay externo
```

### Nivel 2 · Diagrama de Contenedores
Unidades desplegables: proxy, SPA/PWA, API REST y base de datos, con tecnologías y protocolos.

```mermaid
flowchart TB
    usuarios["<b>Usuarios de BarberFlow</b><br/>[Personas]<br/>Cliente, profesional, recepcionista,<br/>propietario, inventario, admin plataforma"]

    subgraph BF["Sistema BarberFlow (servidor VPS con Docker)"]
        proxy["<b>Proxy inverso</b><br/>[Nginx + SSL Let's Encrypt]<br/>Termina HTTPS y enruta el tráfico"]
        spa["<b>Aplicación web (SPA / PWA)</b><br/>[React + Vite, servida por Nginx]<br/>Portales por rol, funciona en móvil y escritorio"]
        api["<b>API REST</b><br/>[Java 17 + Spring Boot 3]<br/>Reglas de negocio, seguridad JWT y acceso a datos"]
        db[("<b>Base de datos</b><br/>[PostgreSQL 16]<br/>Citas, pagos, comisiones,<br/>inventario y fidelización")]
    end

    msg["<b>Proveedor de mensajería</b><br/>[Sistema externo]"]
    pay["<b>Pasarela de pagos</b><br/>[Sistema externo]"]

    usuarios -->|"Navegador<br/>[HTTPS 443]"| proxy
    proxy -->|"Entrega la SPA<br/>[HTTP]"| spa
    proxy -->|"Rutas /api/*<br/>[HTTP / JSON]"| api
    spa -.->|"Llamadas desde el navegador<br/>[HTTPS / JSON + JWT]"| proxy
    api -->|"Lee y escribe<br/>[JDBC / SQL, puerto 5432]"| db
    api -->|"Envía notificaciones<br/>[HTTPS / REST]"| msg
    api -->|"Solicita cobros<br/>[HTTPS / REST]"| pay

    classDef persona fill:#08427b,stroke:#052e56,color:#fff
    classDef cont fill:#1168bd,stroke:#0b4884,color:#fff
    classDef datos fill:#2e7d32,stroke:#1b5e20,color:#fff
    classDef externo fill:#999,stroke:#6b6b6b,color:#fff
    class usuarios persona
    class proxy,spa,api cont
    class db datos
    class msg,pay externo
```

### Nivel 3 · Diagrama de Componentes (Backend)
Organización interna de la API: seguridad, controladores, servicios, repositorios, eventos y manejo de errores.

```mermaid
flowchart TB
    spa["<b>SPA React</b><br/>[Contenedor]"]
    db[("<b>PostgreSQL</b><br/>[Contenedor]")]
    msg["<b>Proveedor de mensajería</b><br/>[Externo]"]
    pay["<b>Pasarela de pagos</b><br/>[Externo]"]

    subgraph API["Contenedor: API REST (Spring Boot)"]
        direction TB

        subgraph SEG["1. Seguridad"]
            jwt["<b>JwtAuthenticationFilter</b><br/>Valida el token en cada petición"]
            rbac["<b>SecurityConfig (RBAC)</b><br/>Permisos por rol y por establecimiento"]
        end

        subgraph CTRL["2. Controladores REST (paquete controller + dto)"]
            cAuth["AuthController<br/>/api/v1/auth"]
            cCita["CitaController<br/>/api/v1/citas"]
            cDisp["DisponibilidadController<br/>/api/v1/disponibilidad"]
            cPago["PagoController<br/>/api/v1/pagos"]
            cInv["InventarioController<br/>/api/v1/insumos"]
            cRep["ReporteController<br/>/api/v1/reportes"]
        end

        subgraph SRV["3. Servicios de negocio (paquete service)"]
            sAuth["AuthService"]
            sCita["CitaService"]
            sDisp["DisponibilidadService"]
            sPago["PagoService"]
            sCom["ComisionService"]
            sInv["InventarioService"]
            sFid["FidelizacionService"]
            sRep["ReporteService"]
        end

        subgraph EVT["4. Eventos (paquete event)"]
            evt["<b>CitaEventListener</b><br/>Escucha CitaCreada, CitaCancelada<br/>y envía notificaciones tras el commit"]
            sNot["NotificacionService"]
        end

        subgraph REPO["5. Repositorios (paquete repository, Spring Data JPA)"]
            rCita["CitaRepository"]
            rUsr["UsuarioRepository"]
            rPago["PagoRepository"]
            rIns["InsumoRepository"]
            rOtros["Horario, Servicio, Comision,<br/>Fidelizacion Repository"]
        end

        geh["<b>GlobalExceptionHandler</b><br/>@RestControllerAdvice: traduce errores a 401, 403, 404, 409 y 422"]
    end

    spa -->|"HTTPS / JSON + JWT"| jwt
    jwt --> rbac
    rbac --> cAuth & cCita & cDisp & cPago & cInv & cRep

    cAuth --> sAuth
    cCita --> sCita
    cDisp --> sDisp
    cPago --> sPago
    cInv --> sInv
    cRep --> sRep

    sCita --> sDisp
    sPago --> sCom
    sPago --> sInv
    sPago --> sFid
    sCita -.->|"publica evento"| evt
    evt --> sNot

    sAuth --> rUsr
    sCita --> rCita
    sDisp --> rCita
    sPago --> rPago
    sInv --> rIns
    sCom --> rOtros
    sFid --> rOtros
    sRep --> rPago

    rCita & rUsr & rPago & rIns & rOtros -->|"JDBC / SQL"| db
    sNot -->|"HTTPS / REST"| msg
    sPago -->|"HTTPS / REST"| pay

    cCita & cPago & cInv -.->|"excepciones"| geh

    classDef ext fill:#999,stroke:#6b6b6b,color:#fff
    classDef comp fill:#85bbf0,stroke:#5d99c6,color:#000
    classDef datos fill:#2e7d32,stroke:#1b5e20,color:#fff
    class msg,pay,spa ext
    class db datos
```

---

## 3. Modelado UML

### 3.1 Diagrama de Clases de Dominio
Es un único modelo dividido en dos vistas para que sea legible. Las clases marcadas `<<Vista A>>` en la vista B están definidas en la vista A.

**Vista A: usuarios, reservas y agenda**

```mermaid
classDiagram
    direction TB

    class Usuario {
        <<abstract>>
        -Long idUsuario
        -String nombres
        -String apellidos
        -String correo
        -String telefono
        -String hashClave
        -RolUsuario rol
        -EstadoUsuario estado
        -int intentosFallidos
        -LocalDateTime bloqueadoHasta
        -LocalDateTime fechaRegistro
        +cambiarClave(String nueva) void
        +bloquear() void
        +estaActivo() boolean
    }
    class Cliente {
        -LocalDate fechaNacimiento
        -String preferencias
        -boolean autorizaTratamientoDatos
    }
    class Profesional {
        -String especialidad
        -BigDecimal calificacionPromedio
        -boolean activo
        +puedePrestar(Servicio s) boolean
        +darDeBaja() void
    }
    class Administrador {
        -CargoAdministrativo cargo
    }
    class Establecimiento {
        -Long idEstablecimiento
        -String nombre
        -String nit
        -TipoNegocio tipo
        -String direccion
        -String ciudad
        -String telefono
        -String zonaHoraria
        -int anticipacionMinHoras
        -EstadoEstablecimiento estado
        +activar() void
        +suspender() void
    }
    class Servicio {
        -Long idServicio
        -String nombre
        -CategoriaServicio categoria
        -String descripcion
        -int duracionMin
        -BigDecimal precio
        -boolean activo
        +actualizarTarifa(BigDecimal precio) void
        +desactivar() void
    }
    class Horario {
        -Long idHorario
        -DayOfWeek diaSemana
        -LocalTime horaInicio
        -LocalTime horaFin
        -LocalTime descansoInicio
        -LocalTime descansoFin
        -LocalDate vigenteDesde
        +contieneFranja(LocalTime inicio, LocalTime fin) boolean
    }
    class Cita {
        -Long idCita
        -LocalDateTime fechaHoraInicio
        -LocalDateTime fechaHoraFin
        -EstadoCita estado
        -BigDecimal valorEstimado
        -String observaciones
        -String motivoCancelacion
        -LocalDateTime fechaCreacion
        +agregarDetalle(Servicio s) void
        +calcularDuracion() int
        +cambiarEstado(EstadoCita nuevo) void
        +cancelar(String motivo) void
        +iniciarAtencion() void
        +finalizar() void
    }
    class DetalleCita {
        -Long idDetalle
        -BigDecimal precioAplicado
        -int duracionMin
        +calcularSubtotal() BigDecimal
    }
    class Notificacion {
        -Long idNotificacion
        -TipoNotificacion tipo
        -CanalEnvio canal
        -String mensaje
        -LocalDateTime fechaProgramada
        -EstadoEnvio estadoEnvio
        -int intentos
        +reintentar() void
    }
    class EstadoCita {
        <<enumeration>>
        PROGRAMADA
        CONFIRMADA
        EN_ATENCION
        FINALIZADA
        CANCELADA
        NO_ASISTIO
    }
    class RolUsuario {
        <<enumeration>>
        CLIENTE
        PROFESIONAL
        ADMINISTRADOR
    }
    class CargoAdministrativo {
        <<enumeration>>
        PROPIETARIO
        RECEPCIONISTA
        RESPONSABLE_INVENTARIO
        ADMIN_PLATAFORMA
    }

    Usuario <|-- Cliente
    Usuario <|-- Profesional
    Usuario <|-- Administrador

    Establecimiento "1" --> "0..*" Profesional : emplea
    Establecimiento "1" --> "0..*" Administrador : administra
    Establecimiento "1" --> "0..*" Servicio : ofrece
    Profesional "1" --> "1..*" Horario : define
    Cliente "1" --> "0..*" Cita : solicita
    Profesional "1" --> "0..*" Cita : atiende
    Establecimiento "1" --> "0..*" Cita : recibe
    Cita "1" *-- "1..*" DetalleCita : se compone de
    DetalleCita "0..*" --> "1" Servicio : refiere a
    Cita "1" --> "0..*" Notificacion : genera
    Profesional "0..*" --> "0..*" Servicio : presta

    Cita ..> EstadoCita
    Usuario ..> RolUsuario
    Administrador ..> CargoAdministrativo
```

**Vista B: pagos, comisiones, fidelización e inventario**

```mermaid
classDiagram
    direction TB

    class Cita {
        <<VistaA>>
    }
    class Cliente {
        <<VistaA>>
    }
    class Profesional {
        <<VistaA>>
    }
    class Servicio {
        <<VistaA>>
    }
    class Establecimiento {
        <<VistaA>>
    }

    class Pago {
        -Long idPago
        -BigDecimal subtotal
        -BigDecimal descuento
        -BigDecimal valorCobrado
        -MetodoPago metodoPago
        -String referencia
        -LocalDateTime fechaPago
        -EstadoPago estado
        +aplicarDescuento(Promocion p) void
        +anular(String motivo) void
    }
    class Promocion {
        -Long idPromocion
        -String codigo
        -TipoDescuento tipoDescuento
        -BigDecimal valor
        -LocalDate vigenciaDesde
        -LocalDate vigenciaHasta
        -boolean activa
        +estaVigente(LocalDate f) boolean
        +calcularDescuento(BigDecimal subtotal) BigDecimal
    }
    class Transaccion {
        -Long idTransaccion
        -TipoTransaccion tipo
        -String concepto
        -BigDecimal monto
        -LocalDateTime fecha
    }
    class ReglaComision {
        -Long idRegla
        -BigDecimal porcentajeProfesional
        -BigDecimal porcentajeEstablecimiento
        -boolean descontarInsumos
        -LocalDate vigenteDesde
        -LocalDate vigenteHasta
        +validarSuma100() boolean
        +calcular(BigDecimal cobrado, BigDecimal insumos) ResultadoComision
    }
    class Comision {
        -Long idComision
        -BigDecimal baseComision
        -BigDecimal costoInsumos
        -BigDecimal valorProfesional
        -BigDecimal valorEstablecimiento
        -EstadoLiq estadoLiquidacion
        +liquidar() void
    }
    class Fidelizacion {
        -Long idFidelizacion
        -int puntosAcumulados
        -int puntosRedimidos
        -NivelFidelidad nivel
        +acumularPuntos(BigDecimal valorCobrado) int
        +redimirPuntos(int puntos) BigDecimal
        +consultarSaldo() int
    }
    class Inventario {
        -Long idInventario
        -String ubicacion
        -LocalDate fechaUltimoConteo
    }
    class Insumo {
        -Long idInsumo
        -String nombre
        -String unidadMedida
        -BigDecimal existencias
        -BigDecimal stockMinimo
        -BigDecimal costoUnitario
        -EstadoInsumo estado
        +descontar(BigDecimal cantidad) boolean
        +registrarEntrada(BigDecimal cantidad, BigDecimal costo) void
        +estaBajoMinimo() boolean
    }
    class ServicioInsumo {
        -BigDecimal cantidadEstandar
    }
    class ConsumoInsumo {
        -Long idConsumo
        -BigDecimal cantidad
        -BigDecimal costoTotal
        -LocalDateTime fechaConsumo
    }
    class MovimientoInventario {
        -Long idMovimiento
        -TipoMovimiento tipo
        -BigDecimal cantidad
        -BigDecimal saldoResultante
        -LocalDateTime fecha
    }
    class EstadoPago {
        <<enumeration>>
        PENDIENTE
        APROBADO
        RECHAZADO
        ANULADO
    }
    class MetodoPago {
        <<enumeration>>
        EFECTIVO
        TARJETA
        TRANSFERENCIA
        BILLETERA
    }
    class TipoTransaccion {
        <<enumeration>>
        INGRESO
        EGRESO
        COMISION
        AJUSTE
    }
    class EstadoLiq {
        <<enumeration>>
        PENDIENTE
        LIQUIDADA
    }
    class TipoMovimiento {
        <<enumeration>>
        ENTRADA
        CONSUMO
        AJUSTE
    }

    Cita "1" --> "0..1" Pago : se cobra con
    Pago "0..*" --> "0..1" Promocion : aplica
    Pago "1" --> "0..*" Transaccion : registra
    Pago "1" --> "1" Comision : genera
    ReglaComision "1" --> "0..*" Comision : rige
    Profesional "1" --> "0..*" Comision : percibe
    Establecimiento "1" --> "0..*" ReglaComision : define
    Cliente "1" --> "0..*" Fidelizacion : posee
    Pago ..> Fidelizacion : acumula puntos

    Establecimiento "1" --> "1" Inventario : posee
    Inventario "1" *-- "0..*" Insumo : contiene
    Servicio "1" --> "0..*" ServicioInsumo : requiere
    Insumo "1" --> "0..*" ServicioInsumo : receta
    Cita "1" --> "0..*" ConsumoInsumo : consume
    Insumo "1" --> "0..*" ConsumoInsumo : se consume en
    Insumo "1" --> "0..*" MovimientoInventario : historial
    ConsumoInsumo "1" --> "0..1" MovimientoInventario : origina

    Pago ..> EstadoPago
    Pago ..> MetodoPago
    Transaccion ..> TipoTransaccion
    Comision ..> EstadoLiq
    MovimientoInventario ..> TipoMovimiento
```

**Cardinalidades destacadas:** 1:1 (Establecimiento–Inventario) · 1:N (Cita–DetalleCita, Establecimiento–Servicio) · N:M (Servicio–Insumo mediante `ServicioInsumo`; Profesional–Servicio).

### 3.2 Diagrama de Secuencia Full Stack: Reservar una cita (RF-06 y RF-07)
Flujo transaccional más crítico (necesidad NEC-04, prioridad Crítica). Incluye los errores **401, 400, 422 y 409**, y el evento posterior de notificación.

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente
    participant SPA as React SPA
    participant SF as JwtAuthFilter
    participant CC as CitaController
    participant CS as CitaService
    participant HR as HorarioRepository
    participant CR as CitaRepository
    participant DB as PostgreSQL
    participant EV as CitaEventListener
    participant MSG as Proveedor mensajería

    C->>SPA: Elige servicio, profesional, fecha y hora, y pulsa Confirmar
    SPA->>SF: POST /api/v1/citas (JWT + SolicitudCitaDTO)

    alt Token ausente, vencido o inválido
        SF-->>SPA: 401 Unauthorized
        SPA-->>C: Sesión expirada, inicia sesión de nuevo
    else Token válido
        SF->>CC: Petición autenticada (rol CLIENTE o RECEPCIONISTA)
        CC->>CC: Valida el DTO con @Valid

        alt DTO con campos inválidos
            CC-->>SPA: 400 Bad Request
            SPA-->>C: Muestra los campos con error
        else DTO válido
            CC->>CS: reservar(solicitud)
            CS->>HR: buscarVigente(profesional, fecha)
            HR->>DB: SELECT horario
            DB-->>HR: Horario laboral
            HR-->>CS: Horario

            alt Fecha pasada o franja fuera del horario o en descanso
                CS-->>CC: ReglaNegocioException
                CC-->>SPA: 422 Unprocessable Entity
                SPA-->>C: La franja no es válida, elige otra
            else Franja dentro del horario
                CS->>CR: contarSolapamientos(profesional, inicio, fin)
                CR->>DB: SELECT COUNT citas activas que se cruzan
                DB-->>CR: Cantidad
                CR-->>CS: Cantidad

                alt Ya existe una cita en esa franja
                    CS-->>CC: ConflictoAgendaException
                    CC-->>SPA: 409 Conflict
                    SPA-->>C: Otra persona tomó la hora, recargando franjas
                else Franja libre
                    CS->>CR: save(Cita en estado PROGRAMADA + detalles)
                    CR->>DB: INSERT cita y detalle_cita

                    alt Restricción EXCLUDE violada (dos reservas simultáneas)
                        DB-->>CR: Error exclusion_violation
                        CR-->>CS: DataIntegrityViolationException
                        CS-->>CC: ConflictoAgendaException
                        CC-->>SPA: 409 Conflict
                        SPA-->>C: Otra persona tomó la hora, recargando franjas
                    else Inserción exitosa
                        DB-->>CR: Cita guardada
                        CR-->>CS: Cita
                        CS-->>CC: CitaDTO
                        CC-->>SPA: 201 Created
                        SPA-->>C: Cita confirmada
                        CS-)EV: Publica CitaCreadaEvent
                        opt Después del commit de la transacción
                            EV->>MSG: Enviar confirmación y programar recordatorios
                            MSG-->>EV: Estado del envío
                        end
                    end
                end
            end
        end
    end
```

### 3.3 Diagrama de Despliegue

```mermaid
flowchart TB
    subgraph DISP["Dispositivo del usuario"]
        nav["<b>Navegador web / PWA</b><br/>Chrome, Edge, Firefox o Safari"]
    end

    subgraph VPS["Nodo: Servidor VPS (Ubuntu 22.04 LTS)"]
        fw["<b>Firewall (ufw)</b><br/>Abiertos: 80, 443 y 22 (SSH)"]

        subgraph DOCKER["Docker Engine: red interna barberflow-net"]
            proxy["<b>Contenedor proxy</b><br/>Nginx + Certbot (SSL Let's Encrypt)<br/>Puertos 80 y 443"]
            front["<b>Contenedor frontend</b><br/>Nginx con el build de React<br/>Puerto interno 80"]
            back["<b>Contenedor backend</b><br/>Spring Boot 3 sobre Java 17<br/>Puerto interno 8080"]
            dbc[("<b>Contenedor postgres</b><br/>PostgreSQL 16<br/>Puerto interno 5432")]
        end

        vol[("Volumen Docker<br/>pgdata")]
        bk["<b>Tarea programada (cron)</b><br/>pg_dump diario, retención 30 días"]
    end

    subgraph EXT["Servicios externos"]
        store["Almacenamiento de respaldos<br/>(fuera del servidor)"]
        msg["Proveedor de mensajería"]
        pay["Pasarela de pagos"]
    end

    nav -->|"HTTPS 443 (TLS 1.2 o superior)"| fw
    fw --> proxy
    proxy -->|"HTTP: /"| front
    proxy -->|"HTTP: /api/*"| back
    back -->|"JDBC 5432"| dbc
    dbc --- vol
    bk -->|"Lee la base"| dbc
    bk -->|"Copia cifrada"| store
    back -->|"HTTPS / REST"| msg
    back -->|"HTTPS / REST"| pay

    classDef cont fill:#1168bd,stroke:#0b4884,color:#fff
    classDef datos fill:#2e7d32,stroke:#1b5e20,color:#fff
    classDef ext fill:#999,stroke:#6b6b6b,color:#fff
    class proxy,front,back cont
    class dbc,vol datos
    class store,msg,pay ext
```


---

## 4. Persistencia: Diccionario de Datos y mapeo JPA

| Entregable | Ubicación |
|---|---|
| Diccionario de Datos (22 tablas, 157 columnas, índices y anotaciones ORM) | [`diccionario-datos.md`](diccionario-datos.md) |
| Esquema SQL (Flyway) con la restricción anti-solapamiento de citas | [`backend/src/main/resources/db/migration/V1__esquema_inicial.sql`](../../backend/src/main/resources/db/migration/V1__esquema_inicial.sql) |
| Entidades `@Entity` (21) y enums | `backend/src/main/java/com/barberflow/domain/` |
| Interfaces `JpaRepository` (20) | `backend/src/main/java/com/barberflow/repository/` |

Buenas prácticas aplicadas: `FetchType.LAZY` en todas las relaciones, `@Enumerated(EnumType.STRING)`, índices declarados en `@Table(indexes = ...)`, dinero con `BigDecimal` / `NUMERIC(12,2)`, herencia `JOINED` para usuarios, composición `Cita` ◆ `DetalleCita` y `@EntityGraph` para evitar el problema N+1.
