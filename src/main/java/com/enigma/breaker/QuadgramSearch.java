package com.enigma.breaker;

import com.enigma.EnigmaMachine;

public final class QuadgramSearch implements SeedSweep.SeedEvaluator {

    private final PlaintextScorer scorer;

    public QuadgramSearch(PlaintextScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public Candidate evaluate(int seed, EnigmaMachine machine, byte[] ciphertext, byte[] out) {
        int len = machine.transform(ciphertext, out);
        double score = scorer.score(out, len);
        return Candidate.of(seed, score, out, len);
    }
}
