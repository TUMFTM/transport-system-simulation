package de.tum.ftm.agentsim.ts.simobjects;

/**
 * Enum Class which defines all status of the simulation objects
 *
 * @author Manfred Klöppel
 */

public enum SimObjectStatus {
    // User status
    USER_IDLE,
    USER_REQUESTING_PICKUP,
    USER_WAITING_FOR_PICKUP,
    USER_IN_TRANSIT,

    // Vehicle status
    VEHICLE_IDLE,
    VEHICLE_RELOCATING,
    VEHICLE_IN_SERVICE
}
