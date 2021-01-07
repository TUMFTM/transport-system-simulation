package de.tum.ftm.agentsim.ts;

import de.tum.ftm.agentsim.ts.utils.UtilXML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Helper class to load and store simulation parameters from configuration file
 *
 * @author Manfred Klöppel, Michael Wittmann
 */
public class Config {

    // Map to log configuration to database
    public static Map<String, String> configMap = new LinkedHashMap<>();

    // SIMULATION INPUT FILES
    public static String    REQUESTS_INPUT_FILE;
    public static String    FLEET_INPUT_FILE;
    public static boolean   FORCE_UPDATE_REQUEST_INPUT_DATA;
    public static boolean   STORE_ROUTE_WKT_IN_INPUT_DATA;

    // DB OUTPUT CONFIG
    public static String    SPATIALITE_PATH;
    public static String    OUTPUT_FOLDER;
    public static String    DB_NAME;
    public static boolean   DB_NAME_APPEND_DATETIME;
    public static int       DB_BATCH_SIZE;
    public static int       SIMOBJECT_MAP_UPDATE_FREQUENCY_SECONDS;
    public static boolean   ENABLE_LOG_ROUTEHISTORY;
    public static int       SIMOBJECT_ROUTEHISTORY_LOG_FREQUENCY_SECONDS;
    public static boolean   ENABLE_POSTPROCESSING;

    // ASSIGNMENT-STRATEGY CONFIG
    public static String    ASSIGNMENT_STRATEGY;
    public static int       JSPRIT_MAX_ITERATIONS;
    public static int       VEHICLE_FILTER_LIST_SIZE;
    public static int       REQUEST_BUFFER_SECONDS;
    public static boolean   REPEATED_ASSIGNMENT;
    public static boolean   PRINT_JSPRIT_SOLUTION_INFO;
    public static int       MAX_POSITION_AGE_SECONDS;
    public static int       VEHICLE_PICKUP_DROPOFF_DELAY_SECONDS;
    public static int       PICKUP_DROPOFF_DURATION_PER_PERSON_SECONDS;
    public static double    MAX_IN_VEH_TIME_ELONGATION_FACTOR;
    public static int       MAX_WAITING_TIME_SECONDS;
    public static int       ACCEPTABLE_TIME_IN_VEH_SECONDS;
    public static boolean   ENABLE_ALONSO_TRAVEL_DELAY_MODE;
    public static int       USER_ALONSO_MAX_DELAY_SECONDS;
    public static String    VEHICLE_SEARCH_MODE;

    // REBALANCING CONFIG
    public static boolean   ENABLE_REBALANCING;
    public static String    REBALANCING_MANAGER_TYPE;
    public static String    REBALANCING_MAP_PATH;
    public static boolean   LOG_REBALANCING;
    public static String    REBALANCING_LOG_PATH;

    // GENERAL GRID CONFIG
    public static double    GRID_TOP_LEFT_LONGITUDE;
    public static double    GRID_TOP_LEFT_LATITUDE;
    public static int       GRID_MIN_WIDTH_KM;
    public static int       GRID_MIN_HEIGHT_KM;
    public static boolean   USE_GRID_VALIDITY_AREA;
    public static String    GRID_VALIDITY_AREA_WKT;

    // GRID-ROUTER CONFIG
    public static boolean   USE_GRID_ROUTER;
    public static String    DURATION_GRID_PATH;
    public static int       DURATION_GRID_CELL_LENGTH;
    public static boolean   WRITE_DURATION_GRID_TO_CSV;
    public static boolean   ENABLE_GRIDROUTER_WARNINGS;

    // GRAPHHOPPER-ROUTER CONFIG
    public static boolean   ENABLE_TURN_RESTRICTIONS_AND_HEADING;
    public static String    GRAPHHOPPER_FOLDER_GRAPH;
    public static String    GRAPHHOPPER_CH_FOLDER_GRAPH;
    public static String    GRAPHHOPPER_OSM_FILE;

    // GENERAL ROUTING CONFIG
    public static double    TRAVEL_TIME_FACTOR_CAR;
    public static double    TRAVEL_TIME_FACTOR_FOOT;


