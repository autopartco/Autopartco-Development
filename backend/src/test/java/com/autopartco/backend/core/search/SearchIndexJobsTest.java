package com.autopartco.backend.core.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.autopartco.backend.shared.events.ListingsChanged;

/** Agrupación de eventos en la ventana corta y disparadores de la reconstrucción completa. */
class SearchIndexJobsTest {
    private final SearchIndexer indexer = mock(SearchIndexer.class);
    private final Suggestions suggestions = mock(Suggestions.class);
    private final SearchService searchService = mock(SearchService.class);
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final SearchIndexJobs jobs = new SearchIndexJobs(indexer, suggestions, searchService, true, executor);

    private Runnable scheduledFlush() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(task.capture(), eq(SearchIndexJobs.DEBOUNCE_MS), eq(TimeUnit.MILLISECONDS));
        return task.getValue();
    }

    private Runnable executed() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        return task.getValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void repeatedChangesInTheWindowAreReindexedOnce() {
        UUID product = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID store = UUID.randomUUID();
        UUID inventory = UUID.randomUUID();

        jobs.onListingsChanged(ListingsChanged.ofProduct(product));
        jobs.onListingsChanged(ListingsChanged.ofProducts(List.of(product, other)));
        jobs.onListingsChanged(ListingsChanged.ofStore(store));
        jobs.onListingsChanged(ListingsChanged.ofInventory(List.of(inventory)));
        scheduledFlush().run();

        ArgumentCaptor<Collection<UUID>> products = ArgumentCaptor.forClass(Collection.class);
        verify(indexer).reindexProducts(products.capture());
        assertThat(products.getValue()).containsExactlyInAnyOrder(product, other);
        verify(indexer).reindexStore(store);
        verify(indexer).reindexInventory(Set.of(inventory));
        verify(indexer, never()).reindexAll();

        // Tras procesar, un evento nuevo abre otra ventana.
        jobs.onListingsChanged(ListingsChanged.ofProduct(product));
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    void everythingOrTooManyIdsBecomesAFullReindex() {
        jobs.onListingsChanged(ListingsChanged.ofProduct(UUID.randomUUID()));
        jobs.onListingsChanged(ListingsChanged.ofEverything());
        scheduledFlush().run();

        verify(indexer).reindexAll();
        verify(searchService).markReady();
        verify(indexer, never()).reindexProducts(anyCollection());
    }

    @Test
    void failuresAreSwallowedAndEmptyEventsIgnored() {
        jobs.onListingsChanged(ListingsChanged.ofProducts(List.of()));
        verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        doThrow(new IllegalStateException("bd caída")).when(indexer).reindexProducts(anyCollection());
        jobs.onListingsChanged(ListingsChanged.ofProduct(UUID.randomUUID()));
        scheduledFlush().run();
    }

    @Test
    void adminRequestRunsAFullReindexInTheIndexThread() {
        jobs.requestFullReindex();
        jobs.requestFullReindex();
        executed().run();

        verify(indexer).reindexAll();
        verify(suggestions, never()).reload();
    }

    @Test
    void nightlyRebuildsIndexAndSuggestions() {
        jobs.nightly();
        executed().run();

        verify(indexer).reindexAll();
        verify(suggestions).reload();
    }

    @Test
    void bootstrapOnlyRebuildsWhenTheIndexIsEmpty() {
        when(searchService.indexReady()).thenReturn(true);
        jobs.bootstrap();
        executed().run();
        verify(indexer, never()).reindexAll();

        SearchIndexJobs disabled = new SearchIndexJobs(indexer, suggestions, searchService, false, executor);
        disabled.bootstrap();
        verify(executor, times(1)).execute(any(Runnable.class));
    }
}
