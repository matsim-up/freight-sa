/* *********************************************************************** *
 * project: org.matsim.*
 * DigicoreVehicleReader.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package org.matsim.up.freight.io;

import java.util.Stack;

import org.apache.log4j.Logger;
import org.matsim.core.utils.io.MatsimXmlParser;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.xml.sax.Attributes;


public class DigicoreVehicleReader extends MatsimXmlParser {
	private final static String DIGICORE_VEHICLE_V1 = "digicoreVehicle_v1.dtd";
	private final static String DIGICORE_VEHICLE_V2 = "digicoreVehicle_v2.dtd";
	private final static Logger LOG = Logger.getLogger(DigicoreVehicleReader.class);
	private MatsimXmlParser delegate = null;
	private DigicoreVehicle vehicle = null;

	
	/**
	 * Creates a new reader for Digicore vehicle files.
	 */
	public DigicoreVehicleReader() {
	}
	
	/**
	 * Rather use the delegated {@link #getVehicle()} method.
	 * @param filename
	 * @return
	 */
	@Deprecated
	public DigicoreVehicle parseDigicoreVehicle(String filename){
		delegate.readFile(filename);
		return this.vehicle;
	}
	
	@Override
	public void startTag(String name, Attributes atts, Stack<String> context) {
		this.delegate.startTag(name, atts, context);
	}

	
	@Override
	public void endTag(String name, String content, Stack<String> context) {
		this.delegate.endTag(name, content, context);
	}

	
	
	@Override
	protected void setDoctype(final String doctype) {
		super.setDoctype(doctype);
		// Currently the only digicoreVehicle-type is v1
		if (DIGICORE_VEHICLE_V1.equals(doctype)) {
			this.delegate = new DigicoreVehicleReader_v1();
			LOG.info("using digicoreVehicle_v1 reader.");
		} else if(DIGICORE_VEHICLE_V2.equals(doctype)){
			this.delegate = new DigicoreVehicleReader_v2();
			LOG.info("using digicoreVehicle_v2 reader.");
		} else {
			throw new IllegalArgumentException("Doctype \"" + doctype + "\" not known.");
		}
	}
	
	
	/**
	 * @return the parsed {@link DigicoreVehicle} from the reader.
	 */
	public DigicoreVehicle getVehicle(){
		if(this.delegate instanceof DigicoreVehicleReader_v1){
			return ((DigicoreVehicleReader_v1)this.delegate).getVehicle();			
		} else if(this.delegate instanceof DigicoreVehicleReader_v2){
			return ((DigicoreVehicleReader_v2)this.delegate).getVehicle();			
		} else{
			throw new RuntimeException("Unknown instance type: " + this.delegate.getClass().toString());
		}
	}

}

