package ru.fsp.casino.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "round_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoundHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long roomId;

    private Long winnerUserId;
    private Long winnerParticipantId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean winnerIsBot = false;

    @Column(nullable = false)
    private Long totalPool;

    @Column(nullable = false)
    private Long payout;

    private Long rngSeed;

    @Column(precision = 18, scale = 6)
    private BigDecimal rngRoll;

    @Column(precision = 18, scale = 6)
    private BigDecimal rngTotalWeight;

    private Integer participantCount;
    private Integer botCount;

    @Builder.Default
    private Instant finishedAt = Instant.now();
}
