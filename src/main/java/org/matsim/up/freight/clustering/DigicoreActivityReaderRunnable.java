/* *********************************************************************** *
 * project: org.matsim.*
 * RunnableDigicoreActivityReader.java
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

package org.matsim.up.freight.clustering;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.misc.Counter;
import org.matsim.up.freight.clustering.containers.MyZone;
import org.matsim.up.freight.containers.DigicoreActivity;
import org.matsim.up.freight.containers.DigicoreChain;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.io.DigicoreVehicleReader_v1;

public class DigicoreActivityReaderRunnable implements Runnable {
	
	private int inCount = 0;
	private int outCount = 0;
	private GeometryFactory gf = new GeometryFactory();
	private final Map<Id<MyZone>, List<Coord>> map = new HashMap<Id<MyZone>, List<Coord>>();;
	private DigicoreVehicle vehicle;
	private final QuadTree<MyZone> zoneQT;
	private Counter counter;
	
	
	/**
	 * This constructor is now deprecated since it caters for reading each 
	 * {@link DigicoreVehicle} from file. As an argument, it therefore takes
	 * the specific vehicle's file. Rather use {@link #DigicoreActivityReaderRunnable(DigicoreVehicle, QuadTree, Counter)} 
	 * @param vehicleFile
	 * @param zoneQT
	 * @param counter
	 */
	@Deprecated
	public DigicoreActivityReaderRunnable(final File vehicleFile, QuadTree<MyZone> zoneQT, Counter counter) {
		this(parseVehicleForConstructor(vehicleFile.getAbsolutePath()), zoneQT, counter);
	}
	
	
	/**
	 * Instantiates an instance of the class.
	 * @param vehicle
	 * @param zoneQT
	 * @param counter
	 */
	public DigicoreActivityReaderRunnable(DigicoreVehicle vehicle, QuadTree<MyZone> zoneQT, Counter counter) {
		this.vehicle = vehicle;
		this.zoneQT = zoneQT;
		this.counter = counter;
		
		for(MyZone mz : this.zoneQT.values()){
			map.put(mz.getId(), new ArrayList<Coord>());
		}
	}
	
	
	private static DigicoreVehicle parseVehicleForConstructor(String file){
		DigicoreVehicleReader_v1 dvr = new DigicoreVehicleReader_v1();
		dvr.readFile(file);
		return dvr.getVehicle();
	}
	
	
	public int getInCount(){
		return this.inCount;
	}
	
	
	public int getOutCount(){
		return this.outCount;
	}
	

	@Override
	public void run() {
		for(DigicoreChain dc : this.vehicle.getChains()){
			
			/* Read in ALL activities, not just minor activities. */
			for(DigicoreActivity da : dc.getAllActivities()){
				Point p = gf.createPoint(new Coordinate(da.getCoord().getX(), da.getCoord().getY()));

				/* Get all the zones surrounding the point.
				 *  
				 * PROBLEM: If only the entire area is given, for example Nelson
				 * Mandela Bay, and the single zone is put in the QT at its 
				 * centroid location, only points surrounding the centroid will
				 * be added. 
				 * 
				 * One way to solve this, albeit not very computationally 
				 * efficient, is to check the number of MyZones in the QT. If 
				 * only one, then check that one zone. Alternatively, follow 
				 * the original procedure of looking for only the surrounding 
				 * zones. 
				 * 
				 * !! This is VERY BAD (Johan, Jan '17... the radius is 
				 * hard-coded, and it seems to only be compatible with GAP 
				 * zones.) */
				if(zoneQT.size() > 1){
					double radius = 10000.;
					Collection<MyZone> neighbourhood =  zoneQT.getDisk(p.getX(), p.getY(), radius);
					while(neighbourhood.size() < .1*zoneQT.size()){
						/* This threshold is again hard-coded, but should at 
						 * least provide more useful clustering, albeit at the
						 * price of the computational burden, especially when 
						 * there are many zone, like GAP. */
						radius *= 2.;
						neighbourhood =  zoneQT.getDisk(p.getX(), p.getY(), radius);
					}
					
					boolean found = false;
					Iterator<MyZone> iterator = neighbourhood.iterator();
					while(iterator.hasNext() && !found){
						MyZone mz = iterator.next();
						if(mz.getEnvelope().contains(p)){
							if(mz.contains(p)){
								found = true;
								map.get(mz.getId()).add(da.getCoord());
								inCount++;
							}
						}
					}
					if(!found){
						outCount++;
					}					
				} else{
					/* There is only ONE zone, i.e. the entire study area. 
					 * Check ALL points in that zone. To make it computationally
					 * a bit more efficient, first check the envelope. */
					MyZone zone = zoneQT.getClosest(p.getX(), p.getY());
					if(zone.getEnvelope().contains(p)){
						if(zone.contains(p)){
							map.get(zone.getId()).add(da.getCoord());
							inCount++;
						} else{
							outCount++;
						}
					} else{
						outCount++;
					}
				}
			}		
		}
		counter.incCounter();
	}


	public Map<Id<MyZone>, List<Coord>> getMap(){
		return this.map;
	}

}

