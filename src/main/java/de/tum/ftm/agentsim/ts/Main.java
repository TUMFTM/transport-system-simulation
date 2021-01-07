package de.tum.ftm.agentsim.ts;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import de.tum.ftm.agentsim.ts.utils.UtilHeapMemoryInfo;
import de.tum.ftm.agentsim.ts.utils.UtilJCommander;
import de.tum.ftm.agentsim.ts.utils.UtilXML;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.Logger;


/**
 * Main class of the simulation. Reads any commandline-parameters and validates and reads the configuration file.
 * Initializes and runs the simulation scenario.
 *
 * @author Manfred Klöppel
 */

public class Main {

    // CLI argument parser (JCommander)
    private static UtilJCommander.Args arguments = new UtilJCommander.Args();
    private static JCommander jc = new JCommander(arguments);

    public static void main(String[] args) {

        try {
            init(args);
            validateConfigXML();

            // Load Config-File
            new Config(arguments.configFilePath);

            // Define a new simulation scenario
            Scenario scenario = new Scenario(arguments.simStartTime, arguments.simEndTime);

            // Initialize the scenario
            scenario.initialize();

            // Run the scenario
            scenario.run();

        } catch (ParameterException pe) {
            Logger.error("Error while parsing parameters! Review command line parameters.");
            System.out.println("");
            jc.usage();
        } catch (Config.ConfigException ce) {
            Logger.error(ce);
            Logger.error("Error while reading Config file!");
        } catch (RuntimeException re) {
            Logger.error(re);
            Logger.error("Program stopped!");
        }

    }

    /**
     * Parsing commandline arguments, setting up tinyLog and printing the logo.
     * @param args Arguments passed by command-line
     */
    private static void init(String[] args) {
        jc.setProgramName("transport-system-simulation.jar");
        printLogo();

        // Configure TinyLog Logger Format
        Configurator.currentConfig()
                .formatPattern("{date:HH:mm:ss} {{level}:|min-size=8} {{message}|min-size=100} [{class_name}::{method}]")
                .activate();

        jc.parse(args);
        setVerbosity();

        if (Logger.getLevel() == Level.DEBUG) UtilHeapMemoryInfo.showInfo();
    }

    private static void printLogo() {
        System.out.println("\n\n" +
                "===================================\n" +
                "==  Transport-System Simulation  ==\n" +
                "===================================\n");
    }

    /**
     * Configures the verbosity of the TinyLog-logger
     */
    private static void setVerbosity() {
        Configurator.currentConfig()
                .level(Level.valueOf(arguments.verbosity.toUpperCase()))
                .activate();
        Logger.info("Selected Verbosity: {}", Level.valueOf(arguments.verbosity.toUpperCase()).toString());
    }

    /**
     * Wrapper to validate Config-File. Throws an error to abort simulation in case of erroneous XML-file.
     */
    private static void validateConfigXML() throws RuntimeException {
        // Validate Config-File
        if (!UtilXML.validateXMLSchema(arguments.XSDFilePath, arguments.configFilePath)) {
            throw new RuntimeException("Config File Validation Error!");
        }
    }
}

