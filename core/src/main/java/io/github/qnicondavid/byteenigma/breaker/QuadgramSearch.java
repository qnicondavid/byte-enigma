package io.github.qnicondavid.byteenigma.breaker;

import io.github.qnicondavid.byteenigma.cipher.ByteEnigma;

public final class QuadgramSearch implements SeedSweep.SeedEvaluator {

    private final PlaintextScorer scorer;

    public QuadgramSearch(PlaintextScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public Candidate evaluate(int seed, ByteEnigma machine, byte[] ciphertext, byte[] out) {
        int len = machine.transform(ciphertext, out);
        double score = scorer.score(out, len);
        return Candidate.of(seed, score, out, len);
    }
}
