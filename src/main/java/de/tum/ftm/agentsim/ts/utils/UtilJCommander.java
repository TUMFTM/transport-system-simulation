package de.tum.ftm.agentsim.ts.utils;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Helper class to parse command-line parameters with JCommander
 *
 * @author Manfred Klöppel
 */
public class UtilJCommander {

    /**
     * Definition of acceptable parameters
     */
    public static class Args {
        @Parameter(names = {"-v", "--verbose"}, description = "Level of verbosity (TRACE, DEBUG, INFO, WARNING, ERROR)", required = false)
        public String verbosity = "INFO";

        // To validate config-XML file
        @Parameter(names = {"-c", "--xsd"}, description = "Path to XSD-File", required = false)
        public String XSDFilePath = "config.xsd";

        @Parameter(names = {"-ts", "--timestart"}, description = "Simulation start time. Format: -ts 'YYYY-MM-DD_HH:MM'",
                required = false, validateWith = UtilJCommander.DateValidator.class)
        public String simStartTime = "";

        @Parameter(names = {"-te", "--timeend"}, description = "Simulation end time. Format: -te 'YYYY-MM-DD_HH:MM'",
                required = false, validateWith = UtilJCommander.DateValidator.class)
        public String simEndTime = "";

        @Parameter(description = "CONFIG-FILE", required = true)
        public String configFilePath = "config.xml";
    }

    /**
     * Validates the format of a String which should represent a valid date/time
     */
    public static class DateValidator implements IParameterValidator {
        public void validate(String name, String value) throws ParameterException {
            try {
                LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm"));
            } catch (DateTimeParseException e) {
                throw new ParameterException("Parameter " + name + " should have format 'YYYY-MM-DD_HH:MM'");
            }
        }
    }
}
