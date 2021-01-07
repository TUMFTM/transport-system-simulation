package de.tum.ftm.agentsim.ts.utils;

import org.pmw.tinylog.Logger;

/**
 * Helper class to print memory information
 *
 * @author Manfred Klöppel
 */
public class UtilHeapMemoryInfo {

    public static void showInfo() {
        // Get current size of heap in bytes
        long heapSize = Runtime.getRuntime().totalMemory()/1024/1024;

        // Get maximum size of heap in bytes. The heap cannot grow beyond this size.// Any attempt will result in an OutOfMemoryException.
        long heapMaxSize = Runtime.getRuntime().maxMemory()/1024/1024;

        // Get amount of free memory within the heap in bytes. This size will increase // after garbage collection and decrease as new objects are created.
        long heapFreeSize = Runtime.getRuntime().freeMemory()/1024/1024;

        long usedMemory = heapSize-heapFreeSize;

        Logger.info("\nMEMORY INFO\n" +
                "Max Heap Size:\t{} MB\n" +
                "Cur Heap Size:\t{} MB\n" +
                "Free Heap Size:\t{} MB\n" +
                "Used Memory:\t{} MB ({0.0}%)\t\t\t\t\t\t\t\t\t", heapMaxSize, heapSize, heapFreeSize, usedMemory, ((double)usedMemory/heapMaxSize)*100);
    }
}
