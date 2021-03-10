package org.matsim.up.freight.clustering.postclustering;

import org.apache.log4j.Logger;
import org.matsim.up.freight.clustering.ClusterUtils;
import org.matsim.up.freight.extract.ExtractionUtils;
import org.matsim.up.utils.Header;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Single class to execute the {@link FacilityToActivityAssigner} for all months
 * in the Digicore longitudinal data set.
 *
 * @author jwjoubert
 */
class RunFacilityToActivityAssignerForAllPeriods {
    final private static Logger LOG = Logger.getLogger(RunFacilityToActivityAssignerForAllPeriods.class);
    final private static String DEFAULT_FOLDER = "/data/digicore/longitudinal/processed/";
    final private static String DEFAULT_SHAPEFILE = "./input/shapefiles/southAfrica/SouthAfrica_H94Lo29_NE.shp";
    final private static String DEFAULT_SHAPEFILE_ID = "1";
    final private static String DEFAULT_THREADS = "22";
    final private static String DEFAULT_RADIUS = "11";
    final private static String DEFAULT_P_MIN = "17";

    /**
     * Optional arguments
     *
     * @param args in the following order:
     *             <ol>
     *             <li>folder where the different time periods' folders can be found;</li>
     *             <li>shapefile that is used by the clustering to determine the extent of the study area;</li>
     *             <li>the field number in the shapefile indicating the unique object number/name; and</li>
     *             <li>the number of threads to use for each clustering sessions.</li>
     *             </ol>
     *             <p>
     *             If no arguments are passed, the defaults represent the data
     *             as it resides on both Hobbes servers.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            LOG.warn("Insufficient arguments. Reverting to defaults.");
            args = new String[]{
                    DEFAULT_FOLDER,
                    DEFAULT_SHAPEFILE,
                    DEFAULT_SHAPEFILE_ID,
                    DEFAULT_THREADS,
                    DEFAULT_RADIUS,
                    DEFAULT_P_MIN};
        }
        Header.printHeader(RunFacilityToActivityAssignerForAllPeriods.class, args);
        run(args);
        Header.printFooter();
    }

    static void run(String[] args) {
        String path = args[0];
        path += path.endsWith("/") ? "" : "/";
        String shapefile = args[1];
        String shapefileField = args[2];
        String threads = args[3];
        String radius = args[4];
        String pmin = args[5];

        List<String> dates = ClusterUtils.getLongitudinalMonths();
        for (String date : dates) {
            LOG.info("==== Processing " + date);
            String folder = path + date + "/";
            String[] clusterArgs = new String[]{
                    folder + ExtractionUtils.FILENAME_VEHICLES,
                    String.format(Locale.US, "%s%s_%s/%s_%s_%s", folder, radius, pmin, radius, pmin, ClusterUtils.SUFFIX_FILENAME_FACILITIES_XML),
                    threads,
                    shapefile,
                    shapefileField,
                    String.format(Locale.US, "%s%s_%s/%s_%s_%s", folder, radius, pmin, radius, pmin, ExtractionUtils.FILENAME_VEHICLES),
            };
            try {
                FacilityToActivityAssigner.main(clusterArgs);
            } catch (IOException e) {
                e.printStackTrace();
                LOG.error("Did not complete the assignment for '" + date + "'");
            }
        }
    }
}
