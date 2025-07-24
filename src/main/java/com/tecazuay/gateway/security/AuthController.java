package com.tecazuay.gateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KeyService keyService;

    @Autowired
    public AuthController(KeyService keyService) {
        this.keyService = keyService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<String> getPublicKey() {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyService.getPublicKey().getEncoded());
        return ResponseEntity.ok(publicKeyBase64);
    }
}
