package com.autopartco.backend.test;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.autopartco.backend.config.ApiConfig;

@RestController
@RequestMapping(ApiConfig.API_BASE_PATH + "/test")
public class SecurityTestController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "Public endpoint OK";
    }

    @GetMapping("/private")
    public PrivateEndpointResponse privateEndpoint(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        String role = jwt.getClaimAsStringList("roles").stream()
                .findFirst()
                .orElse(null);

        return new PrivateEndpointResponse(
                "Private endpoint OK",
                email,
                name,
                role
        );
    }
}