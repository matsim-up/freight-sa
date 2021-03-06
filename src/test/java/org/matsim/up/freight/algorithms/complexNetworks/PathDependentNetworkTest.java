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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.facilities.ActivityFacility;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.up.freight.algorithms.complexNetworks.PathDependentNetwork.PathDependentNode;
import org.matsim.up.freight.containers.DigicoreActivity;
import org.matsim.up.freight.containers.DigicoreChain;

public class PathDependentNetworkTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void testSetupListOfChains() {
        List<DigicoreChain> chains = setupListOfChains();
        Assert.assertEquals("Wrong number of chains.", 5, chains.size());
        Assert.assertEquals("Wrong chain length - chain 1.", 3, chains.get(0).getAllActivities().size());
        Assert.assertEquals("Wrong chain length - chain 2.", 3, chains.get(1).getAllActivities().size());
        Assert.assertEquals("Wrong chain length - chain 3.", 3, chains.get(2).getAllActivities().size());
        Assert.assertEquals("Wrong chain length - chain 4.", 5, chains.get(3).getAllActivities().size());
        Assert.assertEquals("Wrong chain length - chain 5.", 3, chains.get(4).getAllActivities().size());
    }


    @Test
    public void testConstructorNoSeed() {
        PathDependentNetwork pdn = new PathDependentNetwork();
        Assert.assertNotNull("Should have a random number generator.", pdn.getRandom());
    }


    @Test
    public void testConstructorSeed() {
        long l = 12345;
        long next = new Random(l).nextLong();
        PathDependentNetwork pdn = new PathDependentNetwork(l);
        Assert.assertEquals("Wrong next random long value.", next, pdn.getRandom().nextLong());
    }


    @Test
    public void testProcessChain() {
        List<DigicoreChain> chains = setupListOfChains();

        PathDependentNetwork pdn = new PathDependentNetwork(12345);
        for (DigicoreChain chain : chains) {
            pdn.processActivityChain(chain);
        }

        Assert.assertEquals("Wrong number of nodes.", 5, pdn.getNumberOfNodes());

        /* Node 'A' */
        PathDependentNode A = pdn.getPathDependentNode(Id.create("A", Node.class));
        Assert.assertEquals("Wrong in-degree for 'A'.", 0, A.getInDegree());
        Assert.assertEquals("Wrong out-degree for 'A'.", 2, A.getOutDegree());
        Assert.assertEquals("Wrong path-dependent out-degree for 'A'.", 2, A.getPathDependentOutDegree(null));
        Assert.assertEquals("Wrong path-dependent out-degree for 'A'.", 2, A.getPathDependentOutDegree(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)));
        Assert.assertEquals("Wrong weight '(source) A -> C'", 2, pdn.getPathDependentWeight(null, Id.create("A", Node.class), Id.create("C", Node.class)), 0.001);
        Assert.assertEquals("Wrong weight '(from C) A -> C'", 0, pdn.getPathDependentWeight(Id.create("C", Node.class), Id.create("A", Node.class), Id.create("C", Node.class)), 0.001);
        Assert.assertEquals("Wrong weight '(source) A -> B'", 1, pdn.getPathDependentWeight(null, Id.create("A", Node.class), Id.create("B", Node.class)), 0.001);
        Assert.assertEquals("Wrong weight '(from B) A -> B'", 0, pdn.getPathDependentWeight(Id.create("B", Node.class), Id.create("A", Node.class), Id.create("B", Node.class)), 0.001);


        /* Node 'B' */
        PathDependentNode B = pdn.getPathDependentNode(Id.create("B", Node.class));
        Assert.assertEquals("Wrong in-degree for 'B'", 1, B.getInDegree());
        Assert.assertEquals("Wrong out-degree for 'B'.", 1, B.getOutDegree());
        Assert.assertEquals("Wrong path-dependent out-degree for 'B'.", 1, B.getPathDependentOutDegree(null));
        Assert.assertEquals("Wrong path-dependent out-degree for 'B'.", 1, B.getPathDependentOutDegree(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)));
        Assert.assertEquals("Wrong weight '(source) B -> C'", 1, pdn.getPathDependentWeight(null, Id.create("B", Node.class), Id.create("C", Node.class)), 0.001);
        Assert.assertEquals("Wrong weight '(from C) B -> C'", 0, pdn.getPathDependentWeight(Id.create("C", Node.class), Id.create("B", Node.class), Id.create("C", Node.class)), 0.001);

        /* Node 'C' */
        PathDependentNode C = pdn.getPathDependentNode(Id.create("C", Node.class));
        Assert.assertEquals("Wrong in-degree for 'C'.", 2, C.getInDegree());
        Assert.assertEquals("Wrong path-dependent ('A') out-degree for 'C'.", 2, C.getPathDependentOutDegree(A.getId()));
        Assert.assertEquals("Wrong path-dependent ('B') out-degree for 'C'.", 1, C.getPathDependentOutDegree(B.getId()));
        Assert.assertEquals("Wrong out-degree for 'C'.", 2, C.getOutDegree());
        Assert.assertEquals("Wrong weight '(A) C -> D'", 1, pdn.getPathDependentWeight(Id.create("A", Node.class), Id.create("C", Node.class), Id.create("D", Node.class)), 0.001);
        Assert.assertEquals("Wrong weight '(A) C -> E'", 1, pdn.getPathDependentWeight(Id.create("A", Node.class), Id.create("C", Node.class), Id.create("E", Node.class)), 0.001);
        Assert.assertEquals("Wrong weight '(B) C -> D'", 1, pdn.getPathDependentWeight(Id.create("B", Node.class), Id.create("C", Node.class), Id.create("D", Node.class)), 0.001);

        /* Node 'D' */
        PathDependentNode D = pdn.getPathDependentNode(Id.create("D", Node.class));
        Assert.assertEquals("Wrong in-degree for 'D'.", 1, D.getInDegree());
        Assert.assertEquals("Wrong out-degree for 'D'.", 0, D.getOutDegree());
        Assert.assertEquals("Wrong path-dependent ('C') out-degree for 'D'.", 0, D.getPathDependentOutDegree(Id.create("C", Node.class)));

        /* Node 'E' */
        PathDependentNode E = pdn.getPathDependentNode(Id.create("E", Node.class));
        Assert.assertEquals("Wrong in-degree for 'E'.", 1, E.getInDegree());
        Assert.assertEquals("Wrong out-degree for 'E'.", 0, E.getOutDegree());
        Assert.assertEquals("Wrong path-dependent ('C') out-degree for 'E'.", 0, E.getPathDependentOutDegree(Id.create("C", Node.class)));

        /* Test edge weights. */
        Assert.assertEquals("Wrong edge weight: A-B", 1, pdn.getWeight(Id.create("A", Node.class), Id.create("B", Node.class)), 0.001);
        Assert.assertEquals("Wrong edge weight: A-C", 2, pdn.getWeight(Id.create("A", Node.class), Id.create("C", Node.class)), 0.001);
        Assert.assertEquals("Wrong edge weight: B-C", 2, pdn.getWeight(Id.create("B", Node.class), Id.create("C", Node.class)), 0.001);
        Assert.assertEquals("Wrong edge weight: C-D", 2, pdn.getWeight(Id.create("C", Node.class), Id.create("D", Node.class)), 0.001);
        Assert.assertEquals("Wrong edge weight: C-E", 1, pdn.getWeight(Id.create("C", Node.class), Id.create("E", Node.class)), 0.001);
    }

    @Test
    public void testSampleBiasedNextPathDependentNode() {
        List<DigicoreChain> chains = setupListOfChains();

        PathDependentNetwork pdn = new PathDependentNetwork(12345);
        for (DigicoreChain chain : chains) {
            pdn.processActivityChain(chain);
        }

        PathDependentNode C = pdn.getPathDependentNode(Id.create("C", Node.class));
        Assert.assertEquals("Wrong next Id.", Id.create("D", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("A", Node.class), C.getId(), 0.25));
        Assert.assertEquals("Wrong next Id.", Id.create("E", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("A", Node.class), C.getId(), 0.75));

        Assert.assertEquals("Wrong next Id.", Id.create("D", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("B", Node.class), C.getId(), 0.1));
        Assert.assertEquals("Wrong next Id.", Id.create("D", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("B", Node.class), C.getId(), 0.25));
        Assert.assertEquals("Wrong next Id.", Id.create("D", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("B", Node.class), C.getId(), 0.5));
        Assert.assertEquals("Wrong next Id.", Id.create("D", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("B", Node.class), C.getId(), 0.75));
        Assert.assertEquals("Wrong next Id.", Id.create("D", Node.class), pdn.sampleBiasedNextPathDependentNode(Id.create("B", Node.class), C.getId(), 0.9));

        PathDependentNode D = pdn.getPathDependentNode(Id.create("D", Node.class));
        Assert.assertNull("Wrong next Id.", pdn.sampleBiasedNextPathDependentNode(Id.create("C", Node.class), D.getId(), 0.5));

        PathDependentNode E = pdn.getPathDependentNode(Id.create("E", Node.class));
        Assert.assertNull("Wrong next Id.", pdn.sampleBiasedNextPathDependentNode(Id.create("C", Node.class), E.getId(), 0.5));
    }

    @Test
    public void testGetSourceWeight() {
        List<DigicoreChain> chains = setupListOfChains();

        PathDependentNetwork pdn = new PathDependentNetwork(12345);
        for (DigicoreChain chain : chains) {
            pdn.processActivityChain(chain);
        }

        Assert.assertEquals("Wrong source weight for 'A'.", 3, pdn.getSourceWeight(Id.create("A", Node.class)), 0.001);
        Assert.assertEquals("Wrong source weight for 'B'.", 1, pdn.getSourceWeight(Id.create("B", Node.class)), 0.001);
        Assert.assertEquals("Wrong source weight for 'C'.", 0, pdn.getSourceWeight(Id.create("C", Node.class)), 0.001);
        Assert.assertEquals("Wrong source weight for 'D'.", 0, pdn.getSourceWeight(Id.create("D", Node.class)), 0.001);
        Assert.assertEquals("Wrong source weight for 'E'.", 0, pdn.getSourceWeight(Id.create("E", Node.class)), 0.001);
    }

    @Test
    public void testSampleChainStartNode() {
        List<DigicoreChain> chains = setupListOfChains();

        PathDependentNetwork pdn = new PathDependentNetwork(12345);
        for (DigicoreChain chain : chains) {
            pdn.processActivityChain(chain);
        }

        Assert.assertEquals("Wrong source node: ratio A:B should be 3:1.", Id.create("A", Node.class), pdn.sampleChainStartNode(0.25));
        Assert.assertEquals("Wrong source node: ratio A:B should be 3:1.", Id.create("A", Node.class), pdn.sampleChainStartNode(0.5));
        Assert.assertEquals("Wrong source node: ratio A:B should be 3:1.", Id.create("A", Node.class), pdn.sampleChainStartNode(0.65));
        Assert.assertEquals("Wrong source node: ratio A:B should be 3:1.", Id.create("A", Node.class), pdn.sampleChainStartNode(0.74));
        Assert.assertEquals("Wrong source node: ratio A:B should be 3:1.", Id.create("B", Node.class), pdn.sampleChainStartNode(0.76));
        Assert.assertEquals("Wrong source node: ratio A:B should be 3:1.", Id.create("B", Node.class), pdn.sampleChainStartNode(0.90));
    }

    @Test
    public void testGetEdges() {
        List<DigicoreChain> chains = setupListOfChains();

        PathDependentNetwork pdn = new PathDependentNetwork(12345L);
        for (DigicoreChain chain : chains) {
            pdn.processActivityChain(chain);
        }
        Map<Id<Node>, Map<Id<Node>, Double>> edgeMap = pdn.getEdges();
        Assert.assertEquals("Wrong number of nodes.", 5, edgeMap.size());
        // A
        Assert.assertTrue("Should contain node A.", edgeMap.containsKey(Id.create("A", ActivityFacility.class)));
        Assert.assertEquals("Wrong number of edges for A.", 2, edgeMap.get(Id.create("A", ActivityFacility.class)).size());
        Assert.assertEquals("Wrong weight.", 1.0, edgeMap.get(Id.create("A", ActivityFacility.class)).get(Id.create("B", ActivityFacility.class)), MatsimTestUtils.EPSILON);
        Assert.assertEquals("Wrong weight.", 2.0, edgeMap.get(Id.create("A", ActivityFacility.class)).get(Id.create("C", ActivityFacility.class)), MatsimTestUtils.EPSILON);
        // B
        Assert.assertTrue("Should contain node B.", edgeMap.containsKey(Id.create("B", ActivityFacility.class)));
        Assert.assertEquals("Wrong number of edges for B.", 1, edgeMap.get(Id.create("B", ActivityFacility.class)).size());
        Assert.assertEquals("Wrong weight.", 2.0, edgeMap.get(Id.create("B", ActivityFacility.class)).get(Id.create("C", ActivityFacility.class)), MatsimTestUtils.EPSILON);
        // C
        Assert.assertTrue("Should contain node C.", edgeMap.containsKey(Id.create("C", ActivityFacility.class)));
        Assert.assertEquals("Wrong number of edges for C.", 2, edgeMap.get(Id.create("C", ActivityFacility.class)).size());
        Assert.assertEquals("Wrong weight.", 2.0, edgeMap.get(Id.create("C", ActivityFacility.class)).get(Id.create("D", ActivityFacility.class)), MatsimTestUtils.EPSILON);
        Assert.assertEquals("Wrong weight.", 1.0, edgeMap.get(Id.create("C", ActivityFacility.class)).get(Id.create("E", ActivityFacility.class)), MatsimTestUtils.EPSILON);
        // D
        Assert.assertTrue("Should contain node D.", edgeMap.containsKey(Id.create("D", ActivityFacility.class)));
        Assert.assertEquals("Wrong number of edges for D.", 0, edgeMap.get(Id.create("D", ActivityFacility.class)).size());
        // E
        Assert.assertTrue("Should contain node E.", edgeMap.containsKey(Id.create("E", ActivityFacility.class)));
        Assert.assertEquals("Wrong number of edges for E.", 0, edgeMap.get(Id.create("E", ActivityFacility.class)).size());
    }


    @Test
    public void testPathDependence(){
        List<DigicoreChain> chains = setupListOfChains();

        PathDependentNetwork pdn = new PathDependentNetwork(12345L);
        for (DigicoreChain chain : chains) {
            pdn.processActivityChain(chain);
        }

        // A
        PathDependentNode nodeA = pdn.getPathDependentNode(Id.createNodeId("A"));
        Assert.assertEquals("Wrong number of path dependence entries.", 1, nodeA.getPathDependence().size());
        Assert.assertTrue("Should contain 'source'.", nodeA.getPathDependence().containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)));
        Assert.assertEquals("Wrong number of destinations.", 3, nodeA.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)).size());
        Assert.assertTrue("Destination should exist", nodeA.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)).containsKey(Id.createNodeId("B")));
        Assert.assertTrue("Destination should exist", nodeA.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)).containsKey(Id.createNodeId("C")));
        Assert.assertTrue("Destination should exist", nodeA.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)).containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN)));

        // B
        PathDependentNode nodeB = pdn.getPathDependentNode(Id.createNodeId("B"));
        Assert.assertEquals("Wrong number of path dependence entries.", 3, nodeB.getPathDependence().size());
        Assert.assertTrue("Should contain 'A'.", nodeB.getPathDependence().containsKey(Id.createNodeId("A")));
        Assert.assertTrue("Should contain 'source'.", nodeB.getPathDependence().containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)));
        Assert.assertTrue("Should contain 'unknown'.", nodeB.getPathDependence().containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN)));
        // B - C
        Assert.assertEquals("Wrong number of destinations.", 1, nodeB.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)).size());
        Assert.assertTrue("Destination should exist", nodeB.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_SOURCE)).containsKey(Id.createNodeId("C")));
        // ?? - B - C
        Assert.assertEquals("Wrong number of destinations.", 1, nodeB.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN)).size());
        Assert.assertTrue("Destination should exist", nodeB.getPathDependence().get(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN)).containsKey(Id.createNodeId("C")));
        // A - B - ??
        Assert.assertEquals("Wrong number of destinations.", 1, nodeB.getPathDependence().get(Id.createNodeId("A")).size());
        Assert.assertTrue("Destination should exist", nodeB.getPathDependence().get(Id.createNodeId("A")).containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN)));

        // C
        PathDependentNode nodeC = pdn.getPathDependentNode(Id.createNodeId("C"));
        Assert.assertEquals("Wrong number of path dependence entries.", 2, nodeC.getPathDependence().size());
        Assert.assertTrue("Should contain 'A'.", nodeC.getPathDependence().containsKey(Id.createNodeId("A")));
        Assert.assertTrue("Should contain 'B'.", nodeC.getPathDependence().containsKey(Id.createNodeId("B")));
        // A - C - D/E
        Assert.assertEquals("Wrong number of destinations.", 2, nodeC.getPathDependence().get(Id.createNodeId("A")).size());
        Assert.assertTrue("Destination should exist", nodeC.getPathDependence().get(Id.createNodeId("A")).containsKey(Id.createNodeId("D")));
        Assert.assertTrue("Destination should exist", nodeC.getPathDependence().get(Id.createNodeId("A")).containsKey(Id.createNodeId("E")));
        // B - C - D/E
        Assert.assertEquals("Wrong number of destinations.", 2, nodeC.getPathDependence().get(Id.createNodeId("B")).size());
        Assert.assertTrue("Destination should exist", nodeC.getPathDependence().get(Id.createNodeId("B")).containsKey(Id.createNodeId("D")));
        Assert.assertTrue("Destination should exist", nodeC.getPathDependence().get(Id.createNodeId("B")).containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN)));

        // D
        PathDependentNode nodeD = pdn.getPathDependentNode(Id.createNodeId("D"));
        Assert.assertEquals("Wrong number of path dependence entries.", 1, nodeD.getPathDependence().size());
        Assert.assertTrue("Should contain 'C'.", nodeD.getPathDependence().containsKey(Id.createNodeId("C")));
        // Sink only
        Assert.assertEquals("Wrong number of destinations.", 1, nodeD.getPathDependence().get(Id.createNodeId("C")).size());
        Assert.assertTrue("Destination should exist", nodeD.getPathDependence().get(Id.createNodeId("C")).containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_SINK)));

        // E
        PathDependentNode nodeE = pdn.getPathDependentNode(Id.createNodeId("E"));
        Assert.assertEquals("Wrong number of path dependence entries.", 1, nodeE.getPathDependence().size());
        Assert.assertTrue("Should contain 'C'.", nodeE.getPathDependence().containsKey(Id.createNodeId("C")));
        // Sink only
        Assert.assertEquals("Wrong number of destinations.", 1, nodeE.getPathDependence().get(Id.createNodeId("C")).size());
        Assert.assertTrue("Destination should exist", nodeE.getPathDependence().get(Id.createNodeId("C")).containsKey(Id.createNodeId(ComplexNetworkUtils.NAME_SINK)));

        // "Unknown"
        PathDependentNode nodeUnknown = pdn.getPathDependentNode(Id.createNodeId(ComplexNetworkUtils.NAME_UNKNOWN));
        Assert.assertNull("Should not have unknown node.", nodeUnknown);
    }

    /**
     * Consider the following example: build a network on the following nodes
     * <p>
     * (0,10)       (10,10)        (20,10)
     * A             D              ??
     *      (5,5)
     *        C
     * B             E
     * (0,0)        (10,0)
     * <p>
     * from the following activity chains:
     * <p>
     * A -> C -> D
     * A -> C -> E
     * B -> C -> D
     * A -> ?? -> B -> C -> ??
     * A -> B -> ??
     * <p>
     * The idea is that when at `C', given the former node was `A', it should be
     * equally likely to choose `D' and `E'. Conversely, if the former node was
     * `B', there should only be one likely next destination, namely `E'.
     */
    private List<DigicoreChain> setupListOfChains() {
        List<DigicoreChain> list = new ArrayList<>();
        /* Chain A -> C -> D */
        DigicoreActivity da1_A = new DigicoreActivity("test", TimeZone.getTimeZone("GMT+2"), Locale.ENGLISH);
        da1_A.setFacilityId(Id.create("A", ActivityFacility.class));
        da1_A.setCoord(new Coord(0, 10));
        DigicoreActivity da1_C = new DigicoreActivity("test", TimeZone.getTimeZone("GMT+2"), Locale.ENGLISH);
        da1_C.setFacilityId(Id.create("C", ActivityFacility.class));
        da1_C.setCoord(new Coord(5, 5));
        DigicoreActivity da1_D = new DigicoreActivity("test", TimeZone.getTimeZone("GMT+2"), Locale.ENGLISH);
        da1_D.setFacilityId(Id.create("D", ActivityFacility.class));
        da1_D.setCoord(new Coord(10, 10));
        DigicoreChain c1 = new DigicoreChain();
        c1.add(da1_A);
        c1.add(da1_C);
        c1.add(da1_D);
        list.add(c1);

        /* Chain A -> C -> E */
        DigicoreActivity da2_A = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da2_A.setFacilityId(Id.create("A", ActivityFacility.class));
        da2_A.setCoord(new Coord(0, 10));
        DigicoreActivity da2_C = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da2_C.setFacilityId(Id.create("C", ActivityFacility.class));
        da2_C.setCoord(new Coord(5, 5));
        DigicoreActivity da2_E = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da2_E.setFacilityId(Id.create("E", ActivityFacility.class));
        da2_E.setCoord(new Coord(10, 0));
        DigicoreChain c2 = new DigicoreChain();
        c2.add(da2_A);
        c2.add(da2_C);
        c2.add(da2_E);
        list.add(c2);

        /* Chain B -> C -> D */
        DigicoreActivity da3_B = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da3_B.setFacilityId(Id.create("B", ActivityFacility.class));
        da3_B.setCoord(new Coord(0, 0));
        DigicoreActivity da3_C = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da3_C.setFacilityId(Id.create("C", ActivityFacility.class));
        da3_C.setCoord(new Coord(5, 5));
        DigicoreActivity da3_D = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da3_D.setFacilityId(Id.create("D", ActivityFacility.class));
        da3_D.setCoord(new Coord(10, 10));
        DigicoreChain c3 = new DigicoreChain();
        c3.add(da3_B);
        c3.add(da3_C);
        c3.add(da3_D);
        list.add(c3);

        /* A -> ?? -> B -> C -> ?? */
        DigicoreActivity da4_A = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da4_A.setFacilityId(Id.create("A", ActivityFacility.class));
        da4_A.setCoord(new Coord(0, 10));
        DigicoreActivity da4_dummy1 = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da4_dummy1.setCoord(new Coord(20, 10));
        DigicoreActivity da4_B = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da4_B.setFacilityId(Id.create("B", ActivityFacility.class));
        da4_B.setCoord(new Coord(0, 0));
        DigicoreActivity da4_C = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da4_C.setFacilityId(Id.create("C", ActivityFacility.class));
        da4_C.setCoord(new Coord(5, 5));
        DigicoreActivity da4_dummy2 = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da4_dummy2.setCoord(new Coord(20, 10));
        DigicoreChain c4 = new DigicoreChain();
        c4.add(da4_A);
        c4.add(da4_dummy1);
        c4.add(da4_B);
        c4.add(da4_C);
        c4.add(da4_dummy2);
        list.add(c4);

        /* A -> B -> ?? */
        DigicoreActivity da5_A = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da5_A.setFacilityId(Id.create("A", ActivityFacility.class));
        da5_A.setCoord(new Coord(0, 10));
        DigicoreActivity da5_B = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da5_B.setFacilityId(Id.create("B", ActivityFacility.class));
        da5_B.setCoord(new Coord(0, 0));
        DigicoreActivity da5_dummy = new DigicoreActivity("test", TimeZone.getDefault(), Locale.ENGLISH);
        da5_dummy.setCoord(new Coord(20, 10));
        DigicoreChain c5 = new DigicoreChain();
        c5.add(da5_A);
        c5.add(da5_B);
        c5.add(da5_dummy);
        list.add(c5);

        return list;
    }

}
