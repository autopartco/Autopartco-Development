package com.autopartco.backend.storesuite.store;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/me")
@AllArgsConstructor
public class MeStoreController {
    private final ListMyStores listMyStores;

    @GetMapping("/stores")
    public List<MyStoreResponse> stores(Authentication authentication) {
        return listMyStores.execute(authentication.getName());
    }
}
