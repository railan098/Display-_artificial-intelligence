package dev.agentmeter.model;

import java.time.Instant;

public record SystemSnapshot(double cpuPercent, long usedMemoryBytes, long totalMemoryBytes, Instant capturedAt) {}
