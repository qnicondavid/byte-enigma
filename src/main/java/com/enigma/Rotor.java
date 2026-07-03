package com.enigma;

import java.util.Random;

public final class Rotor {

    private final byte[] forwardMap = new byte[256];
    private final byte[] reverseMap = new byte[256];
    private int turnoverPoint;
    private int initialPosition;
    private int position;

    public Rotor(int seed, int initialPosition) {
        reseed(seed, initialPosition);
    }

    public void reseed(int seed, int initialPosition) {
        Random rand = new Random(seed);
        this.turnoverPoint = Math.floorMod(seed, 256);
        this.initialPosition = Math.floorMod(initialPosition, 256);
        this.position = this.initialPosition;

        for (int i = 0; i < 256; i++) {
            forwardMap[i] = (byte) i;
        }
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            byte temp = forwardMap[i];
            forwardMap[i] = forwardMap[j];
            forwardMap[j] = temp;
        }
        for (int i = 0; i < 256; i++) {
            reverseMap[forwardMap[i] & 0xFF] = (byte) i;
        }
    }

    public void reset() {
        position = initialPosition;
    }

    public int encrypt(int c) {
        return forwardMap[(c + position) & 0xFF] & 0xFF;
    }

    public int decrypt(int c) {
        return ((reverseMap[c] & 0xFF) - position) & 0xFF;
    }

    public void rotate() {
        position = (position + 1) & 0xFF;
    }

    public boolean atTurnover() {
        return position == turnoverPoint;
    }

    public int position() {
        return position;
    }

    public int turnoverPoint() {
        return turnoverPoint;
    }
}
