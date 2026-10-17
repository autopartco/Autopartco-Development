package com.autopartco.backend.core.vehicle.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Lecturas del catálogo maestro make/model/versions (sin entidades propias; solo registros vigentes). */
public interface VehicleCatalogRepository extends Repository<Compatibility, UUID> {

    @Query(value = "select id as id, name as name from make where deleted_at is null order by name, id",
            nativeQuery = true)
    List<VehicleOptionView> findMakes();

    @Query(value = """
            select id as id, name as name from model
            where make_id = :makeId and deleted_at is null
            order by name, id
            """, nativeQuery = true)
    List<VehicleOptionView> findModels(@Param("makeId") UUID makeId);

    @Query(value = """
            select id as id, version as name from versions
            where model_id = :modelId and deleted_at is null
            order by version, id
            """, nativeQuery = true)
    List<VehicleOptionView> findVersions(@Param("modelId") UUID modelId);

    @Query(value = "select exists(select 1 from make where id = :makeId and deleted_at is null)",
            nativeQuery = true)
    boolean makeExists(@Param("makeId") UUID makeId);

    @Query(value = "select exists(select 1 from model where id = :modelId and deleted_at is null)",
            nativeQuery = true)
    boolean modelExists(@Param("modelId") UUID modelId);

    /** Marcas vigentes entre {@code ids} (no llamar con colección vacía). */
    @Query(value = "select id as id, name as name from make where id in (:ids) and deleted_at is null",
            nativeQuery = true)
    List<VehicleOptionView> findMakesByIds(@Param("ids") Collection<UUID> ids);

    /** Modelos vigentes entre {@code ids} con su marca (no llamar con colección vacía). */
    @Query(value = """
            select id as id, make_id as "parentId", name as name from model
            where id in (:ids) and deleted_at is null
            """, nativeQuery = true)
    List<VehicleRefView> findModelsByIds(@Param("ids") Collection<UUID> ids);

    /** Versiones vigentes entre {@code ids} con su modelo (no llamar con colección vacía). */
    @Query(value = """
            select id as id, model_id as "parentId", version as name from versions
            where id in (:ids) and deleted_at is null
            """, nativeQuery = true)
    List<VehicleRefView> findVersionsByIds(@Param("ids") Collection<UUID> ids);
}
