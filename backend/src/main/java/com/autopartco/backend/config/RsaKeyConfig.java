package com.autopartco.backend.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@EnableConfigurationProperties(RsaKeyProperties.class)
public class RsaKeyConfig {

    @Bean
    public RSAPublicKey rsaPublicKey(RsaKeyProperties props) throws Exception {
        byte[] content = readPem(props.publicKey());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(content);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey(RsaKeyProperties props) throws Exception {
        byte[] content = readPem(props.privateKey());
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(content);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private byte[] readPem(Resource resource) throws IOException {
        try (PemReader pemReader = new PemReader(new InputStreamReader(resource.getInputStream()))) {
            PemObject pemObject = pemReader.readPemObject();
            return pemObject.getContent();
        }
    }
}
