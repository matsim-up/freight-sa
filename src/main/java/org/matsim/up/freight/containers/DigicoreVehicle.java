package org.matsim.up.freight.containers;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Id;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.ArrayList;
import java.util.List;


public class DigicoreVehicle implements Vehicle {
	private final Id<Vehicle> id;
	private VehicleType type = VehicleUtils.createVehicleType(Id.create("commercial", VehicleType.class));
	private final List<DigicoreChain> chains = new ArrayList<>();
	private final Attributes attributes = new Attributes();
	
	public DigicoreVehicle(final Id<Vehicle> id) {
		this.id = id;
	}

	public Id<Vehicle> getId() {
		return this.id;
	}

	public VehicleType getType() {
		return this.type;
	}
	
	public List<DigicoreChain> getChains(){
		return this.chains;
	}
	
	public void setType(String type){
		this.type = VehicleUtils.createVehicleType(Id.create(type, VehicleType.class));
	}
	
	
	
	
	/**
	 * This method takes a {@link MultiPolygon} and assesses what % of a vehicle's activities
	 * takes place in the area.
	 * 
	 * @param area the MultiPolygon obtained from a shapefile
	 * @return an int value (either 0 for intra-provincial, 1 for inter-provincial, 2 for extra-provincial)
	 */
	@Deprecated
	public int determineIfIntraInterExtraVehicle(MultiPolygon area){
		int vehicleType = 4; //just arbitrary value to know if the vehicle couldn't be classified. 
		int inside = 0;
		int allCount = 0;
		
		GeometryFactory gf = new GeometryFactory();
		
		for(DigicoreChain dc : this.getChains()){
			Point p;
			int countInside_Chain = 0;
			
			/*
			 * this was changed (QvH-October 2012) to consider ALL activities in
			 * a chain when determining whether in it intra, inter, extra
			 */
			for(DigicoreActivity da : dc.getAllActivities()){
				//if(da.getType().equalsIgnoreCase("minor")){
					p = gf.createPoint(new Coordinate(da.getCoord().getX(),da.getCoord().getY()));
					if(area.getEnvelope().contains(p)){
						if(area.contains(p)){
							countInside_Chain++;
						}
					}
				allCount++;
			}
			inside += countInside_Chain;
		}
		
		if(allCount > 0){
			double percentageValue = (double)inside/(double)allCount;
			
			if(percentageValue > 0.6){
				//this is an intra-provincial vehicle
				vehicleType = 0;
			}else if(percentageValue <= 0.6){
				if(percentageValue > 0){
					//this is an inter-provincial vehicle
					vehicleType = 1;
				}else{
					//this vehicle never enters the area --> extra vehicle
					vehicleType = 2;
				}
			}
			
		}else{
			Logger.getLogger(DigicoreVehicle.class).warn("Vehicle " + this.getId() + " contains no activities.");
		}
				
		return vehicleType;
	}

	@Override
	public Attributes getAttributes() {
		return this.attributes;
	}
}
