/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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
package org.matsim.up.freight.analysis.activity;

import com.vividsolutions.jts.geom.*;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.up.freight.containers.DigicoreActivity;
import org.matsim.up.freight.containers.DigicoreChain;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.utils.Header;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class ActivityOutsideSaCounter {
    final private static Logger LOG = Logger.getLogger(ActivityOutsideSaCounter.class);
    private static Map<Id<Vehicle>, Integer> map = new HashMap<>();

    public static void main(String[] args) {
        Header.printHeader(ActivityOutsideSaCounter.class, args);
        try {
            run(args);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not process activity counts outside of SA.");
        }
        Header.printFooter();
    }


    public static void run(String[] args) throws IOException {
        String vehiclesFile = args[0];
        String shapefile = args[1];
        String output = args[2];

        /* Read the shapefile */
        ShapeFileReader sfr = new ShapeFileReader();
        Collection<SimpleFeature> features = sfr.readFileAndInitialize(shapefile);
        if (features.size() > 1) {
            LOG.warn("There are " + features.size() + " features in '" + shapefile + "'. Using only the first.");
        }
        SimpleFeature feature = features.iterator().next();
        Object o = feature.getDefaultGeometry();
        MultiPolygon mp = null;
        if (o instanceof MultiPolygon) {
            mp = (MultiPolygon) o;
        } else {
            LOG.error("Geometry is not of type 'MultiPolygon' but '" + o.getClass().toString() + "'");
        }
        assert mp != null;
        Geometry envelope = mp.getEnvelope();

        /* Read the vehicles container */
        Counter counter = new Counter("   vehicles: ");
        GeometryFactory gf = new GeometryFactory();
        DigicoreVehicles vehicles = new DigicoreVehicles();
        new DigicoreVehiclesReader(vehicles).readFile(vehiclesFile);
        for (DigicoreVehicle vehicle : vehicles.getVehicles().values()) {
            for (DigicoreChain chain : vehicle.getChains()) {
                for (DigicoreActivity act : chain.getAllActivities()) {
                    Point p = gf.createPoint(new Coordinate(act.getCoord().getX(), act.getCoord().getY()));
                    if (envelope.covers(p)) {
                        if (!mp.covers(p)) {
                            processActivity(vehicle.getId());
                        }
                    } else {
                        processActivity(vehicle.getId());
                    }
                }
            }
            counter.incCounter();
        }
        counter.printCounter();

        /* Write the output to file. */
        try (BufferedWriter bw = IOUtils.getBufferedWriter(output)) {
            bw.write("vId,count");
            bw.newLine();
            for (Id<Vehicle> vId : map.keySet()) {
                bw.write(String.format("%s,%d\n", vId.toString(), map.get(vId)));
            }
        }
    }


    private static void processActivity(Id<Vehicle> vId) {
        if (!map.containsKey(vId)) {
            map.put(vId, 1);
        } else {
            map.put(vId, map.get(vId) + 1);
        }
    }
}
