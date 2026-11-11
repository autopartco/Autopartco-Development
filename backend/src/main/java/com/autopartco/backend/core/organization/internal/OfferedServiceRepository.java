package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferedServiceRepository extends JpaRepository<OfferedService, UUID> {

    @Query("select s from OfferedService s where s.storeId = :storeId and s.active = true "
            + "order by s.sortOrder, lower(s.name)")
    List<OfferedService> findActiveInStore(@Param("storeId") UUID storeId);

    @Query("select s from OfferedService s where s.storeId = :storeId "
            + "order by s.active desc, s.sortOrder, lower(s.name)")
    List<OfferedService> findAllInStore(@Param("storeId") UUID storeId);

    @Query("select s from OfferedService s where s.id = :serviceId and s.storeId = :storeId")
    Optional<OfferedService> findInStore(@Param("serviceId") UUID serviceId, @Param("storeId") UUID storeId);

    @Query("select count(s) from OfferedService s where s.storeId = :storeId and s.active = true")
    long countActiveInStore(@Param("storeId") UUID storeId);

    /** Pares (servicio, sucursal) de los servicios activos del almacén. */
    @Query("select s.id, b from OfferedService s join s.branchIds b where s.storeId = :storeId and s.active = true")
    List<Object[]> findActiveBranchLinks(@Param("storeId") UUID storeId);
}
