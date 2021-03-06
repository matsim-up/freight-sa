/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,     *
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

package org.matsim.up.freight.algorithms.complexNetworks;

import org.apache.log4j.Logger;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.freight.utils.DigicoreChainCleaner;
import org.matsim.up.utils.Header;


/** Class to build a path dependent complex network by reading all 
 *  the clean {@link DigicoreVehicle}s generated by executing the class
 *  {@link DigicoreChainCleaner}. When building the {@link PathDependentNetwork},
 *  the next {@link PathDependentNetwork.PathDependentNode} is determined by taking into
 *  account the previous {@link PathDependentNetwork.PathDependentNode}s connected
 *  to the current {@link PathDependentNetwork.PathDependentNode}.
 * 
 * @author jwjoubert
 */
public class PathDependentNetworkBuilder {
	private final static Logger LOG = Logger.getLogger(PathDependentNetworkBuilder.class);
	private final PathDependentNetwork network;

	/**
	 * @param args compulsory arguments in the following order:
	 *             <ol>
	 *             <li>path to the "clean/" folder containing clean vehicle xml
	 *             files generated by executing the class {@link DigicoreChainCleaner};</li>
	 *             <li>path to the <code>pathDependentNetwork.xml.gz</code> file; and</li>
	 *             <li>description of the network, specifying the area that the network
	 * 		      covers (National/Gauteng/etc), and the clustering parameter
	 *  		  configuration (pmin=..., radius=...).</li>
	 *             </ol>
	 */
	public static void main(String[] args) {
		if(args.length == 0){
			LOG.error("There are no defaults to revert back to.");
		}
		Header.printHeader(PathDependentNetworkBuilder.class, args);
		
		String vehicleFile = args[0];
		String outputFile = args[1];
		String description = args[2];
		
		PathDependentNetworkBuilder builder = new PathDependentNetworkBuilder(MatsimRandom.getRandom().nextLong());
		builder.setNetworkDescription(description);
		
		/* Read the vehicle container. */
		DigicoreVehicles vehicles = new DigicoreVehicles();
		new DigicoreVehiclesReader(vehicles).readFile(vehicleFile);
		builder.network.buildNetwork(vehicles);
		
		new DigicorePathDependentNetworkWriter(builder.network).write(outputFile);
		
		Header.printFooter();
	}
	
	public PathDependentNetworkBuilder(long seed) {
		this.network = new PathDependentNetwork(seed);
	}
	
	public void setNetworkDescription(String string){
		this.network.setDescription(string);
	}
	

	


}
