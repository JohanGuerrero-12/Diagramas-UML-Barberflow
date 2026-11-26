package com.barberflow.repository;

import com.barberflow.domain.Cita;
import com.barberflow.domain.enums.EstadoCita;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    /** Cuenta citas activas del profesional que se cruzan con [inicio, fin) (RF-07). */
    @Query("""
            select count(c) from Cita c
            where c.profesional.idUsuario = :idProfesional
              and c.estado in :estados
              and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio
            """)
    long contarSolapamientos(@Param("idProfesional") Long idProfesional,
                             @Param("inicio") LocalDateTime inicio,
                             @Param("fin") LocalDateTime fin,
                             @Param("estados") List<EstadoCita> estados);

    default long contarSolapamientos(Long idProfesional, LocalDateTime inicio, LocalDateTime fin) {
        return contarSolapamientos(idProfesional, inicio, fin, EstadoCita.ACTIVAS);
    }

    /** Citas activas de un profesional en un día: base para calcular las franjas libres. */
    @Query("""
            select c from Cita c
            where c.profesional.idUsuario = :idProfesional
              and c.estado in :estados
              and c.fechaHoraInicio >= :desde and c.fechaHoraInicio < :hasta
            order by c.fechaHoraInicio
            """)
    List<Cita> buscarActivasEnRango(@Param("idProfesional") Long idProfesional,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta,
                                    @Param("estados") List<EstadoCita> estados);

    /** Historial del cliente, de la más reciente a la más antigua. */
    List<Cita> findByCliente_IdUsuarioOrderByFechaHoraInicioDesc(Long idCliente);

    /** Carga la cita con sus servicios y profesional en una sola consulta (evita N+1). */
    @EntityGraph(attributePaths = {"detalles", "detalles.servicio", "profesional", "cliente"})
    Optional<Cita> findConDetallesByIdCita(Long idCita);
}
