/* *********************************************************************** *
 * project: org.matsim.*
 * MyDigicoreStatusReader.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Class to read vehicle status codes for the <i>Digicore</i> records. 
 * @author jwjoubert
 */
class DigicoreStatusReader {
	private final Logger log = Logger.getLogger(DigicoreStatusReader.class);
	private final List<String> startSignals;
	private final List<String> stopSignals;
	
	/**
	 * Instantiates the {@link DigicoreStatusReader}. The user should not get 
	 * confused: these are <i>vehicle</i> stop signals, i.e. a vehicle stop 
	 * signal might be something like <i>`ignition off'</i>. The two-line, 
	 * comma-separated file format of the file must be as shown in the example 
	 * below, and the status codes are only allowed to be <i>integer</i> values.</p>
	 * 		<p><i>Example:</i>
	 * 		<ul><code>
	 * 			Start,2,3,4,5,16,18,20<br>
	 * 			Stop,1,6,7,8,9,10,11,12<br>
	 *  	</code></ul>
	 * Only the first two lines of the signal file will be read. Ensure thus that the 
	 * first line of the file is not blank.
	 * @param filename absolute path of the file containing the vehicle statuses.
	 * @throws FileNotFoundException when the status file is not available.
	 */
	DigicoreStatusReader(String filename) throws FileNotFoundException {
		startSignals = new ArrayList<>();
		stopSignals = new ArrayList<>();
		this.readSignals(filename);
	}
	
	
	private void readSignals(String string) throws FileNotFoundException{
		log.info("Reading vehicle start and stop signals from " + string);
		try (Scanner input = new Scanner(new BufferedReader(new FileReader(string)))) {
			String[] listStart = input.nextLine().split(",");
			if (listStart[0].equalsIgnoreCase("start")) {
				startSignals.addAll(Arrays.asList(listStart).subList(1, listStart.length));
				if (startSignals.size() == 0) {
					log.warn("No start signals were identified!");
				}
			} else {
				log.error("The first line of the signal file does not start with 'Start'");
				throw new RuntimeException("The signal file is in the wrong format!");
			}
			String[] listStop = input.nextLine().split(",");
			if (listStop[0].equalsIgnoreCase("stop")) {
				stopSignals.addAll(Arrays.asList(listStop).subList(1, listStop.length));
				if (stopSignals.size() == 0) {
					log.warn("No stop signals were identified!");
				}
			} else {
				log.error("The second line of the signal file does not start with 'Stop'");
				throw new RuntimeException("The signal file is in the wrong format!");
			}
			log.info("Done reading signals.");
		}
	}


	public List<String> getStartSignals() {
		return startSignals;
	}

	public List<String> getStopSignals() {
		return stopSignals;
	}

}

