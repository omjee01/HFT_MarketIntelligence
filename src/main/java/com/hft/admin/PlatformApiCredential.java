package com.hft.admin;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "platform_api_credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformApiCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private PlatformCredentialProvider provider;

    @Column(length = 500)
    private String encryptedApiKey;

    @Column(length = 500)
    private String encryptedClientId;

    @Column(length = 500)
    private String encryptedClientSecret;

    private LocalDateTime updatedAt;

    private String updatedBy;
}
