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

package org.matsim.up.freight.populationGeneration;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.up.freight.algorithms.complexNetworks.DigicorePathDependentNetworkReader_v2;
import org.matsim.up.freight.algorithms.complexNetworks.PathDependentNetwork;
import org.matsim.up.utils.Header;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * Generates a given number of freight activity chains using a sequence-
 * dependent complex network.
 * @author jwjoubert
 */
public class FreightChainGenerator {
	private final static Logger LOG = Logger.getLogger(FreightChainGenerator.class);
	private final static Random RANDOM = MatsimRandom.getRandom();
	private final static int MAX_CHAIN_ATTEMPTS = 10;
	private static final String SUB_POPULATION_NAME = "commercial";
	private static int TOTAL_CHAIN_RESTARTS = 0;

	@SuppressWarnings("unused")
	private final static Double AVERAGE_SPEED = 50.0 / 3.6;

	private PathDependentNetwork complexNetwork;
	private Scenario sc = ScenarioUtils.createScenario(ConfigUtils.createConfig());

	/**
	 * @param args arguments in the following sequence:
	 * <ol>
	 *    <li> absolute path of the (path-dependent) complex network file;</li>
	 *	  <li> the number of plans to generate; </li>
	 *	  <li> the {@link String} prefix to use for the population Ids;</li>
	 *	  <li> absolute path to the output population;</li>
	 *	  <li> the number of threads to use.</li>
	 * </ol>
	 */
	public static void main(String[] args) {
		Header.printHeader(FreightChainGenerator.class, args);

		/* Parse all the arguments. */
		String complexNetworkFile = args[0];
		int numberOfPlans = Integer.parseInt(args[1]);
		String populationPrefix = args[2];
		String outputPlansFile = args[3];
		int numberOfThreads = Integer.parseInt(args[4]);

		/* Read the path-dependent complex network. */
		DigicorePathDependentNetworkReader_v2 nr = new DigicorePathDependentNetworkReader_v2();
		nr.readFile(complexNetworkFile);
		PathDependentNetwork pathDependentNetwork = nr.getPathDependentNetwork();
		pathDependentNetwork.writeNetworkStatisticsToConsole();

		/* Set up the freight chain generator. */
		FreightChainGenerator fcg = new FreightChainGenerator(pathDependentNetwork);
		fcg.generateFreightAgentPlans(pathDependentNetwork, populationPrefix, numberOfPlans, numberOfThreads);

		/* Write the population to file. */
		new PopulationWriter(fcg.getScenario().getPopulation(), null).write(outputPlansFile);

		LOG.info("Total number of chain restarts: " + TOTAL_CHAIN_RESTARTS);

		Header.printFooter();
	}


	private FreightChainGenerator(PathDependentNetwork network) {
		this.complexNetwork = network;
	}


