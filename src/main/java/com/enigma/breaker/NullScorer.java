package com.enigma.breaker;

public final class NullScorer implements PlaintextScorer {

    @Override
    public double score(byte[] plaintext, int len) {
        return 0.0;
    }
}
