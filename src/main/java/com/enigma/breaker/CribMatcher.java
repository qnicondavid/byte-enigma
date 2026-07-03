package com.enigma.breaker;

import com.enigma.EnigmaMachine;
import java.util.ArrayList;
import java.util.List;

public final class CribMatcher implements SeedSweep.SeedEvaluator {

    private final byte[] crib;
    private final int offset;

    public CribMatcher(byte[] crib, int offset) {
        if (crib.length == 0) {
            throw new IllegalArgumentException("crib must be non-empty");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        this.crib = crib.clone();
        this.offset = offset;
    }

    @Override
    public Candidate evaluate(int seed, EnigmaMachine machine, byte[] ciphertext, byte[] out) {
        if (offset + crib.length > ciphertext.length) {
            return null;
        }
        int len = machine.transform(ciphertext, out);
        for (int j = 0; j < crib.length; j++) {
            if (out[offset + j] != crib[j]) {
                return null;
            }
        }
        return Candidate.of(seed, crib.length, out, len);
    }

    public static boolean offsetAdmissible(byte[] ciphertext, byte[] crib, int offset) {
        if (offset < 0 || crib.length == 0 || offset + crib.length > ciphertext.length) {
            return false;
        }
        for (int j = 0; j < crib.length; j++) {
            if (crib[j] == ciphertext[offset + j]) {
                return false;
            }
        }
        return true;
    }

    public static List<Integer> admissibleOffsets(byte[] ciphertext, byte[] crib) {
        List<Integer> offsets = new ArrayList<>();
        for (int offset = 0; offset + crib.length <= ciphertext.length; offset++) {
            if (offsetAdmissible(ciphertext, crib, offset)) {
                offsets.add(offset);
            }
        }
        return offsets;
    }
}
