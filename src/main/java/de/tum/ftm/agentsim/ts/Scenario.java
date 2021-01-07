package de.tum.ftm.agentsim.ts;

import de.tum.ftm.agentsim.ts.events.*;
import de.tum.ftm.agentsim.ts.assignmentStrategy.AssignmentStrategyInterface;
import de.tum.ftm.agentsim.ts.assignmentStrategy.shortestRouteAssignment.ParallelSRA;
import de.tum.ftm.agentsim.ts.assignmentStrategy.shortestRouteAssignment.SequentialSRA;
import de.tum.ftm.agentsim.ts.assignmentStrategy.closestVehicleAssignment.ParallelCVA;
import de.tum.ftm.agentsim.ts.assignmentStrategy.closestVehicleAssignment.SequentialCVA;
import de.tum.ftm.agentsim.ts.routing.CityGridRouter;
import de.tum.ftm.agentsim.ts.routing.GraphHopperRouter;
import de.tum.ftm.agentsim.ts.simobjects.SimObjectController;
import de.tum.ftm.agentsim.ts.log.DBLog;
import de.tum.ftm.agentsim.ts.utils.SimTime;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.Logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Initializes the simulation scenario by loading all agents and events.
 * Contains and processes the simulation event-queue.
 *
 * @author Manfred Klöppel, Michael Wittmann
 */

public class Scenario {

    private PriorityBlockingQueue<Event> simulationEventQueue;  // Ordered event queue containing all simulation events
    private DBLog dbLog;                                        // DBlogger to write simulation results to database
    private SimObjectController simObjectController;            // Loads and contains all agents

    public AssignmentStrategyInterface assignmentStrategy;             // AssignmentStrategy is controlled by config-file
    private SimTime customSimStartTime;                         // Simulation start time, if set via command-line-parameter
    private SimTime customSimEndTime;                           // Simulation end time, if set via command-line-parameter

    private int totalRequestsCnt;                               // Number of total requests
    private AtomicInteger processedRequestsCnt = new AtomicInteger(0);  // Number of processed requests
    private AtomicInteger failedRequestsCnt = new AtomicInteger(0);     // Number of failed requests

    private long simSystemStartTime;                            // System clock time, when simulation was started

