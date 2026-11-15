package com.autopartco.backend.shared.location;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    /**
     * Código DIVIPOLA del municipio por nombre de ciudad y departamento, sin tildes, mayúsculas ni signos. Si el
     * nombre existe en varios departamentos exige que el departamento coincida; si no hay coincidencia, {@code null}.
     * Los filtros por ciudad y departamento de la búsqueda dependen de este código.
     */
    @Query(value = """
            with c as (
                select m.code,
                       regexp_replace(public.immutable_unaccent(lower(d.name)), '[^a-z0-9]', '', 'g')
                           = regexp_replace(public.immutable_unaccent(lower(cast(:department as text))), '[^a-z0-9]', '', 'g')
                           as dep_ok
                from geo_municipalities m
                join geo_departments d on d.code = m.department_code
                where regexp_replace(public.immutable_unaccent(lower(m.name)), '[^a-z0-9]', '', 'g')
                    = regexp_replace(public.immutable_unaccent(lower(cast(:city as text))), '[^a-z0-9]', '', 'g')
            )
            select code from c
            where dep_ok or (select count(*) from c) = 1
            order by dep_ok desc
            limit 1
            """, nativeQuery = true)
    String resolveMunicipalityCode(@Param("city") String city, @Param("department") String department);
}
