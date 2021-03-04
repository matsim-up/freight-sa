package org.matsim.up.freight.extract;

import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * Class that contains reused variables and functions for extracting activity
 * chain data from the Digicore trace files.
 *
 * @author jwjoubert
 */
class ExtractionUtils {
    final static String FOLDER_VEHICLES = "vehicles/";
    final static String FOLDER_XML = "xml/";
    final static String FILENAME_VEHICLES = "digicoreVehicles.xml.gz";

    final static String SORTED_HEADER_VEHICLE_ID = "vId";
    final static String SORTED_HEADER_TIME = "time";
    final static String SORTED_HEADER_LONGITUDE = "lon";
    final static String SORTED_HEADER_LATITUDE = "lat";
    final static String SORTED_HEADER_STATUS = "status";
    final static String SORTED_HEADER_SPEED = "speed";

    final static String INPUT_CRS = TransformationFactory.WGS84;
}
