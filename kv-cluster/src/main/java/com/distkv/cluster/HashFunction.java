package com.distkv.cluster;

import java.nio.charset.StandardCharsets;

/**
 * Hash function interface for partitioning keys and virtual nodes across the consistent hash ring.
 */
public interface HashFunction {

    /**
     * Hashes a string key into a 64-bit integer token on the hash ring.
     *
     * @param key input key
     * @return 64-bit non-negative hash token
     */
    long hash(String key);

    /**
     * High-performance 64-bit MurmurHash3 implementation.
     * Provides uniform avalanche distribution critical for consistent hashing.
     */
    class Murmur3 implements HashFunction {

        private static final long C1 = 0x87c37b91114253d5L;
        private static final long C2 = 0x4cf5ad432745937fL;

        @Override
        public long hash(String key) {
            if (key == null) return 0L;
            byte[] data = key.getBytes(StandardCharsets.UTF_8);
            return hash64(data, 0, data.length, 0);
        }

        public static long hash64(byte[] data, int offset, int length, int seed) {
            long h1 = seed & 0x00000000FFFFFFFFL;
            int nblocks = length >> 3;

            for (int i = 0; i < nblocks; i++) {
                int index = offset + (i << 3);
                long k1 = getLittleEndianLong(data, index);

                k1 *= C1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= C2;

                h1 ^= k1;
                h1 = Long.rotateLeft(h1, 27);
                h1 = h1 * 5 + 0x52dce729;
            }

            int tailOffset = offset + (nblocks << 3);
            long k1 = 0;
            switch (length & 7) {
                case 7: k1 ^= ((long) data[tailOffset + 6] & 0xff) << 48;
                case 6: k1 ^= ((long) data[tailOffset + 5] & 0xff) << 40;
                case 5: k1 ^= ((long) data[tailOffset + 4] & 0xff) << 32;
                case 4: k1 ^= ((long) data[tailOffset + 3] & 0xff) << 24;
                case 3: k1 ^= ((long) data[tailOffset + 2] & 0xff) << 16;
                case 2: k1 ^= ((long) data[tailOffset + 1] & 0xff) << 8;
                case 1:
                    k1 ^= ((long) data[tailOffset] & 0xff);
                    k1 *= C1;
                    k1 = Long.rotateLeft(k1, 31);
                    k1 *= C2;
                    h1 ^= k1;
            }

            h1 ^= length;
            h1 = fmix64(h1);
            // Ensure positive token values in range [0, Long.MAX_VALUE]
            return h1 & Long.MAX_VALUE;
        }

        private static long getLittleEndianLong(byte[] data, int index) {
            return (((long) data[index] & 0xff))
                    | (((long) data[index + 1] & 0xff) << 8)
                    | (((long) data[index + 2] & 0xff) << 16)
                    | (((long) data[index + 3] & 0xff) << 24)
                    | (((long) data[index + 4] & 0xff) << 32)
                    | (((long) data[index + 5] & 0xff) << 40)
                    | (((long) data[index + 6] & 0xff) << 48)
                    | (((long) data[index + 7] & 0xff) << 56);
        }

        private static long fmix64(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            k *= 0xc4ceb9fe1a85ec53L;
            k ^= k >>> 33;
            return k;
        }
    }
}
