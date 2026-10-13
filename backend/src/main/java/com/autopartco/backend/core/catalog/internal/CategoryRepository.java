package com.autopartco.backend.core.catalog.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface CategoryRepository extends JpaRepository<Category, UUID> {
	List<Category> findAllByOrderByCodeAsc();

	@Query(value = "select exists (select 1 from categories where lower(code) = lower(:code))", nativeQuery = true)
	boolean codeExists(@Param("code") String code);

	@Query(value = "select exists (select 1 from categories where lower(code) = lower(:code) and id <> :id)",
			nativeQuery = true)
	boolean codeUsedByOther(@Param("code") String code, @Param("id") UUID id);
}