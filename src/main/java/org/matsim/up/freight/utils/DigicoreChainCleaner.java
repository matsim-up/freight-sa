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

package org.matsim.up.freight.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.Counter;
import org.matsim.up.freight.containers.*;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.freight.io.DigicoreVehiclesWriter;
import org.matsim.up.utils.Header;


/**
 * Class to read the {@link DigicoreVehicles} and adapt each vehicle's activity 
 * chains ({@link DigicoreChain}s) by merging all consecutive activities 
 * ({@link DigicoreActivity}) that occur at the same facility, i.e. having the 
 * same facility {@link Id}. The output is written to a new (given) 
 * {@link DigicoreVehicles} container.
 * 
 * <br><br><b>Note:</b> Vehicle activities will only be associated with facilities
 * if activities were clustered using an implementation of the
 * {@link org.matsim.up.freight.clustering.DJCluster} class.
 *
 * @author jwjoubert
 */
public class DigicoreChainCleaner {
	final private static Logger LOG = Logger.getLogger(DigicoreChainCleaner.class);

	/**
	 * @param args compulsory arguments in the following order:
	 *             <ol>
	 *             <li>input {@link DigicoreVehicles} file;</li>
	 *             <li>number of threads; and</li>
	 *             <li>output {@link DigicoreVehicles} file</li>
	 *             </ol>
	 */
	public static void main(String[] args) {
		Header.printHeader(DigicoreChainCleaner.class, args);
		String inputVehiclesFile = args[0];
		int numberOfThreads = Integer.parseInt(args[1]);		
		String outputVehiclesFile = args[2];

		/* Read the vehicles container. */
		DigicoreVehicles vehicles = new DigicoreVehicles();
		new DigicoreVehiclesReader(vehicles).readFile(inputVehiclesFile);
		
		/* Execute the multi-threaded jobs */
		ExecutorService threadExecutor = Executors.newFixedThreadPool(numberOfThreads);
		List<Future<DigicoreVehicle>> listOfJobs = new ArrayList<>(vehicles.getVehicles().size());
		Counter threadCounter = new Counter("   vehicles completed: ");
		
		for(DigicoreVehicle vehicle : vehicles.getVehicles().values()){
			Callable<DigicoreVehicle> job = new CallableChainCleaner(vehicle, threadCounter);
			Future<DigicoreVehicle> submit = threadExecutor.submit(job);
			listOfJobs.add(submit);
		}
		
		threadExecutor.shutdown();
		while(!threadExecutor.isTerminated()){
		}
		threadCounter.printCounter();

		/* Consolidate output. */
		LOG.info("Consolidate cleaned vehicles into single DigicoreVehicles container.");
		DigicoreVehicles newVehicles = new DigicoreVehicles(vehicles.getCoordinateReferenceSystem());
		String oldDescription = vehicles.getDescription();
		if(oldDescription == null){
			oldDescription = "";
		} else{
			oldDescription += oldDescription.endsWith(".") ? " " : ". ";
		}
		oldDescription += "Cleaned consecutive activities at same facility.";
		newVehicles.setDescription(oldDescription);
		
		for(Future<DigicoreVehicle> future : listOfJobs){
			DigicoreVehicle vehicle;
			try {
				vehicle = future.get();
				if(vehicle != null){
					newVehicles.addDigicoreVehicle(vehicle);
				}
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				throw new RuntimeException("Could not get DigicoreVehicle after multithreaded run.");
			}
		}
		
		new DigicoreVehiclesWriter(newVehicles).write(outputVehiclesFile);
		
		Header.printFooter();
	}
	
	
	public static class CallableChainCleaner implements Callable<DigicoreVehicle> {
		private final DigicoreVehicle vehicle;
		private final Counter counter;
		private int numberOfActivitiesChanged = 0;
		
		public CallableChainCleaner(DigicoreVehicle vehicle, Counter threadCounter) {
			this.vehicle = vehicle;
			this.counter = threadCounter;
		}

