package com.enigma.breaker;

@FunctionalInterface
public interface PlaintextScorer {

    double score(byte[] plaintext, int len);
}
