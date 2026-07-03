package com.enigma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordSeedTest {

    private static final String MESSAGE = "The quick brown fox jumps over the lazy dog.";

    private static final String[][] HASHCODE_COLLISION_PAIRS = {
        {"Aa", "BB"},
        {"Ba", "CB"},
        {"Ca", "DB"},
        {"Ab", "BC"}
    };

    @Test
    void javaHashCodeCollisionPairsMapToDistinctSeeds() {
        for (String[] pair : HASHCODE_COLLISION_PAIRS) {
            String a = pair[0];
            String b = pair[1];
            assertEquals(a.hashCode(), b.hashCode(),
                    "expected equal Java hashCode for " + a + " and " + b);
            assertNotEquals(EnigmaMachine.seedFromPassword(a), EnigmaMachine.seedFromPassword(b),
                    "seedFromPassword collided for hashCode pair " + a + "/" + b);
        }
    }

    @Test
    void javaHashCodeCollisionPairsProduceDistinctCiphertext() {
        for (String[] pair : HASHCODE_COLLISION_PAIRS) {
            String a = pair[0];
            String b = pair[1];
            String cipherA = EnigmaMachine.fromPassword(a, 3).encrypt(MESSAGE);
            String cipherB = EnigmaMachine.fromPassword(b, 3).encrypt(MESSAGE);
            assertNotEquals(cipherA, cipherB,
                    "ciphertext collided for hashCode pair " + a + "/" + b);
        }
    }

    @Test
    void allTwoLetterPasswordsMapToDistinctSeeds() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Set<Integer> seeds = new HashSet<>();
        int total = 0;
        for (int i = 0; i < alphabet.length(); i++) {
            for (int j = 0; j < alphabet.length(); j++) {
                String password = "" + alphabet.charAt(i) + alphabet.charAt(j);
                seeds.add(EnigmaMachine.seedFromPassword(password));
                total++;
            }
        }
        assertEquals(2704, total);
        assertEquals(total, seeds.size(),
                "seedFromPassword produced collisions over [A-Za-z] two-letter passwords");
    }
}
