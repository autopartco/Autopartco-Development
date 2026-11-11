package com.autopartco.backend.core.search;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.shared.events.ListingsChanged;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Cuándo se reindexa {@code search_listings}. Todo el trabajo corre en un único hilo propio (no en el de la petición
 * ni en el {@code taskScheduler} compartido), así que las reindexaciones nunca se pisan:
 * <ul>
 *   <li>{@link ListingsChanged} tras el commit: se acumula y se procesa {@value #DEBOUNCE_MS} ms después, de modo que
 *   varios cambios del mismo producto en esa ventana se reindexan una sola vez. Si se acumulan más de
 *   {@value #MAX_PENDING_IDS} ids, se pasa a una reconstrucción completa.</li>
 *   <li>Cada noche a las 03:00 (America/Bogota): reconstrucción completa y recarga de sugerencias.</li>
 *   <li>Al arrancar, si el índice está vacío: reconstrucción completa en segundo plano
 *   ({@code autopartco.search.bootstrap-reindex=false} lo desactiva).</li>
 *   <li>A pedido del admin ({@link #requestFullReindex()}).</li>
 * </ul>
 * Un fallo se registra (sin datos personales) y no se reintenta: la reconstrucción nocturna lo corrige. Cada escritura
 * en el índice sube {@code catalogVersion} de {@code search:results} ({@link SearchResultCache}); la recarga de
 * sugerencias vacía {@code search:suggest}.
 */
@Slf4j
@Component
public class SearchIndexJobs {
    static final long DEBOUNCE_MS = 2_000;
    static final int MAX_PENDING_IDS = 5_000;

    private final SearchIndexer indexer;
    private final Suggestions suggestions;
    private final SearchService searchService;
    private final ScheduledExecutorService executor;
    private final boolean bootstrapEnabled;
    private final AtomicBoolean fullReindexQueued = new AtomicBoolean();

    private final Object lock = new Object();
    private Set<UUID> products = new HashSet<>();
    private Set<UUID> inventories = new HashSet<>();
    private Set<UUID> branches = new HashSet<>();
    private Set<UUID> stores = new HashSet<>();
    private boolean everything;
    private boolean flushScheduled;

    @Autowired
    public SearchIndexJobs(SearchIndexer indexer, Suggestions suggestions, SearchService searchService,
            @Value("${autopartco.search.bootstrap-reindex:true}") boolean bootstrapEnabled) {
        this(indexer, suggestions, searchService, bootstrapEnabled, Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "search-index");
            thread.setDaemon(true);
            return thread;
        }));
    }

    SearchIndexJobs(SearchIndexer indexer, Suggestions suggestions, SearchService searchService,
            boolean bootstrapEnabled, ScheduledExecutorService executor) {
        this.indexer = indexer;
        this.suggestions = suggestions;
        this.searchService = searchService;
        this.bootstrapEnabled = bootstrapEnabled;
        this.executor = executor;
    }

    /** Solo tras el commit (o de inmediato si se publicó fuera de una transacción). No bloquea al publicador. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingsChanged(ListingsChanged event) {
        if (event == null || event.isEmpty()) {
            return;
        }
        synchronized (lock) {
            everything |= event.everything();
            products.addAll(event.productIds());
            inventories.addAll(event.inventoryIds());
            branches.addAll(event.branchIds());
            stores.addAll(event.storeIds());
            if (products.size() + inventories.size() + branches.size() + stores.size() > MAX_PENDING_IDS) {
                everything = true;
            }
            if (everything) {
                products.clear();
                inventories.clear();
                branches.clear();
                stores.clear();
            }
            if (flushScheduled) {
                return;
            }
            flushScheduled = true;
        }
        try {
            executor.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException shuttingDown) {
            synchronized (lock) {
                flushScheduled = false;
            }
        }
    }

    /** Procesa lo acumulado. */
    void flush() {
        Set<UUID> productIds;
        Set<UUID> inventoryIds;
        Set<UUID> branchIds;
        Set<UUID> storeIds;
        boolean all;
        synchronized (lock) {
            productIds = products;
            inventoryIds = inventories;
            branchIds = branches;
            storeIds = stores;
            all = everything;
            products = new HashSet<>();
            inventories = new HashSet<>();
            branches = new HashSet<>();
            stores = new HashSet<>();
            everything = false;
            flushScheduled = false;
        }
        try {
            if (all) {
                fullReindex();
                return;
            }
            storeIds.forEach(indexer::reindexStore);
            branchIds.forEach(indexer::reindexBranch);
            indexer.reindexInventory(inventoryIds);
            indexer.reindexProducts(productIds);
        } catch (RuntimeException failure) {
            log.warn("Falló la actualización del índice de búsqueda: {}", failure.getClass().getSimpleName());
        } finally {
            // Aunque falle a medias, lo escrito invalida search:results (cambios agrupados por DEBOUNCE_MS).
            searchService.catalogChanged();
        }
    }

    /** Encola una reconstrucción completa (si ya hay una en cola, no añade otra). */
    public void requestFullReindex() {
        submitFull(false);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Bogota")
    public void nightly() {
        submitFull(true);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (!bootstrapEnabled) {
            return;
        }
        execute(() -> {
            try {
                if (!searchService.indexReady()) {
                    log.info("Índice de búsqueda vacío: reconstrucción inicial en segundo plano.");
                    fullReindex();
                    reloadSuggestions();
                }
            } catch (RuntimeException failure) {
                log.warn("Falló la reconstrucción inicial del índice de búsqueda: {}",
                        failure.getClass().getSimpleName());
            }
        });
    }

    private void submitFull(boolean withSuggestions) {
        if (!fullReindexQueued.compareAndSet(false, true)) {
            return;
        }
        execute(() -> {
            fullReindexQueued.set(false);
            fullReindex();
            if (withSuggestions) {
                reloadSuggestions();
            }
        });
    }

    private void fullReindex() {
        try {
            indexer.reindexAll();
            searchService.markReady();
        } catch (RuntimeException failure) {
            log.warn("Falló la reconstrucción del índice de búsqueda: {}", failure.getClass().getSimpleName());
        } finally {
            searchService.catalogChanged();
        }
    }

    private void reloadSuggestions() {
        try {
            suggestions.reload();
        } catch (RuntimeException failure) {
            log.warn("Falló la recarga de sugerencias de búsqueda: {}", failure.getClass().getSimpleName());
        }
    }

    private void execute(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException shuttingDown) {
            fullReindexQueued.set(false);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
