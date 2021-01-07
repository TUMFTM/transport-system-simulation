package de.tum.ftm.agentsim.ts.utils;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Helper class with some static utils for strings.
 *
 * @author Michael Wittmann
 */
public class UtilStrings {

    private static final String PATTERN_DATE = "yyyy-MM-dd HH:mm:ss";

    /**
     * Parses date to String according to pattern(yyyyMMdd_HHmmss)
     *
     * @param date date to be parsed
     * @return parsed date
     */
    public static String dateToStringFormatYYYYMMDD_HHMMSS(Date date) {
        SimpleDateFormat dtf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        return dtf.format(date);
    }

    /**
     * Parses date to String according to pattern(yyyy-MM-dd HH:mm:ss)
     *
     * @param date date to be parsed
     * @return parsed date
     */
    public static String dateToStringFormatISO(Date date) {
        SimpleDateFormat dtf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dtf.format(date);
    }

    /**
     * Parses String to date
     *
     * @param string date to be parsed need to be formatted like (yyyy-MM-dd HH:mm:ss)
     * @return parsed date
     */
    public static Date parseDate(String string) {
        Date d = null;
        DateFormat df = new SimpleDateFormat(PATTERN_DATE);
        df.setLenient(false);
        try {
            d = df.parse(string);
        } catch (Exception e) {
            return null;
        }
        return d;
    }

    /**
     * Parses a given double into a String formatted to #####.#
     *
     * @param number to be parsed
     * @return number as String formatted to #####.#
     */
    public static String parseDoubleOneDigit(double number) {
        String pattern = "##.#";
        DecimalFormat df = new DecimalFormat(pattern);

        return df.format(number);
    }

    /**
     * Parses milliseconds to HH:MM:SS
     *
     * @param milliseconds
     * @return String formated to HH:MM:SS
     */
    public static String formatMilliseconds(long milliseconds) {
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(milliseconds),
                TimeUnit.MILLISECONDS.toMinutes(milliseconds) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(milliseconds) % TimeUnit.MINUTES.toSeconds(1));
        return hms;

    }

}
