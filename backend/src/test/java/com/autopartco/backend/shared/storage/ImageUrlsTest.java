package com.autopartco.backend.shared.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class ImageUrlsTest {
    private static final String PATH = "products/p1/a b.jpg";
    private static final String PUBLIC = "https://x.supabase.co/storage/v1/object/public/products-images/products/p1/a%20b.jpg";

    private ImageUrls urls(String provider, String cloud, String openinary) {
        SupabaseStorageService storage = mock(SupabaseStorageService.class);
        when(storage.publicUrl(PATH)).thenReturn(PUBLIC);
        return new ImageUrls(storage, provider, cloud, openinary);
    }

    @Test
    void noneDevuelveLaUrlPublica() {
        assertEquals(PUBLIC, urls("none", "", "").variant(PATH, ImageVariant.THUMB));
        assertEquals(PUBLIC, urls("none", "", "").original(PATH));
    }

    @Test
    void wsrvCodificaLaUrlYAplicaTamano() {
        String url = urls("wsrv", "", "").variant(PATH, ImageVariant.CARD);
        assertEquals("https://wsrv.nl/?url=https%3A%2F%2Fx.supabase.co%2Fstorage%2Fv1%2Fobject%2Fpublic%2F"
                + "products-images%2Fproducts%2Fp1%2Fa%2520b.jpg&w=800&h=800&fit=cover&output=webp&q=80", url);
    }

    @Test
    void cloudinaryUsaModoFetch() {
        assertEquals("https://res.cloudinary.com/demo/image/fetch/w_400,h_400,c_fill,f_auto,q_auto/" + PUBLIC,
                urls("cloudinary", "demo", "").variant(PATH, ImageVariant.THUMB));
    }

    @Test
    void cloudinarySinCloudNameDegradaAlOriginal() {
        assertEquals(PUBLIC, urls("cloudinary", "", "").variant(PATH, ImageVariant.THUMB));
    }

    @Test
    void openinaryConstruyeRutaDeTransformacion() {
        assertEquals("http://localhost:3000/t/w_800,h_800,c_fill,f_webp/" + PATH,
                urls("openinary", "", "http://localhost:3000/").variant(PATH, ImageVariant.CARD));
    }

    @Test
    void openinarySinBaseUrlYProveedorDesconocidoDegradan() {
        assertEquals(PUBLIC, urls("openinary", "", "").variant(PATH, ImageVariant.THUMB));
        assertEquals(PUBLIC, urls("otro", "", "").variant(PATH, ImageVariant.THUMB));
        assertEquals(PUBLIC, urls(null, null, null).variant(PATH, ImageVariant.THUMB));
    }
}
