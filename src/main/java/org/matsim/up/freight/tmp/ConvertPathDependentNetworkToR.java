/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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
package org.matsim.up.freight.tmp;

import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.up.freight.algorithms.complexNetworks.ComplexNetworkUtils;
import org.matsim.up.freight.algorithms.complexNetworks.DigicorePathDependentNetworkReader_v2;
import org.matsim.up.utils.Header;

public class ConvertPathDependentNetworkToR {

	public static void main(String[] args) {
		Header.printHeader(ConvertPathDependentNetworkToR.class, args);
		String network = args[0];
		String output = args[1];

		DigicorePathDependentNetworkReader_v2 reader =
				new DigicorePathDependentNetworkReader_v2();
		reader.readFile(network);

		ComplexNetworkUtils.writeWeightedEdgelistToFile(reader.getPathDependentNetwork(), reader.getPathDependentNetwork().getEdges(), output, TransformationFactory.WGS84);

		Header.printFooter();
	}
}
