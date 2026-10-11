package com.autopartco.backend;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

public class GeneratedKeyPair {

    private static final int KEY_SIZE = 2048;
    private static final Path CERTS_DIRECTORY = Path.of("src", "main", "resources", "certs");
    private static final String PUBLIC_KEY_FILE = "pub.pem";
    private static final String PRIVATE_KEY_FILE = "pri.pem";

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(KEY_SIZE);
        KeyPair keyPair = keyPairGenerator.genKeyPair();

        byte[] pub = keyPair.getPublic().getEncoded();
        byte[] pri = keyPair.getPrivate().getEncoded();

        Files.createDirectories(CERTS_DIRECTORY);

        writePem(CERTS_DIRECTORY.resolve(PUBLIC_KEY_FILE), "PUBLIC KEY", pub);
        writePem(CERTS_DIRECTORY.resolve(PRIVATE_KEY_FILE), "PRIVATE KEY", pri);

        System.out.println("Claves generadas en: " + CERTS_DIRECTORY.toAbsolutePath());
    }

    private static void writePem(Path filePath, String type, byte[] content) throws IOException {
        try (PemWriter pemWriter = new PemWriter(
                new OutputStreamWriter(new FileOutputStream(filePath.toFile()), StandardCharsets.US_ASCII))) {
            pemWriter.writeObject(new PemObject(type, content));
        }
    }
}