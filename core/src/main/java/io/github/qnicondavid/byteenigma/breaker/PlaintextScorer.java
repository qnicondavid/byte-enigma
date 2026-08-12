package io.github.qnicondavid.byteenigma.breaker;

@FunctionalInterface
public interface PlaintextScorer {

    double score(byte[] plaintext, int len);
}
