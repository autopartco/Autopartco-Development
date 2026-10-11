package com.autopartco.backend.product.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.autopartco.backend.product.entity.Compatibility;

public interface CompatibilityRepository extends JpaRepository<Compatibility, UUID> {

		@Query(value = """
						select c.id as id, mk.name as make, mo.name as model, v.version as version
						from compatibilities c
						join make mk on mk.id = c.make_id
						join model mo on mo.id = c.model_id
						join versions v on v.id = c.version_id
						where c.deleted_at is null
							and mk.deleted_at is null
							and mo.deleted_at is null
							and v.deleted_at is null
						order by mk.name, mo.name, v.version
						""", nativeQuery = true)
		List<CompatibilityOptionView> findOptions();
}