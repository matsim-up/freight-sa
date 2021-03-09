package org.matsim.up.freight.clustering;

import org.apache.log4j.Logger;
import org.matsim.up.freight.extract.ExtractionUtils;
import org.matsim.up.utils.Header;

import java.util.List;

/**
 * Single class to execute the {@link DigicoreClusterRunner} for all months in
 * the Digicore longitudinal data set.
 *
 * @author jwjoubert
 */
class RunClusteringForAllPeriods {
    final private static Logger LOG = Logger.getLogger(RunClusteringForAllPeriods.class);
    final private static String DEFAULT_FOLDER = "/data/digicore/longitudinal/processed/";
    final private static String DEFAULT_SHAPEFILE = "./input/shapefiles/southAfrica/SouthAfrica_GAP2010_H94Lo29_NE.shp";
    final private static String DEFAULT_SHAPEFILE_ID = "1";
    final private static String DEFAULT_THREADS = "22";

    /**
     * Optional arguments
     * @param args in the following order:
     *             <ol>
     *             <li>folder where the different time periods' folders can be found;</li>
     *             <li>shapefile that is used by the clustering to determine the extent of the study area;</li>
     *             <li>the field number in the shapefile indicating the unique object number/name; and</li>
     *             <li>the number of threads to use for each clustering sessions.</li>
     *             </ol>
     *
     *             If no arguments are passed, the defaults represent the data
     *             as it resides on both Hobbes servers.
     */
    public static void main(String[] args) {
        if(args.length == 0){
            LOG.warn("Insufficient arguments. Reverting to defaults.");
            args = new String[]{DEFAULT_FOLDER, DEFAULT_SHAPEFILE, DEFAULT_SHAPEFILE_ID, DEFAULT_THREADS};
        }
        Header.printHeader(RunClusteringForAllPeriods.class, args);
        run(args);
        Header.printFooter();
    }

    static void run(String[] args){
        String path = args[0];
        path += path.endsWith("/") ? "" : "/";
        String shapefile = args[1];
        String shapefileField = args[2];
        String threads = args[3];

        List<String> dates = ClusterUtils.getLongitudinalMonths();
        for(String date : dates){
            LOG.info("==== Processing " + date);
            String folder = path + date + "/";
            String[] clusterArgs = new String[]{
                    folder + ExtractionUtils.FILENAME_VEHICLES,
                    shapefile,
                    shapefileField,
                    threads,
                    folder
            };
            DigicoreClusterRunner.main(clusterArgs);
        }
    }
}
