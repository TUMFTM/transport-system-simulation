package de.tum.ftm.agentsim.ts.utils;

import java.util.Random;

/**
 * Provides some static methods to generate RandomNumbers
 *
 * @author Michael Wittmann
 */
public class UtilRandomNumber {

    /**
     * @param min
     * @param max
     * @return a random Integer value between <b>min</b> and <b>max</b>
     */
    public static int randInt(int min, int max) {
        Random rand = new Random();
        int randomNum = rand.nextInt((max - min) + 1) + min;
        return randomNum;
    }
}
