package io.github.qnicondavid.byteenigma.cipher;

import java.util.Random;

public final class Involution {

    private final byte[] map = new byte[256];
    private final byte[] pool = new byte[256];

    public Involution(int seed) {
        reseed(seed);
    }

    public void reseed(int seed) {
        for (int i = 0; i < 256; i++) {
            pool[i] = (byte) i;
        }
        Random rand = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            byte temp = pool[i];
            pool[i] = pool[j];
            pool[j] = temp;
        }
        for (int i = 0; i < 256; i += 2) {
            int a = pool[i] & 0xFF;
            int b = pool[i + 1] & 0xFF;
            map[a] = (byte) b;
            map[b] = (byte) a;
        }
    }

    public int apply(int c) {
        return map[c] & 0xFF;
    }
}
