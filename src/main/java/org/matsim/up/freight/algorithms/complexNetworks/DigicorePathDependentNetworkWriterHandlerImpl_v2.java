/* *********************************************************************** *
 * project: org.matsim.*
 * DigicoreVehicleWriterHandlerImpl_v0.java
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

package org.matsim.up.freight.algorithms.complexNetworks;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Node;
import org.matsim.up.freight.algorithms.complexNetworks.PathDependentNetwork.PathDependentNode;


public class DigicorePathDependentNetworkWriterHandlerImpl_v2 implements
		DigicorePathDependentNetworkWriterHandler {

	@Override
	public void writeSeparator(BufferedWriter out) {
		/* Don't think a separator will make the file more readable. */
	}
	
	@Override
	public void startNetwork(PathDependentNetwork network, BufferedWriter out)
			throws IOException {
		out.write("\n<digicoreNetwork");
		if(network.getDescription() != null){
			out.write(" desc=\"" + network.getDescription() + "\"");
		}
		out.write(">\n\n");
	}

	@Override
	public void endNetwork(BufferedWriter out) throws IOException {
		out.write("</digicoreNetwork>");
	}

	@Override
	public void startNode(PathDependentNode node, BufferedWriter out)
			throws IOException {
		out.write("\t<node");
		out.write(" id=\"" + node.getId().toString() + "\"");
		out.write(String.format(Locale.US, " x=\"%.2f\" y=\"%.2f\">\n",
				node.getCoord().getX(), 
				node.getCoord().getY() ) );
	}

	@Override
	public void endNode(PathDependentNode node, BufferedWriter out) throws IOException {
		out.write("\t</node>\n\n");
	}

	@Override
	public void startPreceding(Id<Node> id, BufferedWriter out) throws IOException {
		out.write("\t\t<preceding");
		out.write(" id=\"" + id.toString());
		out.write("\">\n");
	}

	@Override
	public void endPreceding(BufferedWriter out) throws IOException {
		out.write("\t\t</preceding>\n");
	}

	@Override
	public void startFollowing(Map<Id<Node>, Double> following, BufferedWriter out)
			throws IOException {
		for(Id<Node> id : following.keySet()){
			out.write("\t\t\t<following");
			out.write(" id=\"" + id.toString() + "\"");
			out.write(" weight=\"" + String.format(Locale.US, "%.2f", following.get(id)));
			out.write("\"/>\n");
		}
	}

	@Override
	public void endFollowing(BufferedWriter out) throws IOException {
		/* Do nothing. */
	}

	@Override
	public void startStartNode(Map<String, Integer> startNode,
			BufferedWriter out) throws IOException {
		/* Convert the strings to sortable values */
		Map<Integer, Map<Integer, Integer>> map = new TreeMap<Integer, Map<Integer,Integer>>();
		for(String s : startNode.keySet()){
			String[] sa = s.split(",");
			Integer hour = Integer.parseInt(sa[0]);
			Integer activities = Integer.parseInt(sa[1]);
			Integer count = startNode.get(s);
			if(!map.containsKey(hour)){
				map.put(hour, new TreeMap<>());
			}
			Map<Integer, Integer> hourMap = map.get(hour);
			hourMap.put(activities, count);
		}
		out.write("\n");
		out.write("\t\t<!--  Source node: activity chain start times and number of activities. -->\n");
		for(Integer hour : map.keySet()){
			for(Integer activities : map.get(hour).keySet()){
				out.write("\t\t<startnode");
				out.write(" hour=\"" + hour + "\"");
				out.write(" activities=\"" + activities + "\"");
				out.write(" count=\"" + map.get(hour).get(activities) + "\"/>\n");
			}
		}
	}

	@Override
	public void endStartNode(BufferedWriter out) throws IOException {
		/* Do nothing. */
	}


}

