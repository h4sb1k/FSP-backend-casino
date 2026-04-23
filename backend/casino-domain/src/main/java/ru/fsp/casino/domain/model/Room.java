package ru.fsp.casino.domain.model;

import jakarta.persistence.*;
import lombok.*;
import ru.fsp.casino.domain.enums.RoomStatus;
import ru.fsp.casino.domain.enums.VipTier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomStatus status = RoomStatus.WAITING;

    @Enumerated(EnumType.STRING)
    private VipTier tier;

    private Integer maxSlots;
    private Long entryFee;
    private Integer prizePoolPct;
    private Boolean boostEnabled;
    private Long boostCost;

    @Column(precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal boostMultiplier = BigDecimal.valueOf(2.0);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creatorId")
    private User creator;

    private Instant timerStartedAt;
    private Instant roundStartedAt;
    private Instant finishedAt;

    @Column(name = "winnerParticipantId")
    private Long winnerParticipantId;

    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "room", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Builder.Default
    private List<RoomParticipant> participants = new ArrayList<>();
}