    public Config(String configFilePath) throws ConfigException {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        try {
            //parse using builder to get DOM representation of the XML file
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document dom = db.parse(configFilePath);
            Element rootElement = dom.getDocumentElement();

            REQUESTS_INPUT_FILE = UtilXML.getChildStringValueForElement(rootElement, "requests_input_file");
            FLEET_INPUT_FILE = UtilXML.getChildStringValueForElement(rootElement, "fleet_input_file");
            SPATIALITE_PATH = UtilXML.getChildStringValueForElement(rootElement, "spatialite_path");
            ENABLE_TURN_RESTRICTIONS_AND_HEADING = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "enable_turn_restrictions_and_heading"));
            GRAPHHOPPER_FOLDER_GRAPH = UtilXML.getChildStringValueForElement(rootElement, "graphhopper_folder_graph");
            GRAPHHOPPER_CH_FOLDER_GRAPH = UtilXML.getChildStringValueForElement(rootElement, "graphhopper_ch_folder_graph");
            GRAPHHOPPER_OSM_FILE = UtilXML.getChildStringValueForElement(rootElement, "graphhopper_osm_file");
            TRAVEL_TIME_FACTOR_CAR = Double.parseDouble(UtilXML.getChildStringValueForElement(rootElement, "travel_time_factor_car"));
            TRAVEL_TIME_FACTOR_FOOT = Double.parseDouble(UtilXML.getChildStringValueForElement(rootElement, "travel_time_factor_foot"));
            OUTPUT_FOLDER = UtilXML.getChildStringValueForElement(rootElement, "output_folder");
            DB_NAME = UtilXML.getChildStringValueForElement(rootElement, "db_name");
            DB_NAME_APPEND_DATETIME = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "db_name_append_datetime"));
            ENABLE_POSTPROCESSING = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "enable_postprocessing"));
            DB_BATCH_SIZE = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "db_batch_size"));
            SIMOBJECT_MAP_UPDATE_FREQUENCY_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "simobject_map_update_frequency_seconds"));
            ENABLE_LOG_ROUTEHISTORY = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "enable_log_routehistory"));
            SIMOBJECT_ROUTEHISTORY_LOG_FREQUENCY_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "simobject_routehistory_log_frequency_seconds"));
            ASSIGNMENT_STRATEGY = UtilXML.getChildStringValueForElement(rootElement, "assignment_strategy");
            REQUEST_BUFFER_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "request_buffer_seconds"));
            MAX_POSITION_AGE_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "max_position_age_seconds"));
            VEHICLE_PICKUP_DROPOFF_DELAY_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "vehicle_pickup_dropoff_delay_seconds"));
            PICKUP_DROPOFF_DURATION_PER_PERSON_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "pickup_dropoff_duration_per_person_seconds"));
            MAX_IN_VEH_TIME_ELONGATION_FACTOR = Double.parseDouble(UtilXML.getChildStringValueForElement(rootElement, "user_max_in_veh_time_elongation_factor"));
            MAX_WAITING_TIME_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "user_max_waiting_time_seconds"));
            ACCEPTABLE_TIME_IN_VEH_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "user_acceptable_time_in_veh_seconds"));
            USE_GRID_ROUTER = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "use_duration_grid"));
            PRINT_JSPRIT_SOLUTION_INFO = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "print_jsprit_solution_info"));
            REPEATED_ASSIGNMENT = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "repeated_assignment"));
            DURATION_GRID_PATH = UtilXML.getChildStringValueForElement(rootElement, "duration_grid_path");
            GRID_TOP_LEFT_LONGITUDE = Double.parseDouble(UtilXML.getChildStringValueForElement(rootElement, "grid_top_left_lon"));
            GRID_TOP_LEFT_LATITUDE = Double.parseDouble(UtilXML.getChildStringValueForElement(rootElement, "grid_top_left_lat"));
            GRID_MIN_WIDTH_KM = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "grid_min_width_km"));
            GRID_MIN_HEIGHT_KM = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "grid_min_height_km"));
            DURATION_GRID_CELL_LENGTH = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "duration_grid_cell_length"));
            FORCE_UPDATE_REQUEST_INPUT_DATA = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "force_update_request_input_data"));
            STORE_ROUTE_WKT_IN_INPUT_DATA = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "store_route_WKT_in_input_data"));
            ENABLE_REBALANCING = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "enable_rebalancing"));
            ENABLE_ALONSO_TRAVEL_DELAY_MODE = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "enable_alonso-mora_travel_delay_mode"));
            USER_ALONSO_MAX_DELAY_SECONDS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "user_alonso-mora_max_delay_seconds"));
            REBALANCING_MANAGER_TYPE = UtilXML.getChildStringValueForElement(rootElement, "rebalancing_manager_type");
            REBALANCING_MAP_PATH = UtilXML.getChildStringValueForElement(rootElement, "rebalancing_map_path");
            WRITE_DURATION_GRID_TO_CSV = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "write_duration_grid_to_csv"));
            USE_GRID_VALIDITY_AREA = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "use_grid_validity_area"));
            ENABLE_GRIDROUTER_WARNINGS = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "enable_gridrouter_warnings"));
            GRID_VALIDITY_AREA_WKT = UtilXML.getChildStringValueForElement(rootElement, "grid_validity_area_WKT");
            VEHICLE_FILTER_LIST_SIZE = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "vehicle_filter_list_size"));
            JSPRIT_MAX_ITERATIONS = Integer.parseInt(UtilXML.getChildStringValueForElement(rootElement, "jsprit_max_iterations"));
            VEHICLE_SEARCH_MODE = UtilXML.getChildStringValueForElement(rootElement, "vehicle_search_mode");
            LOG_REBALANCING = Boolean.parseBoolean(UtilXML.getChildStringValueForElement(rootElement, "log_rebalancing"));
            REBALANCING_LOG_PATH = UtilXML.getChildStringValueForElement(rootElement, "rebalancing_log_path");

            // Generate map with all keys and values from config-file for database-log
            NodeList nodeList = rootElement.getChildNodes();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    configMap.put(node.getNodeName(), node.getFirstChild().getNodeValue());
                }
            }

        } catch (ParserConfigurationException | SAXException | IOException error) {
            throw new ConfigException(error.toString());
        }
    }

    class ConfigException extends Exception {
        ConfigException(String e) {
            super(e);
        }
    }
}
