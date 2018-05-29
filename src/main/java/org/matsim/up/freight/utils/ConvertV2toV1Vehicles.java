/* *********************************************************************** *
 * project: org.matsim.*
 * ConvertV2toV1Vehicles.java
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

/**
 * 
 */
package org.matsim.up.freight.utils;

import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.freight.io.DigicoreVehiclesWriter;
import org.matsim.up.utils.Header;

/**
 * @author jwjoubert
 *
 */
public class ConvertV2toV1Vehicles {

	/**
	 * Class to read in a version 2 {@link DigicoreVehicles} container, and
	 * writing it out as a version 1 container (without the traces).
	 * @param args
	 */
	public static void main(String[] args) {
		Header.printHeader(ConvertV2toV1Vehicles.class, args);
		
		String inputContainer = args[0];
		String outputContainer = args[1];
		
		DigicoreVehicles dv = new DigicoreVehicles();
		new DigicoreVehiclesReader(dv).readFile(inputContainer);
		
		new DigicoreVehiclesWriter(dv).writeV1(outputContainer);
		
		Header.printFooter();
	}

}
