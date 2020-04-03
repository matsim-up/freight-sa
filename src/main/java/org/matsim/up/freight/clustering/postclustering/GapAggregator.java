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
package org.matsim.up.freight.clustering.postclustering;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.up.freight.containers.DigicoreActivity;
import org.matsim.up.freight.containers.DigicoreChain;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.freight.io.DigicoreVehiclesWriter;
import org.matsim.up.utils.Header;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Class to aggregate the {@link org.matsim.up.freight.containers.DigicoreActivity} to
 * the Geospatial Analysis Platform (GAP) mesozone. The intention is that the
 * identity of the facilities should be masked as a mesozone is roughly about
 * 7km x 7km, or approximately 50km squared.
 *
 * @see <a href="http://www.stepsa.org/socio_econ.html">Spatial indicators at StepSA</a>.
 */
public class GapAggregator {
	final private static Logger LOG = Logger.getLogger(GapAggregator.class);
	final private static double DISTANCE_THRESHOLD = 20000;
	final private static String ATTR_MESOZONE_ID = "MESO_ID";
	private static Map<Id<Geometry>, Geometry> gapMap;
	private static QuadTree<Id<Geometry>> qt;

	public static void main(String[] args) {
		if(args.length != 3){
			LOG.error("Wrong number of arguments. They must be:");
			LOG.error("   1. Input DigicoreVehicles file;");
			LOG.error("   2. Input GAP-shapefile (projected in the same coordinate reference system); and");
			LOG.error("   3. Output DigicoreVehicles file.");
			throw new IllegalArgumentException("Terminating.");
		}
		run(args);
	}

	public static void run(String[] args){
		Header.printHeader(GapAggregator.class, args);
		String vehiclesIn = args[0];
		String gap = args[1];
		String vehiclesOut = args[2];

		parseGapQuadTree(gap);
		processVehicles(vehiclesIn, vehiclesOut);

		Header.printFooter();
	}

	/**
	 * Parses the GAP mesozones into a {@link QuadTree}, and keeping the entire
	 * @param gapFile the *.shp file containing the GAP mesozones.
	 */
	private static void parseGapQuadTree(String gapFile){
		LOG.info("Parsing GAP mesozones into QuadTree...");
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;

		ShapeFileReader sfr = new ShapeFileReader();
		Collection<SimpleFeature> features = sfr.readFileAndInitialize(gapFile);
		for(SimpleFeature feature : features){
			Geometry g;
			Object o = feature.getDefaultGeometry();
			if(o instanceof MultiPolygon){
				g = (Geometry) o;
				double x = g.getCentroid().getX();
				double y = g.getCentroid().getY();
				minX = Math.min(minX, x);
				maxX = Math.max(maxX, x);
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}

		gapMap = new TreeMap<>();
		qt = new QuadTree<>(minX, minY, maxX, maxY);
		int nonMultiPolygons = 0;
		for(SimpleFeature feature : features){
			Geometry g;
			Object o  = feature.getDefaultGeometry();
			if(o instanceof Geometry){
				/* Get the feature's GAP zone name. */
				String id = feature.getAttribute(ATTR_MESOZONE_ID).toString();
				Id<Geometry> geometryId = Id.create(id, Geometry.class);

				/* Get the feature's geometry. */
				g = (Geometry) o;
				qt.put(g.getCentroid().getX(), g.getCentroid().getY(), geometryId);

				/* Put the geometry itself into the static map. */
				gapMap.put(geometryId, g);
			} else{
				nonMultiPolygons++;
			}
		}
		LOG.warn("Total number of GAP mesozone geometries that are NOT MultiPolygons: " + nonMultiPolygons);

		LOG.info("Done parsing GAP mesozones; total of " + qt.size() + " found.");
	}


	private static void processVehicles(String fileIn, String fileOut){
		DigicoreVehicles vehicles = new DigicoreVehicles();
		new DigicoreVehiclesReader(vehicles).readFile(fileIn);

		LOG.info("Processing the vehicles.");
		Counter counter = new Counter("   vehicles # ");
		GeometryFactory gf = new GeometryFactory();
		for(DigicoreVehicle vehicle : vehicles.getVehicles().values()){
			for(DigicoreChain chain : vehicle.getChains()){
				for(DigicoreActivity activity : chain.getAllActivities()){
					Id<ActivityFacility> fId = activity.getFacilityId();
					if(fId != null){
						/* It has a facility Id, now find its associated GAP zone. */
						Coord c = activity.getCoord();
						Point p = gf.createPoint(new Coordinate(c.getX(), c.getY()));

						Collection<Id<Geometry>> geometries = qt.getDisk(c.getX(), c.getY(), DISTANCE_THRESHOLD);
						Iterator<Id<Geometry>> iterator = geometries.iterator();
						boolean found = false;
						while(!found && iterator.hasNext()){
							Id<Geometry> thisGeometryId = iterator.next();
							Geometry thisGeometry = gapMap.get(thisGeometryId);
							if(thisGeometry.covers(p)){
								found = true;
								activity.setFacilityId(Id.create(thisGeometryId.toString(), ActivityFacility.class));

								/* Update the coordinate to the GAP zones's envelope centroid. */
								Point centroidP = thisGeometry.getEnvelope().getCentroid();
								Coord centroidC = CoordUtils.createCoord(centroidP.getX(), centroidP.getY());
								activity.setCoord(centroidC);
							}
						}
					}
				}
			}
			counter.incCounter();
		}
		counter.printCounter();
		LOG.info("Done processing the vehicles.");

		/* Write the final vehicles to file. */
		new DigicoreVehiclesWriter(vehicles).write(fileOut);
	}
}
