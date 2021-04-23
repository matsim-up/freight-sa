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

import java.util.*;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.misc.Counter;
import org.matsim.up.freight.containers.DigicoreActivity;
import org.matsim.up.freight.containers.DigicoreChain;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;

public class PathDependentNetwork {
    private final Logger LOG = Logger.getLogger(PathDependentNetwork.class);
    private final static int RESAMPLING_LIMIT = 10000;
    private final Map<Id<Node>, PathDependentNode> network;
    private Double totalSourceWeight = null;
    private final Random random;
    private String description = null;
    private long buildStartTime;
    private long buildEndTime;

    private static int sinkOnly = 0;
    private static int noSink = 0;
    private static int totalNextNodesSampled = 0;


    /**
     * Instantiating a path-dependent network for the Digicore data. This
     * constructor creates its own {@link Random} object for sampling. If you
     * require a deterministic outcome, rather use
     * {@link PathDependentNetwork#PathDependentNetwork(long))}.
     */
    public PathDependentNetwork() {
        this(MatsimRandom.getRandom().nextLong());
    }


    /**
     * Instantiating a path-dependent network for the Digicore data. This
     * constructor requires a seed value, and is mainly used for test purposes,
     * or when you require some deterministic outcome. When using this class in
     * applications, also consider using {@link PathDependentNetwork#PathDependentNetwork()}.
     *
     * @param seed a random seed value to make the sampling repeatable.
     */
    public PathDependentNetwork(long seed) {
        MatsimRandom.reset(seed);
        this.random = MatsimRandom.getRandom();
        this.network = new TreeMap<>();
    }


    public Map<Id<Node>, PathDependentNode> getPathDependentNodes() {
        return this.network;
    }

    public void setDescription(String string) {
        this.description = string;
    }

    public String getDescription() {
        return this.description;
    }


    /**
     * Method (only) used for test purposes.
     */
    Random getRandom() {
        return this.random;
    }


    public void processActivityChain(DigicoreChain chain) {
        List<DigicoreActivity> activities = chain.getAllActivities();

        /* Elements of the chain that we want to retain with the specific
         * source node. This is usable when we sample an activity chain later
         * from the path-dependent complex network. */
        DigicoreActivity firstMajor = activities.get(0);
        int startHour = firstMajor.getEndTimeGregorianCalendar().get(Calendar.HOUR_OF_DAY);
        int numberOfActivities = chain.getMinorActivities().size();


        /* Process the first activity pair, but only if both activities are
         * associated with a facility. */
        Id<Node> previousNodeId;
        Id<Node> currentNodeId = Id.createNodeId(activities.get(0).getFacilityId());
        Id<Node> nextNodeId = Id.createNodeId(activities.get(1).getFacilityId());

        if (currentNodeId != null) {//FIXME && nextNodeId != null) {
            /* Add the current node to the network if it doesn't already exist. */
            if (!this.network.containsKey(currentNodeId)) {
                this.network.put(currentNodeId, new PathDependentNode(currentNodeId, activities.get(0).getCoord()));
            }
            PathDependentNode currentNode = this.network.get(currentNodeId);

            /* Add the next node to the network if it doesn't already exist. */
            if (nextNodeId != null) {
                if (!this.network.containsKey(nextNodeId)) {
                    this.network.put(nextNodeId, new PathDependentNode(nextNodeId, activities.get(1).getCoord()));
                }

                /* Ensure that the next node reflects the fact that it can be
                 * reached from the currentNode. */
                this.network.get(nextNodeId).establishPathDependence(currentNodeId);
            } else {
                nextNodeId = Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN);
            }

            /* Add the first path-dependent link to the network. */
            currentNode.addSourceLink(startHour, numberOfActivities, nextNodeId);
        }
        previousNodeId = currentNodeId == null ? Id.create(ComplexNetworkUtils.NAME_UNKNOWN, Node.class) : currentNodeId;

