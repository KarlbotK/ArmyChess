package com.karlbot.armychess.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Component
public final class FileRoomStateStore implements RoomStateStore {
    private static final Logger log = LoggerFactory.getLogger(FileRoomStateStore.class);
    private final ObjectMapper json;
    private final Path directory;

    public FileRoomStateStore(ObjectMapper json, @Value("${armychess.data-dir:./data/rooms}") String dataDirectory) {
        this.json = json;
        this.directory = Path.of(dataDirectory).toAbsolutePath().normalize();
        ensureDirectory();
    }

    @Override
    public List<GameRoomState> loadAll() {
        ensureDirectory();
        List<GameRoomState> states = new ArrayList<>();
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            states.add(json.readValue(Files.readAllBytes(path), GameRoomState.class));
                        } catch (RuntimeException | IOException error) {
                            log.error("Skipping unreadable room state {}", path.getFileName(), error);
                        }
                    });
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load room states", error);
        }
        return List.copyOf(states);
    }

    @Override
    public synchronized void save(GameRoomState state) {
        ensureDirectory();
        Path target = roomFile(state.code());
        Path temporary = directory.resolve(state.code() + ".json.tmp");
        try {
            Files.write(temporary, json.writeValueAsBytes(state));
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to persist room " + state.code(), error);
        }
    }

    private Path roomFile(String roomCode) {
        if (roomCode == null || !roomCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("invalid room code");
        }
        return directory.resolve(roomCode + ".json");
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to create room state directory", error);
        }
    }
}
