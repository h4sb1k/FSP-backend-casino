package ru.fsp.casino.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "room_participants",
       uniqueConstraints = @UniqueConstraint(columnNames = {"roomId", "userId"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "roomId", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId")
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isBot = false;

    private String botName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean boosted = false;

    @Builder.Default
    private Instant joinedAt = Instant.now();
}
