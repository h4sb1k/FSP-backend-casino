package ru.fsp.casino.domain.model;

import jakarta.persistence.*;
import lombok.*;
import ru.fsp.casino.domain.enums.UserRole;
import ru.fsp.casino.domain.enums.VipTier;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VipTier vipTier = VipTier.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    @Builder.Default
    private Long balance = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Long reservedBalance = 0L;

    @Builder.Default
    private Instant createdAt = Instant.now();
}
