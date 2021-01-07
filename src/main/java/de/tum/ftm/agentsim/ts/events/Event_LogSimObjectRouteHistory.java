package de.tum.ftm.agentsim.ts.events;

import de.tum.ftm.agentsim.ts.Config;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.log.DBTableEntry;
import de.tum.ftm.agentsim.ts.routing.route.Route;
import de.tum.ftm.agentsim.ts.routing.route.RouteStep;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectController;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectRoutable;
import de.tum.ftm.agentsim.ts.simobjects.User;
import de.tum.ftm.agentsim.ts.simobjects.Vehicle;
import de.tum.ftm.agentsim.ts.utils.SimTime;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;


/**
 * This event triggers the logging of routes of the Vehicle and User Agents.
 * The event is executed in configured intervals. Each time this event is executed, a new event is inserted
 * into the event queue, until no more User or Vehicle events are in the event queue.
 * @author Manfred Klöppel
 */
public class Event_LogSimObjectRouteHistory extends Event {

    public static SimObjectController simObjectController;

    public Event_LogSimObjectRouteHistory(SimTime scheduledTime) {
        super(scheduledTime);
    }

    @Override
    public void action() {

        // Log routes from all vehicles and users
        logRoutes();

        // Set next event to update all SimObjectStatus
        setNextUpdate();

    }

    /**
     * Insert the next logging event to the event-queue, as long as there are still User and Vehicle events
     */
    private void setNextUpdate() {
        if (scenario.areUserOrVehicleEventsInQueue()) {
            scenario.addEvent(new Event_LogSimObjectRouteHistory(new SimTime(SimTime.now().getTimeMillis() + Config.SIMOBJECT_ROUTEHISTORY_LOG_FREQUENCY_SECONDS*1000)));
        }
    }

    /**
     * This function extracts the routes from all vehicles and users for logging to the DB.
     */
    private void logRoutes() {
        // Log routes from vehicles
        Map<Route, Vehicle> vehicleRouteMap = new HashMap<>();
        for (Vehicle v : simObjectController.getFleet().values()) {
            if (v.getRouteHistory() != null) {
                if (v.getRouteHistory().getRouteSteps().size() > 0) {
                    vehicleRouteMap.put(v.getRouteHistory(), v);
                }
            }
        }
        logRoutesToDB(vehicleRouteMap);

        // Log routes from users. Routes need to be extracted from list of requests of the user. Log only routes
        // which are completed
        Map<Route, User> userRouteMap = new HashMap<>();
        for (User u : simObjectController.getUsers().values()) {
            for (User.TripRequest r : u.getUserTripRequestsList()) {
                if (!r.isRouteHistoryBlocked()) {
                    if (r.getRouteHistory() != null) {
                        userRouteMap.put(r.getRouteHistory(), u);
                    }
                }
            }
        }
        logRoutesToDB(userRouteMap);
    }

    /**
     * Takes a Map of Routes and Users and builds an object for logging. The RouteSteps will be cleared
     * to reduce memory usage after logging to the db.
     * @param simObjectRouteMap Contains all Routes from all Vehicles and Users
     * @param <T> Generics for simObjectRoutable
     */
    private <T extends SimObjectRoutable> void logRoutesToDB(Map<Route, T> simObjectRouteMap) {
        for (Map.Entry<Route, T> entry : simObjectRouteMap.entrySet()) {
            Route route = entry.getKey();
            T object = entry.getValue();

            TreeSet<RouteStep> routeSteps = route.getRouteSteps();
            for (RouteStep rs : routeSteps) {
                DBLog.dbTableRoutes.addLogEntry(new DBTableEntry.Builder()
                        .routeID(route.getRouteID())
                        .objectID(object.getId())
                        .objectType(object.getClass().getName())
                        .origStartTime(rs.getStartTime())
                        .origStopTime(rs.getEndTime())
                        .stepType(rs.getStepType().toString())
                        .drivingDurationMIN(((double) rs.getDurationMS()) / 1000 / 60)
                        .drivingDistanceKM(rs.getDistanceM() / 1000)
                        .geomWKT(rs.getWKTFromRouteStep().toString())
                        .currentPaxCount(rs.getPassengerCount())
                        .currentRequestsCount(rs.getRequestCount())
                        .build());
            }

            // clear the routesteps of the routehistory of the vehicle/users in order to free up memory
            route.getRouteSteps().clear();
        }
    }
}
