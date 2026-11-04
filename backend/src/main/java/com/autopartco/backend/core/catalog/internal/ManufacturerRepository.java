package com.autopartco.backend.core.catalog.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, UUID> {
	/** Todos (aprobados y pendientes): solo para administración. */
	List<Manufacturer> findAllByOrderByNameAsc();

	/** Solo aprobados: listados públicos. */
	List<Manufacturer> findAllByApprovedTrueOrderByNameAsc();

	/** Aprobados + pendientes sugeridos por el almacén. */
	@Query("select m from Manufacturer m where m.approved = true or m.suggestedByStoreId = :storeId order by m.name")
	List<Manufacturer> findVisibleToStore(@Param("storeId") UUID storeId);

	/** Un fabricante solo si es aprobado o pendiente sugerido por ese almacén. */
	@Query("select m from Manufacturer m where m.id = :id and (m.approved = true or m.suggestedByStoreId = :storeId)")
	Optional<Manufacturer> findVisibleToStore(@Param("id") UUID id, @Param("storeId") UUID storeId);

	/** Fabricantes pendientes (no aprobados, no borrados) sugeridos por el almacén. */
	@Query("select count(m) from Manufacturer m where m.suggestedByStoreId = :storeId and m.approved = false")
	long countPendingByStore(@Param("storeId") UUID storeId);

	/** Coincidencia exacta normalizada (sin acentos ni mayúsculas) con cualquier fabricante, visible o no. */
	@Query(value = """
			select m.* from manufacturers m
			where m.deleted_at is null
			  and public.immutable_unaccent(lower(m.name)) = public.immutable_unaccent(lower(cast(:name as text)))
			""", nativeQuery = true)
	List<Manufacturer> findByNormalizedName(@Param("name") String name);

	/**
	 * Fija, solo para la transacción en curso ({@code set_config(..., true)}), los umbrales de los operadores
	 * {@code %} y {@code <%} de pg_trgm. Debe ejecutarse en la misma transacción que {@link #search}. Devuelve un
	 * valor sin interés (concatenación de los dos valores fijados).
	 */
	@Query(value = """
			select set_config('pg_trgm.similarity_threshold', cast(:similarity as text), true)
			    || set_config('pg_trgm.word_similarity_threshold', cast(:word as text), true)
			""", nativeQuery = true)
	String applySearchThresholds(@Param("similarity") double similarity, @Param("word") double word);

	/**
	 * Búsqueda difusa visible al almacén en UNA consulta: prefijo, contiene, o trigramas. {@code %} (umbral
	 * {@code pg_trgm.similarity_threshold}) y {@code <%} ({@code word_similarity}: «Bosh» contra «Robert Bosch GmbH»,
	 * donde la similitud global es baja) usan el índice GIN {@code idx_manufacturers__name_trgm}. Orden: prefijo,
	 * contiene, mejor similitud. {@code escaped} trae escapados {@code \ % _}. Requiere {@link #applySearchThresholds}
	 * en la misma transacción.
	 */
	@Query(value = """
			with q as (
			  select public.immutable_unaccent(lower(cast(:q as text))) as t,
			         public.immutable_unaccent(lower(cast(:escaped as text))) as e
			)
			select m.id as id, m.name as name, m.approved as approved,
			       greatest(similarity(public.immutable_unaccent(lower(m.name)), q.t),
			                word_similarity(q.t, public.immutable_unaccent(lower(m.name)))) as similarity
			from manufacturers m, q
			where m.deleted_at is null
			  and (m.approved or m.suggested_by_store_id = :storeId)
			  and (public.immutable_unaccent(lower(m.name)) like q.e || '%'
			       or public.immutable_unaccent(lower(m.name)) like '%' || q.e || '%'
			       or public.immutable_unaccent(lower(m.name)) % q.t
			       or q.t <% public.immutable_unaccent(lower(m.name)))
			order by case
			           when public.immutable_unaccent(lower(m.name)) like q.e || '%' then 0
			           when public.immutable_unaccent(lower(m.name)) like '%' || q.e || '%' then 1
			           else 2 end,
			         similarity desc, m.name
			limit 8
			""", nativeQuery = true)
	List<MatchRow> search(@Param("storeId") UUID storeId, @Param("q") String q, @Param("escaped") String escaped);

	/** Similares visibles al almacén con similitud >= {@code min}. */
	@Query(value = """
			select m.id as id, m.name as name, m.approved as approved,
			       similarity(public.immutable_unaccent(lower(m.name)),
			                  public.immutable_unaccent(lower(cast(:q as text)))) as similarity
			from manufacturers m
			where m.deleted_at is null
			  and (m.approved or m.suggested_by_store_id = :storeId)
			  and public.immutable_unaccent(lower(m.name)) % public.immutable_unaccent(lower(cast(:q as text)))
			  and similarity(public.immutable_unaccent(lower(m.name)),
			                 public.immutable_unaccent(lower(cast(:q as text)))) >= :min
			order by similarity desc, m.name
			limit 5
			""", nativeQuery = true)
	List<MatchRow> findSimilar(@Param("storeId") UUID storeId, @Param("q") String q, @Param("min") double min);

	@Query(value = """
			select exists (select 1 from manufacturers where deleted_at is null
			  and public.immutable_unaccent(lower(name)) = public.immutable_unaccent(lower(cast(:name as text))))
			""", nativeQuery = true)
	boolean nameExists(@Param("name") String name);

	@Query(value = """
			select exists (select 1 from manufacturers where deleted_at is null and id <> :id
			  and public.immutable_unaccent(lower(name)) = public.immutable_unaccent(lower(cast(:name as text))))
			""", nativeQuery = true)
	boolean nameUsedByOther(@Param("name") String name, @Param("id") UUID id);

	interface MatchRow {
		UUID getId();

		String getName();

		boolean getApproved();

		double getSimilarity();
	}
}
