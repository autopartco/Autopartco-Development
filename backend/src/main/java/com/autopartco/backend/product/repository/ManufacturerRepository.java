package com.autopartco.backend.product.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.product.entity.Manufacturer;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
	List<Manufacturer> findAllByOrderByNameAsc();

	@Query(value = "select exists (select 1 from manufacturers where lower(name) = lower(:name))", nativeQuery = true)
	boolean nameExists(@Param("name") String name);

	@Query(value = "select exists (select 1 from manufacturers where lower(name) = lower(:name) and id <> :id)",
			nativeQuery = true)
	boolean nameUsedByOther(@Param("name") String name, @Param("id") UUID id);
}