		@Override
		public DigicoreVehicle call() throws Exception {
			int chainIndex = 0;
			while(chainIndex < vehicle.getChains().size()){
				DigicoreChain chain = vehicle.getChains().get(chainIndex);
				
				cleanChain(chain);
				
				if(chain.size() < 2){
					/* It no longer is a valid chain, remove it. */
					vehicle.getChains().remove(chainIndex);
				} else{
					/* Move to the next chain. */
					chainIndex++;
				}
			}
			
			/* Since activities have been merged within chains, we have to 
			 * consolidate the 'major' activities BETWEEN consecutive chains. */
			consolidateBetweenChains();
			
			counter.incCounter();

			/* Write the vehicle to file, if it has at least one chain. */
			if(vehicle.getChains().size() > 0){
				LOG.info("   ==> " + vehicle.getId().toString() + " -> " + numberOfActivitiesChanged);
				return vehicle;
			} else{
				return null;
			}
		}

		/**
		 * This method has been revised in January 2017 to also account for 
		 * the {@link DigicoreTrace}s that are between two consecutive
		 * {@link DigicoreActivity}s.
		 * 
		 * @param chain to be cleaned.
		 */
		public void cleanChain(DigicoreChain chain){
			int activityIndex = 0;
			while(activityIndex < ((List<DigicoreChainElement>) chain).size()-1){
				DigicoreActivity thisActivity = null;
				if(((List<DigicoreChainElement>) chain).get(activityIndex) instanceof DigicoreActivity){
					thisActivity = (DigicoreActivity) ((List<DigicoreChainElement>) chain).get(activityIndex);
				}
				
				DigicoreActivity nextActivity = null;
				if(((List<DigicoreChainElement>) chain).get(activityIndex+2) instanceof DigicoreActivity){
					nextActivity = (DigicoreActivity) ((List<DigicoreChainElement>) chain).get(activityIndex+2);
				}

				assert thisActivity != null;
				if( thisActivity.getFacilityId() != null &&
						Objects.requireNonNull(nextActivity).getFacilityId() != null &&
						thisActivity.getFacilityId().toString().equalsIgnoreCase(nextActivity.getFacilityId().toString()) ){
					/* Merge the two activities. */
					numberOfActivitiesChanged++;
					thisActivity.setEndTime( nextActivity.getEndTime().seconds() );
					chain.remove( activityIndex + 1 ); /* Remove the trace. */
					chain.remove( activityIndex + 1 ); /* Remove the subsequent activity. */
					
					/* If one of the two activities is a 'major' type, then the 
					 * joint, merged activity should be major too. */
					if(thisActivity.getType().equalsIgnoreCase("major") ||
							nextActivity.getType().equalsIgnoreCase("major")){
						thisActivity.setType("major");
					}
				} else{
					/* Step over the current activity and the subsequent trace. */
					activityIndex += 2;
				}
			}
		}
		
		public void consolidateBetweenChains(){
			for(int i=0; i < this.vehicle.getChains().size()-1; i++){
				/* Get the two chains. */
				DigicoreChain thisChain = this.vehicle.getChains().get(i);
				DigicoreChain nextChain = this.vehicle.getChains().get(i+1);
				
				/* Get their respective 'major' activities to be compared. */
				DigicoreActivity thisLastMajor = thisChain.getLastMajorActivity();
				DigicoreActivity nextFirstMajor = nextChain.getFirstMajorActivity();
				
				/* Get the maximum extent. */
				double earliestStart = Math.min(
						thisLastMajor.getStartTime().seconds(),
						nextFirstMajor.getStartTime().seconds());
				double latestEnd = Math.min(
						thisLastMajor.getEndTime().seconds(),
						nextFirstMajor.getEndTime().seconds());
				
				/* Adjust the timing. */
				thisLastMajor.setStartTime(earliestStart);
				nextFirstMajor.setStartTime(earliestStart);
				thisLastMajor.setEndTime(latestEnd);
				nextFirstMajor.setEndTime(latestEnd);
			}
		}
	} 

}
