package com.example.geospatial_service.kafka.dto.socket;

public record DriverConfigMessage(String type, Payload payload) {
    public record Payload(long locationIntervalMs) {}

    // [고속 모드] 운행 중: 1초마다 전송
    public static DriverConfigMessage highFrequency() {
        return new DriverConfigMessage("CONFIG_UPDATE", new Payload(1000));
    }

    // [대기 모드] 운행 종료/대기: 10초마다 전송
    public static DriverConfigMessage lowFrequency() {
        return new DriverConfigMessage("CONFIG_UPDATE", new Payload(5000));
    }
}