	private void generateFreightAgentPlans(PathDependentNetwork network, String prefix, int numberOfPlans, int numberOfThreads) {
		LOG.info("Creating a population of " + numberOfPlans + " freight activity chains...");

		/* Set up the multi-threaded infrastructure. */
		ExecutorService threadExecutor = Executors.newFixedThreadPool(numberOfThreads);
		List<Future<Plan>> listOfJobs = new ArrayList<>();

		LOG.info("Creating plans using multi-threaded infrastructure.");
		Counter counter = new Counter("   plans # ");
		for (int i = 0; i < numberOfPlans; i++) {
			DigicorePlanGenerator job = new DigicorePlanGenerator(this.complexNetwork, counter);
			Future<Plan> submit = threadExecutor.submit(job);
			listOfJobs.add(submit);
		}

		threadExecutor.shutdown();
		while (!threadExecutor.isTerminated()) {
//			try {
//				TimeUnit.SECONDS.sleep(5);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//				throw new RuntimeException("Terminated while waiting (5 seconds at a time) for multi-threading to finish.");
//			}
		}
		counter.printCounter();

		/* Aggregate the output plans. */
		PopulationFactory pf = sc.getPopulation().getFactory();
		try {
			int i = 0;
			for (Future<Plan> job : listOfJobs) {
				Person vehicle = pf.createPerson(Id.createPersonId(prefix + "_" + i++));
				Plan plan = job.get();

				/* Add the plan to the person, and the person to the population. */
				vehicle.addPlan(plan);
				sc.getPopulation().addPerson(vehicle);

				/* Indicate the subpopulation. */
				vehicle.getAttributes().putAttribute(sc.getConfig().plans().getSubpopulationAttributeName(), SUB_POPULATION_NAME);
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
			throw new RuntimeException("Could not aggregate multi-threaded plan output.");
		}
		network.reportSamplingStatus();
	}


	private Scenario getScenario() { return this.sc; }


	/**
	 * A private class to generate {@link Plan}s in a multi-threaded manner. 
	 *
	 * @author jwjoubert
	 */
	private class DigicorePlanGenerator implements Callable<Plan> {
		private final PathDependentNetwork network;
		private final Counter counter;
		private int chainAttempts = 0;

		/**
		 * @param network a path-dependent complex network as generated by the code
		 *          {@link PathDependentNetwork}.
		 */
		DigicorePlanGenerator(PathDependentNetwork network, Counter counter) {
			this.network = network;
			this.counter = counter;
		}

		@Override
		public Plan call() {
			PopulationFactory pf = sc.getPopulation().getFactory();

			/* Build a list of consecutive activities. */
			boolean validChain = false;
			int chainRestarts = 0;

			Integer[] chainAttributes = null;
			List<String> chainList = null;
			while (!validChain) {
				/* Generate the first activity. */
				Id<Node> source = Id.create("source", Node.class);
				Id<Node> firstNode = network.sampleChainStartNode();

				chainAttributes = network.sampleChainStartHour(firstNode);
				int numberOfActivities = chainAttributes[1];

				chainList = new ArrayList<>(numberOfActivities + 2);
				chainList.add(source + "," + firstNode);
				while (chainList.size() < (numberOfActivities + 1) && chainAttempts < MAX_CHAIN_ATTEMPTS) {
					buildChainList(chainList, network);

					/* It is (unfortunately) possible that the entire activity
					 * chain could have been removed. If that happens, the
					 * chain process should be restarted completely. This can
					 * be achieved by immediately setting the number of chain
					 * attempts to its maximum, forcing the chain to be started
					 * from scratch. */
					if (chainList.isEmpty()) {
						chainAttempts = MAX_CHAIN_ATTEMPTS;
					}
				}
				if (chainAttempts < MAX_CHAIN_ATTEMPTS) {
					/* Add the final major activity. */
					String[] sa = chainList.get(chainList.size() - 1).split(",");
					Id<Node> previousId = Id.createNodeId(sa[0]);
					Id<Node> currentId = Id.createNodeId(sa[1]);
					Id<Node> finalNode = network.sampleEndOfChainNode(previousId, currentId);
					if (finalNode != null) {
						validChain = true;
						chainList.add(currentId.toString() + "," + finalNode.toString());
					}
				}
				if (!validChain) {
					chainRestarts++;
					TOTAL_CHAIN_RESTARTS++;
					LOG.debug("  ===> Chain restarts: " + chainRestarts);
					chainAttempts = 0;
				}
			}

			/* Set up the plan. */
			Plan plan = pf.createPlan();

			/* Now add all the activities in the chain sequence. Start with the
			 * first 'major' activity. */
			Id<Node> firstId = Id.createNodeId(chainList.get(0).split(",")[1]);
			Coord coord = network.getPathDependentNode(firstId).getCoord();
			Activity firstActivity = PopulationUtils.createActivityFromCoord("major", coord);
			firstActivity.setFacilityId(Id.create(firstId.toString(), ActivityFacility.class));

			int startHour = chainAttributes[0];
			double endTime = Math.round((startHour + RANDOM.nextDouble()) * 3600); /* in seconds after midnight */
			firstActivity.setEndTime(endTime);
			plan.addActivity(firstActivity);

			/* Add the leg. */
			Leg firstLeg = pf.createLeg("commercial");
			plan.addLeg(firstLeg);

			/* Add all the 'minor' activities. */
			for (int i = 1; i < chainList.size() - 1; i++) {
				Id<Node> thisId = Id.createNodeId(chainList.get(i).split(",")[1]);

				Coord thisCoord = network.getPathDependentNode(thisId).getCoord();
				Activity activity = PopulationUtils.createActivityFromCoord("minor", thisCoord);
				activity.setFacilityId(Id.create(thisId.toString(), ActivityFacility.class));

				double duration = ActivityDuration.getDurationInSeconds(RANDOM.nextDouble());
				activity.setMaximumDuration(duration);
				plan.addActivity(activity);

				/* Add the leg. */
				Leg leg = pf.createLeg("commercial");
				plan.addLeg(leg);
			}

			/* Add the final 'major' activity. */
			Id<Node> finalId = Id.createNodeId(chainList.get(chainList.size() - 1).split(",")[1]);
			Coord finalCoord = network.getPathDependentNode(finalId).getCoord();
			Activity finalActivity = PopulationUtils.createActivityFromCoord("major", finalCoord);
			finalActivity.setFacilityId(Id.create(finalId, ActivityFacility.class));
			plan.addActivity(finalActivity);

			counter.incCounter();
			return plan;
		}

		private void buildChainList(List<String> list, PathDependentNetwork network) {
			/* Get the last entry's Ids. */
			String[] sa = list.get(list.size() - 1).split(",");
			Id<Node> previousNodeId = Id.createNodeId(sa[0]);
			Id<Node> currentNodeId = Id.createNodeId(sa[1]);
			Id<Node> nextNodeId = network.sampleBiasedNextPathDependentNode(previousNodeId, currentNodeId);
			if (nextNodeId == null) {
				int rowsToRemove = Math.max(1, RANDOM.nextInt((int) Math.round(0.25 * ((double) list.size()))));
				for (int i = 0; i < rowsToRemove; i++) {
					list.remove(list.size() - 1);
				}
				chainAttempts++;
			} else {
				String s = currentNodeId.toString() + "," + nextNodeId.toString();
				list.add(s);
			}
		}
	}


}