    /**
     * Creation of the simulation scenario and defining the simulation time-interval. Only events which are within
     * this time-interval will be executed
     *
     * @param simStartTimeString Simulation start time, if set via command-line-parameter
     * @param simEndTimeString   Simulation end time, if set via command-line-parameter
     */
    public Scenario(String simStartTimeString, String simEndTimeString) {
        Logger.info("Creating new scenario");
        dbLog = new DBLog(Config.OUTPUT_FOLDER, Config.DB_NAME);
        simulationEventQueue = new PriorityBlockingQueue<>();

        Event.scenario = this;
        Event.dbLog = dbLog;

        // Setting simulation start time, only events which are after this time will be executed
        try {
            this.customSimStartTime = new SimTime(LocalDateTime.parse(simStartTimeString, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm")));
        } catch (DateTimeParseException e) {
            customSimStartTime = new SimTime(0);
        }

        // Setting simulation end time, only events which are before this time will be executed
        try {
            this.customSimEndTime = new SimTime(LocalDateTime.parse(simEndTimeString, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm")));
        } catch (DateTimeParseException e) {
            customSimEndTime = new SimTime(LocalDateTime.parse("2100-01-01_00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm")));
        }
    }

    /**
     * This method initializes the simulation-scenario and loads the provided input files
     */
    protected void initialize() {
        simSystemStartTime = System.nanoTime();
        //GraphHopperRouter hopper = GraphHopperRouter.getInstance();

        Logger.info("Creating SimObjectController...");
        simObjectController = new SimObjectController(this, dbLog);

        Logger.info("Preparing person/request input data...");
        simObjectController.prepareDBInputData(Config.FORCE_UPDATE_REQUEST_INPUT_DATA);

        Logger.info("Loading persons...");
        simObjectController.loadUsersWithRequestsFromDB(Config.REQUESTS_INPUT_FILE);
        Logger.info("{} persons loaded", simObjectController.getUsers().size());

        Logger.info("Adding requests to Task List...");
        PriorityQueue<Event> userRequests = simObjectController.getRequestsForTaskList();
        simulationEventQueue.addAll(userRequests);
        Logger.info("{} requests added to Task List", userRequests.size());
        totalRequestsCnt = userRequests.size();

        Logger.info("Loading vehicles...");
        simObjectController.loadVehiclesFromCSV(Config.FLEET_INPUT_FILE);
        Logger.info("{} vehicles loaded", simObjectController.getFleet().size());

        // Initialize Ride-Sharing Strategy according to Config
        switch (Config.ASSIGNMENT_STRATEGY) {
            case "SCVA":
                assignmentStrategy = new SequentialCVA(this);
                break;
            case "PCVA":
                assignmentStrategy = new ParallelCVA(this);
                break;
            case "SSRA":
                assignmentStrategy = new SequentialSRA(this);
                break;
            case "PSRA":
                assignmentStrategy = new ParallelSRA(this);
                break;
            default:
                throw new RuntimeException("Assignment Strategy not recognized!");
        }
        Logger.info("Selected Assignment Strategy: {}", assignmentStrategy.getStrategyType());

        // Load city grid router, if enabled
        if (Config.USE_GRID_ROUTER) {
            Logger.info("Grid Router enabled. Loading duration grid data...");
            CityGridRouter.getInstance().loadCityGridData();
        }

        if (Config.ENABLE_REBALANCING) {
            Logger.info("Rebalancing enabled...");
            simObjectController.setupRelocationManager();
        }

        // Set/Update overall Simulation Time to either the first event in the event queue or to the custom start
        // time, if provided
        SimTime.updateSimulationTime(simulationEventQueue.peek().getScheduledTime());
        if (customSimStartTime.isGreaterThan(SimTime.now())) {
            SimTime.updateSimulationTime(customSimStartTime);
        }
        SimTime.setSimulationStart(SimTime.now());
        Logger.info("Simulation Time starts at: {}", SimTime.getSimulationStart());

        // Add recurring events
        addRecurringEvents();
    }

    /**
     * The main simulation run. Processes each event of the event-queue
     */
    public void run() {
        Logger.info("Running scenario...");

        try {
            try (ProgressBar pb = new ProgressBar(String.format("%s INFO:\t ",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))), totalRequestsCnt, ProgressBarStyle.ASCII)) {

                // Main simulation loop to process event-queue
                while (!simulationEventQueue.isEmpty()) {

                    // Get the next event
                    Event e = simulationEventQueue.poll();

                    // Only process event, if it is within the simulation start-/end-times
                    if (e.getScheduledTime().isGreaterThan(new SimTime(SimTime.now().getTimeMillis() - 1))
                    && e.getScheduledTime().isLessThan(customSimEndTime)) {

                        // Update simulation-time to the current event-time
                        SimTime.updateSimulationTime(e.getScheduledTime());
                        Logger.trace("Event: {} @ {}", e.getClass().getSimpleName(), e);

                        // Execute event
                        e.action();

                    } else if (e instanceof Event_UserRequest) {
                        // Update progressbar, if any user-request was skipped because it was not within the simulation-time
                        totalRequestsCnt -= 1;
                        pb.maxHint(totalRequestsCnt);
                    }
                    pb.stepTo(processedRequestsCnt.get());
                }
            }

            Logger.info("Simulation completed (SimTime: {}).", SimTime.now());
            closeLogs();

            Logger.info("Finished! (Runtime: {})", getSimDurationString());

        } catch (Exception e) {
            Logger.error("\n\nSimulation Failed! (Runtime: {})", getSimDurationString());
            Logger.error(e);
        }
    }

    /**
     * Calculates the overall simulation run-time
     *
     * @return simulation run-time as string
     */
    private String getSimDurationString() {
        long simDuration = (System.nanoTime() - simSystemStartTime)/1000000;
        return String.format("%02d:%02d:%02d", simDuration/(3600*1000),
                simDuration/(60*1000) % 60,
                simDuration/1000 % 60);
    }

    /**
     * Helper function to setup the recurring events for the event-queue
     */
    private void addRecurringEvents() {
        // Add first event to process ride-sharing buffer to event-queue
        addEvent(new Event_ProcessRequestBuffer(new SimTime(SimTime.now().getTimeMillis() + Config.REQUEST_BUFFER_SECONDS *1000)));

        // Add first event for status logging of SimObjects to event-queue
        Event_LogSimObjectStatus.simObjectController = simObjectController;
        simulationEventQueue.add(new Event_LogSimObjectStatus(new SimTime(SimTime.now())));

        // Add first event for logging of SimObject Route History to event-queue
        Event_LogSimObjectRouteHistory.simObjectController = simObjectController;
        simulationEventQueue.add(new Event_LogSimObjectRouteHistory(new SimTime(SimTime.now())));
    }

    /**
     * Adds an Event to the event queue. Events will be added by their natural order
     *
     * @param e Event to be added
     */
    public void addEvent(Event e) {
        Logger.trace("Added {} to tasklist @ {}", e.getClass().getSimpleName(), e.getScheduledTime().toString());
        simulationEventQueue.add(e);
    }

    /**
     * Writes out remaining information to the database and closes the database
     */
    private void closeLogs() {
        Logger.info("Failed/Processed requests: {}/{} ({0.00}%)", failedRequestsCnt, processedRequestsCnt,
                (double) failedRequestsCnt.get() / processedRequestsCnt.get() * 100);

        Logger.info("Logging vehicle statistics...");
        simObjectController.logVehicleStats();

        // flush and close DB
        Logger.info("Writing DBs...");
        dbLog.writeCallCountersToDB(getCallCounters());
        dbLog.flushBuffersAndPostprocess();

        // Write Simulation Time to ConfigDBTable
        dbLog.writeSimTimeToConfigDB(getSimDurationString());
        dbLog.close();
    }

    /**
     * Returns counters of the simulation for statistical analysis
     *
     * @return Map with both description and the count of implemented counters
     */
    private Map<String, Long> getCallCounters() {
        HashMap<String, Long> counterMap = new LinkedHashMap<>();

        if (Config.USE_GRID_ROUTER) {
            counterMap.put("Grid Router Calls", CityGridRouter.getInstance().getRoutingCallCounter());
        }
        counterMap.put("GraphHopper Calls", GraphHopperRouter.getInstance().getRoutingCallCounter());
        counterMap.putAll(assignmentStrategy.getAssignmentStatistics());

        Logger.debug("Call Statistics:");
        if (Logger.getLevel() == Level.DEBUG) {
            for (Map.Entry<String, Long> pair : counterMap.entrySet()) {
                System.out.println(String.format("%s:\t%s", pair.getKey(), pair.getValue()));
            }
        }
        return counterMap;
    }


    /**
     * @param e Event to be removed
     */
    public void removeEvent(Event e) {
        simulationEventQueue.remove(e);
    }

    public boolean isTaskListEmpty() {
        return simulationEventQueue.isEmpty();
    }

    public int getTaskListSize() {
        return simulationEventQueue.size();
    }

    /**
     * @return Number of processed request-events
     */
    public int getProcessedRequestsCnt() {
        return processedRequestsCnt.get();
    }

    /**
     * Increment the number of processed request-events
     */
    public void incProcessedRequestsCnt() {
        this.processedRequestsCnt.incrementAndGet();
    }

    /**
     * Increment the number of failed request-events
     */
    public void incFailedRequestsCnt() {
        incProcessedRequestsCnt();
        this.failedRequestsCnt.incrementAndGet();
    }

    public DBLog getDBLog() {
        return this.dbLog;
    }

    public SimObjectController getSimObjectController() {
        return simObjectController;
    }

    /**
     * Checks, if there are any user-request-events or vehicle-events in the event-queue
     * @return true, if any user/vehicle-event is apparent, else returns false
     */
    public boolean areUserOrVehicleEventsInQueue() {
        for (var event : simulationEventQueue) {
            if (event instanceof Event_UserRequest) return true;
            if (event instanceof Event_VehicleNextActivity) return true;
        }
        return false;
    }

    /**
     * Checks, if there are any user-request-events in the event-queue
     * @return true, if any user-event is apparent, else returns false
     */
    public boolean areRelocationEventsInQueue() {
        for (var event : simulationEventQueue) {
            if (event instanceof Event_RebalancingManager) return true;
        }
        return false;
    }
}
