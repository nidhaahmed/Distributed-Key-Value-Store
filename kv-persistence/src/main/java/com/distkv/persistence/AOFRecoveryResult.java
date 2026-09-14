package com.distkv.persistence;

/**
 * Encapsulates metadata and metrics from an AOF recovery replay operation.
 */
public class AOFRecoveryResult {

    private final int totalLinesProcessed;
    private final int replayedOperations;
    private final int corruptedEntriesSkipped;
    private final boolean recoverySuccessful;

    public AOFRecoveryResult(int totalLinesProcessed, int replayedOperations, int corruptedEntriesSkipped, boolean recoverySuccessful) {
        this.totalLinesProcessed = totalLinesProcessed;
        this.replayedOperations = replayedOperations;
        this.corruptedEntriesSkipped = corruptedEntriesSkipped;
        this.recoverySuccessful = recoverySuccessful;
    }

    public int getTotalLinesProcessed() {
        return totalLinesProcessed;
    }

    public int getReplayedOperations() {
        return replayedOperations;
    }

    public int getCorruptedEntriesSkipped() {
        return corruptedEntriesSkipped;
    }

    public boolean isRecoverySuccessful() {
        return recoverySuccessful;
    }

    @Override
    public String toString() {
        return "AOFRecoveryResult{" +
                "linesProcessed=" + totalLinesProcessed +
                ", replayedOps=" + replayedOperations +
                ", corruptedSkipped=" + corruptedEntriesSkipped +
                ", success=" + recoverySuccessful +
                '}';
    }
}
