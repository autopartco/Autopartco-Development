package com.autopartco.backend.core.booking.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.core.booking.BookingStatus;

import jakarta.persistence.LockModeType;

public interface ServiceBookingRepository
        extends JpaRepository<ServiceBooking, UUID>, JpaSpecificationExecutor<ServiceBooking> {

    boolean existsByCode(String code);

    /** Bloquea la fila ({@code SELECT … FOR UPDATE}) para cambiar el estado sin carreras. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ServiceBooking b where b.id = :id")
    Optional<ServiceBooking> lockById(@Param("id") UUID id);

    @Query("select count(b) from ServiceBooking b where b.accountId = :accountId and b.storeId = :storeId "
            + "and b.status in :statuses")
    long countByAccountAndStore(@Param("accountId") UUID accountId, @Param("storeId") UUID storeId,
            @Param("statuses") Collection<BookingStatus> statuses);

    @Query("select count(b) from ServiceBooking b where b.accountId = :accountId and b.createdAt >= :since")
    long countCreatedSince(@Param("accountId") UUID accountId, @Param("since") Instant since);

    /** Citas confirmadas del servicio en la sede que se cruzan con {@code [from, to)}. */
    @Query("select b from ServiceBooking b where b.branchId = :branchId and b.serviceId = :serviceId "
            + "and b.status = com.autopartco.backend.core.booking.BookingStatus.CONFIRMED "
            + "and b.startsAt < :to and b.endsAt > :from")
    List<ServiceBooking> findConfirmedOverlapping(@Param("serviceId") UUID serviceId,
            @Param("branchId") UUID branchId, @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Pendientes de respuesta cuya hora ya llegó: {@code REQUESTED} por {@code starts_at} y {@code PROPOSED} por la hora
     * propuesta (si falta, por {@code starts_at}).
     */
    @Query("select b.id from ServiceBooking b where "
            + "(b.status = com.autopartco.backend.core.booking.BookingStatus.REQUESTED and b.startsAt <= :now) "
            + "or (b.status = com.autopartco.backend.core.booking.BookingStatus.PROPOSED "
            + "and coalesce(b.proposedStartsAt, b.startsAt) <= :now) order by b.startsAt")
    List<UUID> findDueToExpire(@Param("now") Instant now, Pageable page);

    /** Confirmadas que empiezan en {@code (from, to]}, para el recordatorio. */
    @Query("select b.id from ServiceBooking b where "
            + "b.status = com.autopartco.backend.core.booking.BookingStatus.CONFIRMED "
            + "and b.startsAt > :from and b.startsAt <= :to order by b.startsAt")
    List<UUID> findConfirmedStartingBetween(@Param("from") Instant from, @Param("to") Instant to, Pageable page);
}
