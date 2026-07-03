package com.enigma;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class EnigmaMachine {

    public static final int MAX_ROTOR_COUNT = 1024;

    private final List<Rotor> rotors;
    private final Involution reflector;
    private final Involution plugboard;

    public EnigmaMachine(int seed, int rotorCount) {
        if (rotorCount < 1 || rotorCount > MAX_ROTOR_COUNT) {
            throw new IllegalArgumentException("rotorCount must be between 1 and " + MAX_ROTOR_COUNT);
        }
        List<Rotor> built = new ArrayList<>();
        Random rand = new Random(mix(seed, 0));
        for (int i = 0; i < rotorCount; i++) {
            int initialPosition = rand.nextInt(256);
            built.add(new Rotor(mix(seed, i + 1), initialPosition));
        }
        this.rotors = built;
        this.reflector = new Involution(mix(seed, -1));
        this.plugboard = new Involution(mix(seed, -2));
    }

    public void rekey(int seed) {
        Random rand = new Random(mix(seed, 0));
        for (int i = 0; i < rotors.size(); i++) {
            int initialPosition = rand.nextInt(256);
            rotors.get(i).reseed(mix(seed, i + 1), initialPosition);
        }
        reflector.reseed(mix(seed, -1));
        plugboard.reseed(mix(seed, -2));
    }

    static int mix(int base, int tag) {
        int h = base ^ (tag * 0x9E3779B9);
        h ^= (h >>> 16); h *= 0x85EBCA6B;
        h ^= (h >>> 13); h *= 0xC2B2AE35;
        h ^= (h >>> 16);
        return h;
    }

    static int seedFromPassword(String password) {
        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);
        int h = 0x811C9DC5;
        for (byte b : bytes) {
            h ^= (b & 0xFF);
            h *= 0x01000193;
        }
        return mix(h, 0x50617373);
    }

    public static EnigmaMachine fromPassword(String password, int rotorCount) {
        return new EnigmaMachine(seedFromPassword(password), rotorCount);
    }

    public String encrypt(String text) {
        byte[] out = transform(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(out);
    }

    public String decrypt(String base64) {
        byte[] out = transform(Base64.getDecoder().decode(base64));
        return new String(out, StandardCharsets.UTF_8);
    }

    public byte[] transform(byte[] input) {
        byte[] output = new byte[input.length];
        transform(input, output);
        return output;
    }

    public int transform(byte[] input, byte[] output) {
        if (output.length < input.length) {
            throw new IllegalArgumentException("output buffer too small");
        }
        for (Rotor rotor : rotors) {
            rotor.reset();
        }
        for (int i = 0; i < input.length; i++) {
            step();

            int c = input[i] & 0xFF;
            c = plugboard.apply(c);
            for (Rotor rotor : rotors) {
                c = rotor.encrypt(c);
            }
            c = reflector.apply(c);
            for (int j = rotors.size() - 1; j >= 0; j--) {
                c = rotors.get(j).decrypt(c);
            }
            c = plugboard.apply(c);
            output[i] = (byte) c;
        }
        return input.length;
    }

    private void step() {
        for (Rotor rotor : rotors) {
            rotor.rotate();
            if (!rotor.atTurnover()) {
                break;
            }
        }
    }

    public List<Rotor> rotors() {
        return Collections.unmodifiableList(rotors);
    }
}
