/* *********************************************************************** *
 * project: org.matsim.*
 * TurnkeyExtractor.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.up.freight.extract;

import java.io.File;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.up.freight.extract.step1_split.DigicoreFileSplitter;
import org.matsim.up.freight.extract.step2_sort.DigicoreFilesSorter;
import org.matsim.up.freight.extract.step3_extract.MultiThreadChainExtractor;
import org.matsim.up.freight.extract.step4_collate.DigicoreVehicleCollator;
import org.matsim.up.utils.Header;


/**
 * Class to perform all four extraction phases for a given data set:
 * <ol>
 * 		<li> split the raw data into unique vehicle files;
 * 		<li> sorting the records chronologically;
 * 		<li> extracting the activity chains; and finally
 * 		<li> combine them into a single container.
 * </ol>
 *
 * @author jwjoubert
 */
public class TurnkeyExtractor {
    final private static Logger LOG = Logger.getLogger(TurnkeyExtractor.class);
    final private static String DEFAULT_INPUT = "/data/digicore/longitudinal/raw/";
    final private static String DEFAULT_OUTPUT = "/data/digicore/longitudinal/processed/";
    final private static String DEFAULT_STATUS_FILE = "/data/digicore/longitudinal/config/status.txt";

    final static int FIELD_TIME = 0;
    final static int FIELD_LATITUDE = 1;
    final static int FIELD_LONGITUDE = 2;
    final static int FIELD_SPEED = 3;
    final static int FIELD_IGNITION_SIGNAL = 4;
    final static int FIELD_VEHICLE_ID = 5;

    final static String FOLDER_VEHICLES = "vehicles/";
    final static String FOLDER_XML = "xml/";
    final static String FILENAME_VEHICLES = "digicoreVehicles.xml.gz";

    /* Parameters for chain extraction. */
    static final String DEFAULT_THREADS = "20";
    static final String DEFAULT_MAJOR_THRESHOLD = "18000";
    static final String DEFAULT_MINOR_THRESHOLD = "60";

    /**
     * @param args optional arguments in the following order:
     *              <ol>
     *              <li>input file of raw GPS traces;</li>
     *              <li>output folder where activity chain is to be extracted;</li>
     *              <li>description for the file; and</li>
     *              <li>file that indicates what ignition signals depict engine-on -off.</li>
     *              </ol>
     *             If no arguments are provided, the defaults point to the first
     *             month's file as located on the Hobbes server(s).
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            LOG.info("Insufficient arguments. Returning to defaults.");
            args = new String[]{DEFAULT_INPUT, DEFAULT_OUTPUT, DEFAULT_STATUS_FILE};
        }
        Header.printHeader(TurnkeyExtractor.class, args);

        String inputFolder = args[0];
        String outputFolder = args[1];
        String statusFile = args[2];

        extractAll(inputFolder, outputFolder, statusFile);

        Header.printFooter();
    }

    private TurnkeyExtractor() {
        /* Hide constructor. */
    }

    static void extract(String inputFile, String outputFolder, String descr, String statusFile) {
        LOG.info("Executing the turnkey extraction... this may take some time.");

        /* Splitting */
        String[] splitArgs = {inputFile, outputFolder, "1",
                String.valueOf(FIELD_VEHICLE_ID),
                String.valueOf(FIELD_TIME),
                String.valueOf(FIELD_LONGITUDE),
                String.valueOf(FIELD_LATITUDE),
                String.valueOf(FIELD_IGNITION_SIGNAL),
                String.valueOf(FIELD_SPEED)};
        DigicoreFileSplitter.main(splitArgs);

        /* Sorting */
        String[] sortArgs = {outputFolder + FOLDER_VEHICLES};
        DigicoreFilesSorter.main(sortArgs);

        /* Extracting */
        boolean createdXmlFolder = new File(outputFolder + FOLDER_XML).mkdirs();
        if (!createdXmlFolder) {
            LOG.error("Could not create the ./xml/ folder.");
        }
        String[] extractArgs = {
                outputFolder + FOLDER_VEHICLES,
                statusFile,
                outputFolder + FOLDER_XML,
                DEFAULT_THREADS,
                DEFAULT_MAJOR_THRESHOLD,
                DEFAULT_MINOR_THRESHOLD,
                TransformationFactory.HARTEBEESTHOEK94_LO29};
        MultiThreadChainExtractor.main(extractArgs);

        /* Collating */
        String[] collateArgs = {outputFolder, outputFolder + FILENAME_VEHICLES, TransformationFactory.HARTEBEESTHOEK94_LO29, descr, "true"};
        DigicoreVehicleCollator.main(collateArgs);

        LOG.info("Done with the turnkey extraction.");
    }

    /**
     * Method meant to be called on the server only, where all the necessary
     * Digicore files are located togather.
     *
     * @param rawFolder folder where all the csv.gz files are;
     * @param processedFolder folder where all the individual months' data will
     *                        be written/extracted to; and
     * @param statusFile the file showing the ignition status (on or off) for
     *                   different codes in the raw input data.
     */
    static void extractAll(String rawFolder, String processedFolder, String statusFile) {
        rawFolder += rawFolder.endsWith("/") ? "" : "/";
        processedFolder += processedFolder.endsWith("/") ? "" : "/";

        String[] months = new String[]{
                "201001", "201002", "201003", "201004", "201005", "201006", "201007", "201008", "201009", "201010", "201011", "201012",
                "201101", "201102", "201103", "201104", "201105", "201106", "201107", "201108", "201109", "201110", "201111", "201112",
                "201201", "201202", "201203", "201204", "201205", "201206", "201207", "201208", "201209", "201210", "201211", "201212",
                "201301", "201302", "201303", "201304", "201305", "201306", "201307", "201308", "201309", "201310", "201311", "201312",
                "201401", "201402", "201403", "201404", "201405"
        };

        for(String month : months){
            File input = new File(String.format(Locale.US, "%s%s.csv.gz", rawFolder, month));
            if(input.exists()){
                String output = String.format(Locale.US, "%s%s/", processedFolder, month);
                String description = "One month's activity chains " + month + " in Hartebeesthoek94_Lo29_NE";
                extract(input.getAbsolutePath(), output, description, statusFile);
            }
        }
    }

}
