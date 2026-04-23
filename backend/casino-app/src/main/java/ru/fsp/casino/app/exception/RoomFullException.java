package ru.fsp.casino.app.exception;

public class RoomFullException extends RuntimeException {
    public RoomFullException(Long roomId) {
        super("Room is full or not joinable: " + roomId);
    }
}