        /* Process the remainder of the (minor) activity pairs. */
        for (int i = 1; i < activities.size() - 1; i++) {
            currentNodeId = Id.create(activities.get(i).getFacilityId(), Node.class);
            nextNodeId = Id.create(activities.get(i + 1).getFacilityId(), Node.class);

            if (currentNodeId != null) {//FIXME && nextNodeId != null) {
                /* Add the current node to the network if it doesn't already exist. */
                if (!this.network.containsKey(currentNodeId)) {
                    this.network.put(currentNodeId, new PathDependentNode(currentNodeId, activities.get(i).getCoord()));
                }
                PathDependentNode currentNode = this.network.get(currentNodeId);

                /* Add the next node to the network if it doesn't already exist. */
                if (nextNodeId != null) {
                    if (!this.network.containsKey(nextNodeId)) {
                        this.network.put(nextNodeId, new PathDependentNode(nextNodeId, activities.get(i + 1).getCoord()));
                    }

                    /* Ensure that the next node reflects the fact that it can be
                     * reached from the currentNode. */
                    this.network.get(nextNodeId).establishPathDependence(currentNodeId);
                } else {
                    nextNodeId = Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN);
                }

                /* Add the path-dependent link to the network. */
                currentNode.addPathDependentLink(previousNodeId, nextNodeId);
            }
            previousNodeId = currentNodeId == null ? Id.create(ComplexNetworkUtils.NAME_UNKNOWN, Node.class) : currentNodeId;
        }

        /* Process the last activity pair. No link needs to be added, but we
         * just have to make sure it is indicated as sink. This is only
         * necessary if the node already exists in the network. If not, it means
         * it was never part of a link that was added to the network anyway. */
        currentNodeId = Id.create(activities.get(activities.size() - 1).getFacilityId(), Node.class);
        if (currentNodeId != null && this.network.containsKey(currentNodeId)) {
            this.network.get(currentNodeId).setAsSink(previousNodeId);
        }
    }


    public int getNumberOfNodes() {
        return this.network.size();
    }


    public int getNumberOfEdges() {
        int totalNumberOfEdges = 0;
        for (PathDependentNode node : this.network.values()) {
            totalNumberOfEdges += node.getOutDegree();
        }
        return totalNumberOfEdges;
    }


    public PathDependentNode getPathDependentNode(Id<Node> id) {
        return this.network.get(id);
    }


    public double getPathDependentWeight(Id<Node> previousId, Id<Node> currentId, Id<Node> nextId) {
        double d = 0.0;

        /* Sort out null Ids for sources and sinks. */
        if (previousId == null) previousId = Id.create(ComplexNetworkUtils.NAME_SOURCE, Node.class);
        if (nextId == null) nextId = Id.create(ComplexNetworkUtils.NAME_SINK, Node.class);
        if (currentId == null) {
            throw new IllegalArgumentException("Cannot have a 'null' Id for current node.");
        }

        if (this.network.containsKey(currentId)) {
            d = this.network.get(currentId).getPathDependentWeight(previousId, nextId);
        }

        return d;
    }

    public double getWeight(Id<Node> fromId, Id<Node> toId) {
        double d = 0.0;
        if (this.network.containsKey(fromId)) {
            d = this.network.get(fromId).getWeight(toId);
        }
        return d;
    }


    public void buildNetwork(DigicoreVehicles vehicles) {
        LOG.info("Building network... number of vehicle files to process: " + vehicles.getVehicles().size());
        Counter xmlCounter = new Counter("   vehicles completed: ");

        buildStartTime = System.currentTimeMillis();
        for (DigicoreVehicle vehicle : vehicles.getVehicles().values()) {
            /* Process vehicle's chains. */
            for (DigicoreChain dc : vehicle.getChains()) {
                this.processActivityChain(dc);
            }
            xmlCounter.incCounter();
        }

        xmlCounter.printCounter();
        buildEndTime = System.currentTimeMillis();

        writeNetworkStatisticsToConsole();
    }


    public void addNewPathDependentNode(Id<Node> id, Coord coord) {
        if (this.network.containsKey(id)) {
            LOG.warn("Could not add a new node " + id.toString() + " - it already exists.");
        } else {
            this.network.put(id, new PathDependentNode(id, coord));
        }
    }


    public void setPathDependentEdgeWeight(Id<Node> previousId, Id<Node> currentId, Id<Node> nextId, double weight) {
        if (!this.network.containsKey(currentId)) {
            LOG.error("Oops... there doesn't seem to be a node " + currentId.toString() + " yet.");
        }
        this.network.get(currentId).setPathDependentEdgeWeight(previousId, nextId, weight);
    }


    /**
     * <b><i>Warning:</i></b> This should only be used (directly) for tests!
     */
    public Id<Node> sampleChainStartNode(double randomValue) {
        Id<Node> id = null;

        /* Determine the total source weight, but only once for the network. */
        if (this.totalSourceWeight == null) {
            this.totalSourceWeight = 0.0;
            for (PathDependentNode node : this.network.values()) {
                this.totalSourceWeight += node.getSourceWeight();
            }
        }

        /* Given a random value, sample the next node. */
        double cumulativeWeight = 0.0;
        Iterator<PathDependentNode> iterator = this.network.values().iterator();
        while (id == null && iterator.hasNext()) {
            PathDependentNode node = iterator.next();
            cumulativeWeight += node.getSourceWeight();
            if (cumulativeWeight / this.totalSourceWeight >= randomValue) {
                id = node.getId();
            }
        }

        return id;
    }


    /**
     * Sample the seed node {@link Id} for the start of the chain.
     */
    public Id<Node> sampleChainStartNode() {
        Id<Node> node = null;
        int counter = 0;
        while (node == null) {
            node = sampleChainStartNode(random.nextDouble());
            if (node == null) {
                LOG.debug("Redrawing a random value to find a start node.");
                if (counter++ == RESAMPLING_LIMIT) {
                    LOG.warn("Redrawn a random value " + RESAMPLING_LIMIT + " times to find a start node.");
                    counter = 0;
                }
            }
        }
        return node;
    }

    /**
     * <b><i>Warning:</i></b> This should only be used (directly) for tests!
     *
     * @return an {@link Integer} array with the first entry being the hour
     * of the day, and the second entry the number of activities within that
     * hour.
     */
    Integer[] sampleChainAttributes(Id<Node> startNode, double randomValue) {
        PathDependentNode node = this.getPathDependentNode(startNode);

        Id<Node> sourceId = Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE);
        if (!node.getPathDependence().containsKey(sourceId)) {
            LOG.error("Cannot sample a chain's start hour from a node that is not considered a major activity.");
            throw new IllegalArgumentException("Illegal start node Id: " + startNode.toString());
        }

        /* Establish the number of times this node has been a source node, but
         * only once per node. */
        if (node.sourceCount == null) {
            node.sourceCount = 0.0;
            for (String s : node.startNodeMap.keySet()) {
                node.sourceCount += (double) node.startNodeMap.get(s);
            }
        }

        double cumulativeWeight = 0.0;
        Integer[] result = null;
        Iterator<String> iterator = node.startNodeMap.keySet().iterator();
        while (result == null && iterator.hasNext()) {
            String s = iterator.next();
            cumulativeWeight += node.startNodeMap.get(s);
            if ((cumulativeWeight / node.sourceCount) >= randomValue) {
                int hour = Integer.parseInt(s.split(",")[0]);
                int activities = Integer.parseInt(s.split(",")[1]);
                result = new Integer[2];
                result[0] = hour;
                result[1] = activities;
            }
        }

        return result;
    }


    /**
     *
     * @param startNode where the chain starts.
     */
    public Integer[] sampleChainStartHour(Id<Node> startNode) {
        return this.sampleChainAttributes(startNode, random.nextDouble());
    }


    public Id<Node> sampleBiasedNextPathDependentNode(Id<Node> previousNodeId, Id<Node> currentNodeId) {
        return sampleBiasedNextPathDependentNode(previousNodeId, currentNodeId, random.nextDouble());
    }


    /**
     * <b><i>Warning!!</i></b> This method is only meant for testing purposes.
     * You should use {@link #sampleBiasedNextPathDependentNode(Id, Id)}.
     * TODO Finish description.
     *
     * @param previousNodeId the preceding node;
     * @param currentNodeId  the current node;
     * @param randomValue    a randomly drawn value in the range [0,1].
     */
    public Id<Node> sampleBiasedNextPathDependentNode(Id<Node> previousNodeId, Id<Node> currentNodeId, double randomValue) {
        PathDependentNode currentNode = this.getPathDependentNode(currentNodeId);
        /* Remove the 'sink' and 'unknown' nodes from the choice set, and see
        if there are possible next nodes. */
        Map<Id<Node>, Double> map = currentNode.getPathDependentNextNodes(previousNodeId);
        Id<Node> sinkId = Id.createNodeId(ComplexNetworkUtils.NAME_SINK);
        Id<Node> unknownId = Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN);
        Map<Id<Node>, Double> choiceMap = new HashMap<>();
        for (Id<Node> id : map.keySet()) {
            if (!id.equals(sinkId) && !id.equals(unknownId)) {
                choiceMap.put(id, map.get(id));
            }
        }

        if (choiceMap.isEmpty()) {
            for (Id<Node> otherPrevious : currentNode.getPathDependence().keySet()) {
                map = currentNode.getPathDependentNextNodes(otherPrevious);
                for (Id<Node> possibleId : map.keySet()) {
                    /* Ignore 'sink' and 'unknown' as a next node. */
                    if (!possibleId.toString().equalsIgnoreCase(sinkId.toString()) &&
                            !possibleId.toString().equalsIgnoreCase(unknownId.toString())) {
                        PathDependentNode possibleNode = this.getPathDependentNode(possibleId);
                        double weight = possibleNode.getTotalSinkWeight();
                        if (weight > 0) {
                            choiceMap.put(possibleId, weight);
                        }
                    }
                }
            }
            sinkOnly++;
        }

        /* If this too fails, see if we can terminate the chain prematurely. */
        if (choiceMap.isEmpty()) {
//			throw new RuntimeException("Ooops! Cannot sample a next node!!");
            return null;
        }

        /* Determine the total weighted out-degree. */
        double total = 0.0;
        for (Id<Node> id : choiceMap.keySet()) {
            total += choiceMap.get(id);
        }

        /* Sample next node. */
        double cumulativeTotal = 0.0;
        Id<Node> nextId = null;
        Iterator<Id<Node>> iterator = choiceMap.keySet().iterator();

        while (nextId == null) {
            Id<Node> id = iterator.next();
            cumulativeTotal += choiceMap.get(id);
            if ((cumulativeTotal / total) >= randomValue) {
                nextId = id;
            }
        }

        totalNextNodesSampled++;
        return nextId;
    }


    public Id<Node> sampleEndOfChainNode(Id<Node> previousId, Id<Node> currentId) {
        return this.sampleEndOfChainNode(previousId, currentId, random.nextDouble());
    }


    /**
     * Should only be used directly for tests.
     */
    Id<Node> sampleEndOfChainNode(Id<Node> previousId, Id<Node> currentId, double randomValue) {
        PathDependentNode currentNode = this.getPathDependentNode(currentId);

        /* Only consider those nodes who have a 'sink' in their choice set. */

        /* Given the path dependence, check if there is a next node that,
         * in turn, will have a 'sink', i.e. end the chain. */
        Map<Id<Node>, Double> choiceMap = new HashMap<>();
        Map<Id<Node>, Double> map = currentNode.getPathDependentNextNodes(previousId);
        Id<Node> sinkId = Id.createNodeId(ComplexNetworkUtils.NAME_SINK);
        for (Id<Node> possibleId : map.keySet()) {
            /* Ignore 'sink' as a next node. */
            if (!possibleId.toString().equalsIgnoreCase(sinkId.toString())) {
                PathDependentNode possibleNode = this.getPathDependentNode(possibleId);
                Map<Id<Node>, Double> possibleMap = possibleNode.getNextNodes(currentId);
                if (possibleMap.containsKey(sinkId)) {
                    /* Yes, this can be a possible node to choose as it can end a chain. */
                    choiceMap.put(possibleId, possibleMap.get(sinkId));
                }
            }
        }

        /* If the first step was unsuccessful, ignore the path dependence, and
         * check if any next node can be a 'sink', irrespective of the previous
         * node in the path-dependence. */
        if (choiceMap.isEmpty()) {
            LOG.debug("Check if this is calculated correctly.");
            /* Find another approach to get a next node that can end a chain. */
            for (Id<Node> otherPrevious : currentNode.getPathDependence().keySet()) {
                map = currentNode.getPathDependentNextNodes(otherPrevious);
                for (Id<Node> possibleId : map.keySet()) {
                    /* Ignore 'sink' as a next node. */
                    if (!possibleId.toString().equalsIgnoreCase(sinkId.toString())) {
                        PathDependentNode possibleNode = this.getPathDependentNode(possibleId);
                        double weight = possibleNode.getTotalSinkWeight();
                        if (weight > 0) {
                            choiceMap.put(possibleId, weight);
                        }
                    }
                }
            }
            noSink++;
        }

        /* If this too fails, see if we can terminate the chain prematurely. */
        if (choiceMap.isEmpty()) {
//			throw new RuntimeException("Ooops! Cannot sample the end of an activity chain!!");
            return null;
        }


        double total = 0.0;
        for (Id<Node> id : choiceMap.keySet()) {
            total += choiceMap.get(id);
        }

        double cumulativeWeight = 0.0;
        Id<Node> nextNode = null;
        Iterator<Id<Node>> iterator = choiceMap.keySet().iterator();
        while (nextNode == null && iterator.hasNext()) {
            Id<Node> thisNode = iterator.next();
            cumulativeWeight += choiceMap.get(thisNode);
            if ((cumulativeWeight / total) >= randomValue) {
                nextNode = thisNode;
            }
        }

        totalNextNodesSampled++;
        return nextNode;
    }

    public double getSourceWeight(Id<Node> nodeId) {
        if (this.network.containsKey(nodeId)) {
            return this.getPathDependentNode(nodeId).getSourceWeight();
        } else {
            return 0.0;
        }
    }

    /**
     * Writing some basic graph statistics: the number of nodes and edges; the
     * density of the graph, and the time taken to build it. The time taken may
     * be zero if the network is just read in and not built from scratch.
     */
    public void writeNetworkStatisticsToConsole() {
        LOG.info("---------------------  Graph statistics  -------------------");
        LOG.info("     Number of vertices: " + this.getNumberOfNodes());
        LOG.info("         Number of arcs: " + this.getNumberOfEdges());
        LOG.info("            Density (%): " + String.format(Locale.US, "%01.6f", (this.getNumberOfEdges()) / Math.pow(getNumberOfNodes(), 2) * 100.0));
        LOG.info(" Network build time (s): " + String.format(Locale.US, "%.2f", ((double) this.buildEndTime - (double) this.buildStartTime) / 1000));
        LOG.info("------------------------------------------------------------");
    }


    /**
     * Class to aggregate the path-dependent network to a path-independent
     * network.
     * <p>
     * TODO I think this is now correct, but it must still be checked
     * (preferably using tests).
     *
     * @return a {@link Map} containing for each possible origin node another
     * {@link Map} with all possible destination nodes from that origin.
     */
    public Map<Id<Node>, Map<Id<Node>, Double>> getEdges() {
        Map<Id<Node>, Map<Id<Node>, Double>> edgeMap = new HashMap<>();

        Map<Id<Node>, PathDependentNode> nodes = this.getPathDependentNodes();
        for (PathDependentNode node : nodes.values()) {
            Map<Id<Node>, Double> thisMap = new HashMap<>();
            edgeMap.put(node.getId(), thisMap);

            Map<Id<Node>, Map<Id<Node>, Double>> pathDependence = node.getPathDependence();
            for (Id<Node> previous : pathDependence.keySet()) {
                Map<Id<Node>, Double> map = pathDependence.get(previous);
                for (Id<Node> next : map.keySet()) {
                    if (!next.equals(Id.createNodeId(ComplexNetworkUtils.NAME_SINK)) &
                            !next.equals(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN))) {
                        double weight = map.get(next);
                        if (!thisMap.containsKey(next)) {
                            thisMap.put(next, weight);
                        } else {
                            double oldValue = thisMap.get(next);
                            thisMap.put(next, oldValue + weight);
                        }
                    }
                }
            }
        }

        /* Now we should have all the edges, ignoring the path-dependence. */
        return edgeMap;
    }

    public void reportSamplingStatus() {
        LOG.info("Sampling status:");
        LOG.info("  |_ next nodes sampled: " + totalNextNodesSampled);
        LOG.info("  |_ revised next nodes: " + sinkOnly);
        LOG.info("  |_ revised sink nodes: " + noSink);
    }


    /**
     * Returns a {@link Collection} of {@link Id}s of all nodes connected to
     * the given node on the upstream side. That is, all nodes connecting into
     * the given node. Consequently the node itself, as source, is omitted.
     */
    @Deprecated
    public Collection<Id<Node>> getConnectedInNodeIds(Id<Node> node) {
        Collection<Id<Node>> nodes = new ArrayList<>();

        Id<Node> source = Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE);
        Id<Node> unknown = Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN);
        for (Id<Node> id : this.getPathDependentNode(node).getPathDependence().keySet()) {
            if (!id.equals(source) && !id.equals(unknown)) {
                nodes.add(id);
            }  // else there is a problem!
        }
        return nodes;
    }


    @Deprecated
    public Collection<Id<Node>> getConnectedOutNodeIds(Id<Node> node) {
        Collection<Id<Node>> nodes = new ArrayList<>();

        PathDependentNode thisNode = this.getPathDependentNode(node);

        Id<Node> sink = Id.createNodeId(ComplexNetworkUtils.NAME_SINK);
        Id<Node> unknown = Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN);
        /* Check all incoming nodes. */
        for (Id<Node> inId : thisNode.getPathDependence().keySet()) {
            for (Id<Node> outId : thisNode.getPathDependentNextNodes(inId).keySet()) {
                if (!outId.equals(sink) && !outId.equals(unknown)) {
                    nodes.add(outId);
                } // else there is a problem!
            }
        }

        return nodes;
    }


    public class PathDependentNode implements Identifiable<Node> {
        private final Id<Node> id;
        private final Coord coord;
        private Double sourceCount = null;
        private final Map<Id<Node>, Map<Id<Node>, Double>> pathDependence;
        private final Map<String, Integer> startNodeMap;

        public PathDependentNode(Id<Node> id, Coord coord) {
            this.id = id;
            this.coord = coord;
            this.pathDependence = new TreeMap<>();
            this.startNodeMap = new TreeMap<>();
        }

        @Override
        public Id<Node> getId() {
            return this.id;
        }

        public Coord getCoord() {
            return this.coord;
        }


        public void setAsSource(int startHour, int numberOfActivities) {
            String node = startHour + "," + numberOfActivities;
            if (!this.startNodeMap.containsKey(node)) {
                this.startNodeMap.put(node, 1);
            } else {
                int oldValue = this.startNodeMap.get(node);
                this.startNodeMap.put(node, oldValue + 1);
            }
        }


        public void setPathDependentEdgeWeight(Id<Node> previousId, Id<Node> nextId, double weight) {
            if (!pathDependence.containsKey(previousId)) {
                pathDependence.put(previousId, new TreeMap<>());
            }
            this.pathDependence.get(previousId).put(nextId, weight);
        }


        public void setAsSink(Id<Node> previousId) {
            Map<Id<Node>, Double> map;
            if (!pathDependence.containsKey(previousId)) {
                map = new TreeMap<>();
                pathDependence.put(previousId, map);
            } else {
                map = pathDependence.get(previousId);
            }

            Id<Node> sinkId = Id.create(ComplexNetworkUtils.NAME_SINK, Node.class);
            if (!map.containsKey(sinkId)) {
                map.put(sinkId, 1.0);
            } else {
                map.put(sinkId, map.get(sinkId) + 1.0);
            }
        }


        public void addSourceLink(int startHour, int numberOfActivities, Id<Node> nextNodeId) {
            this.setAsSource(startHour, numberOfActivities);
            Id<Node> previousNodeId = Id.create(ComplexNetworkUtils.NAME_SOURCE, Node.class);

            addPathDependentLink(previousNodeId, nextNodeId);
        }

        public void addPathDependentLink(Id<Node> previousNodeId, Id<Node> nextNodeId) {
            /* DEBUG Remove after problem sorted... why are there links from
             * a node to itself if the network has already been cleaned?! */
            if (this.getId().toString().equalsIgnoreCase(nextNodeId.toString())) {
                LOG.debug("Link from node to itself.");
            }

            /* Add the path-dependency if it doesn't exist yet. */
            Map<Id<Node>, Double> map;
            if (!pathDependence.containsKey(previousNodeId)) {
                map = new TreeMap<>();
                pathDependence.put(previousNodeId, map);
            } else {
                map = pathDependence.get(previousNodeId);
            }

            /* Increment the link weight. */
            if (!map.containsKey(nextNodeId)) {
                map.put(nextNodeId, 1.0);
            } else {
                map.put(nextNodeId, map.get(nextNodeId) + 1.0);
            }
        }


        @SuppressWarnings("unused")
        public Id<Node> sampleNextNode(Id<Node> pastNode) {
            //TODO Complete
            return null;
        }


        /**
         * This method is mainly used for testing purposes.
         *
         * @param pastNode previous node visited.
         * @return the {@link Map} of nodes that can be visited from the current
         * node given the previous node. The values in the map is the path-dependent
         * edge weight.
         */
        public Map<Id<Node>, Double> getNextNodes(Id<Node> pastNode) {
            return this.pathDependence.get(pastNode);
        }


        /**
         * Determining the node's (unweighted) in-degree. That is, the number of
         * other nodes that links into this node.
         */
        public int getInDegree() {
            int inDegree = 0;
            for (Id<Node> id : this.pathDependence.keySet()) {
                if (!id.equals(Id.create(ComplexNetworkUtils.NAME_SOURCE, Node.class)) && !id.equals(Id.create(ComplexNetworkUtils.NAME_UNKNOWN, Node.class))) {
                    inDegree++;
                }
            }
            return inDegree;
        }


        /**
         * Determining the node's (unweighted) out-degree. That is, the number of
         * other nodes that this node can link to. Path-dependence is <b>not</b>
         * taken into account. If you want path-dependent out-degree, rather use
         * {@link #getPathDependentOutDegree(Id)}.
         */
        public int getOutDegree() {
            List<Id<Node>> outNodes = new ArrayList<>();
            for (Id<Node> inId : pathDependence.keySet()) {
                for (Id<Node> outId : pathDependence.get(inId).keySet()) {
                    if (!outNodes.contains(outId)) {
                        outNodes.add(outId);
                    }
                }
            }
            outNodes.remove(Id.create(ComplexNetworkUtils.NAME_SINK, Node.class));
            outNodes.remove(Id.create(ComplexNetworkUtils.NAME_UNKNOWN, Node.class));

            return outNodes.size();
        }


        private void establishPathDependence(Id<Node> previousId) {
            if (!pathDependence.containsKey(previousId)) {
                pathDependence.put(previousId, new TreeMap<>());
            }
        }


        /**
         * Determining the node's (unweighted) out-degree. That is, the number of
         * other nodes that this node can link to. Path-dependence <b>is</b>
         * taken into account. If you want the overall out-degree, rather use
         * {@link #getOutDegree()}.
         */
        public int getPathDependentOutDegree(Id<Node> inId) {
            if (inId == null) inId = Id.create(ComplexNetworkUtils.NAME_SOURCE, Node.class);

            List<Id<Node>> outNodes = new ArrayList<>();
            for (Id<Node> outId : pathDependence.get(inId).keySet()) {
                if (!outNodes.contains(outId)) {
                    outNodes.add(outId);
                }
            }
            outNodes.remove(Id.create(ComplexNetworkUtils.NAME_SINK, Node.class));
            outNodes.remove(Id.create(ComplexNetworkUtils.NAME_UNKNOWN, Node.class));

            return outNodes.size();
        }

        public Map<Id<Node>, Double> getPathDependentNextNodes(Id<Node> previousId) {
            return this.pathDependence.get(previousId);
        }


        public Map<Id<Node>, Map<Id<Node>, Double>> getPathDependence() {
            return this.pathDependence;
        }


        /**
         * Calculates the path-dependent edge weight from the current node to
         * the given next node.
         *
         * @param previousId previous node visited;
         * @param nextId     next node visited;
         * @return weight of the link, given the path dependence, or zero if the
         * particular link does not exist.
         */
        private double getPathDependentWeight(Id<Node> previousId, Id<Node> nextId) {
            double weight = 0;
            if (pathDependence.containsKey(previousId)) {
                Map<Id<Node>, Double> map = pathDependence.get(previousId);
                if (map.containsKey(nextId)) {
                    weight = map.get(nextId);
                }
            }
            return weight;
        }

        private double getWeight(Id<Node> nextId) {
            double weight = 0.0;
            for (Map<Id<Node>, Double> map : pathDependence.values()) {
                if (map.containsKey(nextId)) {
                    weight += map.get(nextId);
                }
            }
            return weight;
        }


        private double getSourceWeight() {
            double weight = 0.0;
            Id<Node> sourceId = Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE);
            if (pathDependence.containsKey(sourceId)) {
                for (Id<Node> destinationId : pathDependence.get(sourceId).keySet()) {
                    if (!destinationId.toString().equals(ComplexNetworkUtils.NAME_UNKNOWN)) {
                        weight += pathDependence.get(sourceId).get(destinationId);
                    }
                }
            }
            return weight;
        }

        public Map<String, Integer> getStartNodeMap() {
            return this.startNodeMap;
        }


        public double getTotalSinkWeight() {
            double sinkWeight = 0.0;
            Id<Node> sink = Id.createNodeId(ComplexNetworkUtils.NAME_SINK);
            for (Id<Node> nodeId : this.pathDependence.keySet()) {
                Map<Id<Node>, Double> map = this.pathDependence.get(nodeId);
                if (map.containsKey(sink)) {
                    sinkWeight += map.get(sink);
                }
            }

            return sinkWeight;
        }

    }
}
