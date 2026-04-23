package ru.fsp.casino.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.fsp.casino.domain.model.Room;
import ru.fsp.casino.domain.model.RoomParticipant;
import ru.fsp.casino.domain.repository.RoomParticipantRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class BotService {

    private static final List<String> BOT_NAMES = List.of(
        "Борис", "Николай", "Елена", "Татьяна", "Дмитрий",
        "Светлана", "Алексей", "Ольга", "Игорь", "Марина",
        "Сергей", "Наталья", "Андрей", "Юлия", "Виктор"
    );

    private final RoomParticipantRepository participantRepository;

    public List<RoomParticipant> fillWithBots(Room room) {
        int currentCount = room.getParticipants().size();
        int botsNeeded = room.getMaxSlots() - currentCount;
        List<RoomParticipant> bots = new ArrayList<>();

        for (int i = 0; i < botsNeeded; i++) {
            String name = BOT_NAMES.get(ThreadLocalRandom.current().nextInt(BOT_NAMES.size()));
            int suffix = ThreadLocalRandom.current().nextInt(10, 100);
            RoomParticipant bot = RoomParticipant.builder()
                .room(room)
                .user(null)
                .isBot(true)
                .botName(name + "_" + suffix)
                .boosted(false)
                .build();
            bots.add(participantRepository.save(bot));
        }

        return bots;
    }
}
