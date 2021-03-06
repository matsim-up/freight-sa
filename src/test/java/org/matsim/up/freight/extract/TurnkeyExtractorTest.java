/* *********************************************************************** *
 * project: org.matsim.*
 * TurnkeyExtractorTest.java
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

package org.matsim.up.freight.extract;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.vehicles.Vehicle;

import java.io.File;


/**
 * Integration test to ensure the entire activity chain extraction remains
 * consistent and 'correct' (Can I say that with certainty?!).
 * 
 * @author jwjoubert
 */
public class TurnkeyExtractorTest {
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void testInputFile(){
		File f1 = new File(utils.getClassInputDirectory() + "/test.csv.gz");
		if(!f1.exists()){
			Assert.fail("Input file 'test' doesn't exist: " + f1.getAbsolutePath());
		}
	}
	
	@Test
	public void testStatusFile(){
		File file = new File(utils.getClassInputDirectory() + "/status.csv");
		if(!file.exists()){
			Assert.fail("Input file doesn't exist: " + file.getAbsolutePath());
		}
	}
	
	@Test
	public void testIntegrationExtraction(){
		
		String[] args = getTestArguments();
		try{
			TurnkeyExtractor.extract(args[0], args[1], args[2], args[3]);
		} catch(Exception e){
			e.printStackTrace();
			Assert.fail("Should extract without exceptions.");
		}
		
		/* Check that the output file exist. */
		File f = new File(utils.getOutputDirectory() + ExtractionUtils.FILENAME_VEHICLES);
		Assert.assertTrue("Vehicles container file should exist.", f.exists());
		
		/* Check that the remaining folders DO NOT exist. */
		File xmlFolder = new File(utils.getOutputDirectory() + ExtractionUtils.FOLDER_XML);
		Assert.assertFalse("Xml directory should not exist.", xmlFolder.exists());
		File vehiclesFolder = new File(utils.getOutputDirectory() + ExtractionUtils.FOLDER_VEHICLES);
		Assert.assertFalse("Vehicles directory should not exist.", vehiclesFolder.exists());
		
		/* Check the vehicles container. */
		DigicoreVehicles vehicles = new DigicoreVehicles();
		new DigicoreVehiclesReader(vehicles).readFile(utils.getOutputDirectory() + ExtractionUtils.FILENAME_VEHICLES);
		Id<Vehicle> vid = Id.createVehicleId("14114");
		Assert.assertTrue("Vehicle 14114 not found.", vehicles.getVehicles().containsKey(vid));
		Assert.assertEquals("Wrong number of vehicles.", 1L, vehicles.getVehicles().size());
		
		/* Check the actual vehicle's activity chains. */
		DigicoreVehicle vehicle = vehicles.getVehicles().get(vid);
		Assert.assertEquals("Wrong number of chains.", 5L, vehicle.getChains().size());
		
	}
	
	
	private String[] getTestArguments(){
		return new String[]{
				utils.getClassInputDirectory() + "/test.csv.gz",
				utils.getOutputDirectory(),
				"Integration test",
				utils.getClassInputDirectory() + "/status.csv"
		};
	}
	
	
	

}
