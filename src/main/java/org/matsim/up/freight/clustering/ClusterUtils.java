/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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
package org.matsim.up.freight.clustering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClusterUtils {
	public static final String ATTR_CONCAVE_HULL = "concaveHull";
	public static final String ATTR_DIGICORE_ACTIVITY_COUNT = "digicoreActivityCount";
	public static final String SUFFIX_FILENAME_FACILITIES_XML = "facilities.xml.gz";
	public static final String SUFFIX_FILENAME_FACILITIES_CSV = "facilities.csv.gz";
	public static final String SUFFIX_FACILITY_FOLDER = "facilityPoints/";

	/**
	 * Gets all the months that is available in the longitudinal data set from
	 * Digicore.
	 * @return A list of dates, as {@link String}s, in the format yyyymm. For
	 * example, "201002" for February 2010.
	 */
	public static List<String> getLongitudinalMonths(){
		List<String> dates = new ArrayList<>();
		for(int year = 2010; year < 2014; year++){
			for(int month = 1; month <= 12; month++){
				dates.add(String.format(Locale.US, "%d%02d", year, month));
			}
		}
		dates.add("201401");
		dates.add("201402");
		dates.add("201403");
		dates.add("201404");
		dates.add("201405");
		return dates;
	}


}
