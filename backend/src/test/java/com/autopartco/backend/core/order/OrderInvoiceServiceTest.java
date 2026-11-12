package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.storage.SupabaseStorageProperties;
import com.autopartco.backend.shared.storage.SupabaseStorageService;

/** Factura de venta: solo PDF real, alcance por almacén y por comprador, reemplazo y limpieza del archivo. */
class OrderInvoiceServiceTest {
    private static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n1 0 obj".getBytes(StandardCharsets.ISO_8859_1);

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SupabaseStorageService storage = mock(SupabaseStorageService.class);
    private final SupabaseStorageProperties properties = mock(SupabaseStorageProperties.class);
    private final OrderInvoiceService service = new OrderInvoiceService(jdbc, storage, properties);
    private final UUID storeId = UUID.randomUUID();
    private final UUID saleId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();

    private void saleBelongsToStore(boolean value) {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(saleId), eq(storeId))).thenReturn(value);
        when(properties.businessBucket()).thenReturn("bussniess");
    }

    @Test
    @SuppressWarnings("unchecked")
    void uploadStoresThePdfReplacesThePreviousOneAndDeletesItsFile() {
        saleBelongsToStore(true);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(saleId))).thenReturn(List.of("sales/old.pdf"));
        when(jdbc.query(anyString(), any(RowMapper.class), eq(saleId)))
                .thenReturn(List.of(new SaleInvoice("FE-1001.pdf", PDF.length, LocalDateTime.now())));

        SaleInvoice invoice = service.upload(storeId, saleId, UUID.randomUUID(),
                new MockMultipartFile("file", "C:\\docs\\FE-1001.pdf", "application/pdf", PDF));

        assertThat(invoice.fileName()).isEqualTo("FE-1001.pdf");
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(storage).uploadToBucket(eq("bussniess"), path.capture(), eq(PDF), eq("application/pdf"), eq(false));
        assertThat(path.getValue()).startsWith("sales/" + saleId + "/invoice/").endsWith(".pdf");
        verify(storage).deleteFromBucket("bussniess", "sales/old.pdf");
    }

    @Test
    void uploadToASaleOfAnotherStoreIsNotFoundAndStoresNothing() {
        saleBelongsToStore(false);

        assertThatThrownBy(() -> service.upload(storeId, saleId, UUID.randomUUID(),
                new MockMultipartFile("file", "f.pdf", "application/pdf", PDF)))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("SELLER_ORDER_NOT_FOUND"));
        verify(storage, never()).uploadToBucket(any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void onlyRealPdfsUpToFiveMegabytesAreAccepted() {
        assertThatThrownBy(() -> OrderInvoiceService.read(new MockMultipartFile("file", "f.pdf", "application/pdf",
                "<html>no</html>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("INVOICE_NOT_PDF"));
        assertThatThrownBy(() -> OrderInvoiceService.read(new MockMultipartFile("file", "f.pdf", "application/pdf",
                new byte[(int) OrderInvoiceService.MAX_SIZE + 1])))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("FILE_TOO_LARGE"));
        assertThatThrownBy(() -> OrderInvoiceService.read(new MockMultipartFile("file", new byte[0])))
                .isInstanceOfSatisfying(BadRequestException.class, error -> assertThat(error.getCode()).isEqualTo("FILE_REQUIRED"));
        assertThat(OrderInvoiceService.read(new MockMultipartFile("file", "x.bin", "image/png", PDF))).isEqualTo(PDF);
    }

    @Test
    void fileNamesLoseFoldersAndDangerousCharacters() {
        assertThat(OrderInvoiceService.cleanFileName("../../etc/pass\"wd")).isEqualTo("passwd.pdf");
        assertThat(OrderInvoiceService.cleanFileName("C:\\Users\\a\\Factura 12.PDF")).isEqualTo("Factura 12.PDF");
        assertThat(OrderInvoiceService.cleanFileName(null)).isEqualTo("factura.pdf");
        assertThat(OrderInvoiceService.cleanFileName("a\r\nb.pdf")).isEqualTo("ab.pdf");
    }

    @Test
    void buyerCannotDownloadTheInvoiceOfSomeoneElsesOrder() {
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(saleId), eq(orderId), eq(personId))).thenReturn(false);

        assertThatThrownBy(() -> service.downloadForBuyer(personId, orderId, saleId))
                .isInstanceOfSatisfying(NotFoundException.class, error -> assertThat(error.getCode()).isEqualTo("INVOICE_NOT_FOUND"));
        verify(storage, never()).downloadFromBucket(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void buyerDownloadsTheInvoiceOfTheirOwnSale() {
        when(properties.businessBucket()).thenReturn("bussniess");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(saleId), eq(orderId), eq(personId))).thenReturn(true);
        when(jdbc.query(anyString(), any(RowMapper.class), eq(saleId)))
                .thenReturn(java.util.Collections.singletonList(new String[] {"sales/x.pdf", "FE-1.pdf"}));
        when(storage.downloadFromBucket("bussniess", "sales/x.pdf"))
                .thenReturn(new SupabaseStorageService.StoredObject(PDF, "application/pdf"));

        OrderInvoiceService.InvoiceFile file = service.downloadForBuyer(personId, orderId, saleId);

        assertThat(file.fileName()).isEqualTo("FE-1.pdf");
        assertThat(file.content()).isEqualTo(PDF);
    }

    @Test
    void deletingWhenThereIsNoInvoiceIsNotFound() {
        saleBelongsToStore(true);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(saleId))).thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(storeId, saleId)).isInstanceOf(NotFoundException.class);
    }
}
