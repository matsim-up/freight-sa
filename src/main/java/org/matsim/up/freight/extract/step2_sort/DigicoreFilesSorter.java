package org.matsim.up.freight.extract.step2_sort;

 import org.apache.log4j.Logger;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.up.freight.containers.DigicoreRecord;
import org.matsim.up.utils.FileUtils;
import org.matsim.up.utils.Header;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The second step in processing the DigiCore data file. This class reads vehicle files
 * split from the DigiCore data set and sort them chronologically according to the time
 * stamp.
 *
 * @author jwjoubert
 */
public class DigicoreFilesSorter {
    private final static Logger log = Logger.getLogger(DigicoreFilesSorter.class);
    private final static String DELIMITER = ","; // this could be "," or "\t"
    private final String root;


    /**
     * Instantiates the file sorter.
     *
     * @param root the absolute path of the folder in which vehicle files can
     *             be found that must be sorted.
     */
    public DigicoreFilesSorter(String root) {
        this.root = root;
    }

    /**
     * Implementing the file sorter.
     *
     * @param args only a single argument: the absolute path of the folder
     *             containing the unsorted vehicle files.
     */
    public static void main(String[] args) {
        Header.printHeader(DigicoreFilesSorter.class, args);
        DigicoreFilesSorter dfs;
        if (args.length != 1) {
            throw new RuntimeException("Must provide path for `Vehicles' folder.");
        } else {
            dfs = new DigicoreFilesSorter(args[0]);
        }

        log.info("=============================================================");
        log.info("  Sorting DigiCore vehicle files after they've been split.");
        log.info("-------------------------------------------------------------");

        dfs.sortVehicleFiles();

        Header.printFooter();
    }


    public void sortVehicleFiles() {
        File folder = new File(this.root);

        File[] filesSorted = folder.listFiles(FileUtils.getFileFilter(".txt.gz"));
        assert filesSorted != null;
        log.info("Number of files already sorted (*.txt.gz): " + filesSorted.length);

        File[] filesUnsorted = folder.listFiles(FileUtils.getFileFilter(".txt"));
        assert filesUnsorted != null;
        log.info("Number of files to sort (*.txt)          : " + filesUnsorted.length);

        Counter fileCounter = new Counter("   files sorted: ");
        int maxLines = 0;
        File maxFile = null;

        for (File theFile : filesUnsorted) {
            List<DigicoreRecord> unsortedList = readFileToList(theFile);
            List<DigicoreRecord> sortedList = sortTimeMerge(unsortedList);

            if (sortedList.size() > maxLines) {
                maxLines = sortedList.size();
                maxFile = theFile;
            }

            writeList(theFile, sortedList);

            fileCounter.incCounter();

            /* Delete the unsorted file once sorted. */
            FileUtils.delete(theFile);
        }
        fileCounter.printCounter();

        if (maxFile != null) {
            log.info("Largest file is " + maxFile.getName() + " and has " + maxLines + " gps records.");
        }
    }


    /**
     * This method reads the given vehicle file, creates a new {@link DigicoreRecord} for
     * each line, and returns the complete GPS log as a {@link List} of {@link DigicoreRecord}s.
     *
     * @param file a valid existing vehicle file containing one or more GPS log records;
     * @return a {@link List} of {@link DigicoreRecord}s; one record for each line.
     */
    private List<DigicoreRecord> readFileToList(File file) {
        int vehID;
        long time;
        double longitude;
        double latitude;
        int status;
        int speed;

        List<DigicoreRecord> log = new ArrayList<>();
        try (BufferedReader br = IOUtils.getBufferedReader(file.getAbsolutePath())) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] sa = line.split(DELIMITER);
                if (sa.length == 6) {
                    try {
                        vehID = Integer.parseInt(sa[0]);
                        time = Long.parseLong(sa[1]);
                        longitude = Double.parseDouble(sa[2]);
                        latitude = Double.parseDouble(sa[3]);
                        status = Integer.parseInt(sa[4]);
                        speed = Integer.parseInt(sa[5]);

                        log.add(new DigicoreRecord(vehID, time, longitude, latitude, status, speed));
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return log;
    }

    private void writeList(File file, List<DigicoreRecord> sortedList) {
        try (BufferedWriter bw = IOUtils.getBufferedWriter(file.getAbsolutePath() + ".gz")) {
            // Write the header, if required, for ArcGIS inclusion
            bw.write("VehicleID");
            bw.write(DELIMITER);
            bw.write("Time");
            bw.write(DELIMITER);
            bw.write("Long");
            bw.write(DELIMITER);
            bw.write("Lat");
            bw.write(DELIMITER);
            bw.write("Status");
            bw.write(DELIMITER);
            bw.write("Speed");
            bw.newLine();

            // write all points, except the last, with a newLine
            for (int i = 0; i < (sortedList.size() - 1); i++) {
                DigicoreRecord dr = sortedList.get(i);
                bw.write(String.valueOf(dr.getVehID()));
                bw.write(DELIMITER);
                bw.write(String.valueOf(dr.getTime()));
                bw.write(DELIMITER);
                bw.write(String.valueOf(dr.getLongitude()));
                bw.write(DELIMITER);
                bw.write(String.valueOf(dr.getLatitude()));
                bw.write(DELIMITER);
                bw.write(String.valueOf(dr.getStatus()));
                bw.write(DELIMITER);
                bw.write(String.valueOf(dr.getSpeed()));
                bw.newLine();
            }

            // write the last element
            DigicoreRecord drLast = sortedList.get(sortedList.size() - 1);
            bw.write(String.valueOf(drLast.getVehID()));
            bw.write(DELIMITER);
            bw.write(String.valueOf(drLast.getTime()));
            bw.write(DELIMITER);
            bw.write(String.valueOf(drLast.getLongitude()));
            bw.write(DELIMITER);
            bw.write(String.valueOf(drLast.getLatitude()));
            bw.write(DELIMITER);
            bw.write(String.valueOf(drLast.getStatus()));
            bw.write(DELIMITER);
            bw.write(String.valueOf(drLast.getSpeed()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Sorts the list of records according to the time stamp.<br><br>
     *
     * See also {@link DigicoreRecord#compareTo(DigicoreRecord)}
     *
     * @param log unsorted {@link List} of {@link DigicoreRecord}s.
     * @return sorted {@link List} of {@link DigicoreRecord}s.
     */
    private List<DigicoreRecord> sortTimeMerge(List<DigicoreRecord> log) {
        Collections.sort(log);
        return log;
    }

}
