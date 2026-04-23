package ru.fsp.casino.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.game.dto.WinnerResult;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WinnerService {

    public WinnerResult determineWinner(Room room, List<RoomParticipant> participants) {
        double boostMultiplier = room.getBoostMultiplier().doubleValue();
        long seed = ThreadLocalRandom.current().nextLong();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double totalWeight = participants.stream()
            .mapToDouble(p -> Boolean.TRUE.equals(p.getBoosted()) ? boostMultiplier : 1.0)
            .sum();

        double roll = rng.nextDouble(0, totalWeight);
        double cursor = 0.0;
        RoomParticipant winner = participants.get(participants.size() - 1);

        for (RoomParticipant p : participants) {
            cursor += Boolean.TRUE.equals(p.getBoosted()) ? boostMultiplier : 1.0;
            if (cursor >= roll) {
                winner = p;
                break;
            }
        }

        return new WinnerResult(winner, roll, totalWeight, seed);
    }

    public double calculateWinProbability(Room room, boolean hasBust) {
        double boostMultiplier = room.getBoostMultiplier().doubleValue();
        double userWeight = hasBust ? boostMultiplier : 1.0;
        int slots = room.getMaxSlots();
        double totalWeight = (slots - 1) * 1.0 + userWeight;
        return userWeight / totalWeight;
    }
}
