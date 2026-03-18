package io.github.bareboneslib.bareboneshll;

import net.openhft.hashing.LongHashFunction;

import java.util.Random;

public class HLLMain {
    public static void main(String[] args) {
        LongHashFunction hash = LongHashFunction.xx();
        int P = 12, R = 4;

        HLL[] arr = new HLL[50000];
        Random rand = new Random();

        HLL a = new HLL(P, R);
        int uniques = 0;
        for(int j=0; j < arr.length; j++) {
            arr[j] = new HLL(P, R);
            int size = rand.nextInt(10000);
            uniques = Math.max(uniques, size);
            for(int i = 0; i < size; i++) {
                arr[j].add(hash.hashInt(i));
            }
        }
        for(int i = 0; i<arr.length; i++) {
            a.merge(arr[i]);
        }

        for(int i=0 ; i<arr.length; i++) {
            arr[i].estimate();
        }

        System.out.println("uniques: " + (uniques + 1));
        System.out.println("estimate: " + a.estimate());

        System.err.println("---- debug ----");
        a.debugInfo();
        System.err.println("---- debug ----");

        byte[] ser = a.serialize();
        System.out.println("Serialized length: " + ser.length);

        HLL newHLL = HLL.deserialize(ser);

        System.out.println("estimate desered: " + newHLL.estimate());

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt flag
        }
    }
}
