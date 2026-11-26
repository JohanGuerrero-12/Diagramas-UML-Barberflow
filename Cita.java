package com.barberflow.domain;

import com.barberflow.domain.enums.EstadoCita;
import com.barberflow.exception.ReglaNegocioException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregado central de la agenda. Sin setters públicos: el estado solo cambia mediante
 * métodos de dominio que validan las transiciones permitidas.
 * La restricción anti-solapamiento vive en la base de datos (ver V1__esquema_inicial.sql).
 */
@Entity
@Table(name = "cita", indexes = {
        @Index(name = "idx_cita_prof_inicio", columnList = "id_profesional, fecha_hora_inicio"),
        @Index(name = "idx_cita_cliente", columnList = "id_cliente, fecha_hora_inicio DESC"),
        @Index(name = "idx_cita_est_estado_fecha", columnList = "id_establecimiento, estado, fecha_hora_inicio")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cita")
    private Long idCita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_profesional")
    private Profesional profesional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_establecimiento")
    private Establecimiento establecimiento;

    @Column(name = "fecha_hora_inicio", nullable = false)
    private LocalDateTime fechaHoraInicio;

    @Column(name = "fecha_hora_fin", nullable = false)
    private LocalDateTime fechaHoraFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoCita estado = EstadoCita.PROGRAMADA;

    @Column(name = "valor_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorEstimado = BigDecimal.ZERO;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "motivo_cancelacion", length = 200)
    private String motivoCancelacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** Composición: sin cita no existe el detalle. */
    @OneToMany(mappedBy = "cita", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleCita> detalles = new ArrayList<>();

    public Cita(Cliente cliente, Profesional profesional, Establecimiento establecimiento, LocalDateTime inicio) {
        this.cliente = cliente;
        this.profesional = profesional;
        this.establecimiento = establecimiento;
        this.fechaHoraInicio = inicio;
        this.fechaHoraFin = inicio;
    }

    @PrePersist
    void alCrear() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
    }

    // ----- comportamiento de dominio -----

    /** Agrega un servicio con su precio y duración actuales y recalcula el fin de la cita (RN-02). */
    public void agregarDetalle(Servicio servicio) {
        detalles.add(new DetalleCita(this, servicio));
        recalcularTotales();
    }

    public int calcularDuracion() {
        return detalles.stream().mapToInt(DetalleCita::getDuracionMin).sum();
    }

    private void recalcularTotales() {
        this.valorEstimado = detalles.stream().map(DetalleCita::calcularSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.fechaHoraFin = fechaHoraInicio.plusMinutes(calcularDuracion());
    }

    public void cambiarEstado(EstadoCita nuevo) {
        if (!estado.puedeTransitarA(nuevo))
            throw new ReglaNegocioException("Transición de estado no permitida: " + estado + " → " + nuevo);
        this.estado = nuevo;
    }

    public void cancelar(String motivo) {
        cambiarEstado(EstadoCita.CANCELADA);
        this.motivoCancelacion = motivo;
    }

    public void iniciarAtencion() {
        cambiarEstado(EstadoCita.EN_ATENCION);
    }

    public void finalizar() {
        cambiarEstado(EstadoCita.FINALIZADA);
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
