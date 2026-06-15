package com.example.helper;

public class ThreeDigitRangeHasher implements HashCalculator {
    @Override
    public int hash(String key) {
        // Simple hash function that maps the key to a number between 0 and 999
        return Math.abs(key.hashCode()) % 1000;
    }

}
