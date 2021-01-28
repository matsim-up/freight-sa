package org.matsim.up.freight.containers;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.ActivityFacility;

import org.matsim.utils.objectattributes.attributable.Attributes;

public class DigicoreActivity implements Activity, DigicoreChainElement {
		
	private Id<ActivityFacility> facilityId;
	private Id<Link> linkId;
	private String type;
	private Coord coord;
	private GregorianCalendar startTime;
	private GregorianCalendar endTime;
	private Double maximumDuration = 0.0;
	
	public DigicoreActivity(String type, TimeZone timeZone, Locale locale) {
		this.type = type;
		startTime = new GregorianCalendar(timeZone, locale);
		endTime = new GregorianCalendar(timeZone, locale);
	}

	public boolean isInArea(MultiPolygon area){
		GeometryFactory gf = new GeometryFactory();
		Point p = gf.createPoint(new Coordinate(this.coord.getX(),this.coord.getY()));
		boolean result = false;
		if(area.getEnvelope().contains(p)){
			if(area.contains(p)){
				result = true;
			}else{
				result = false;
			}
		}
		return result;
	}
	
	public String getType(){
		return this.type;
	}

	public Coord getCoord() {
		return this.coord;
	}
	
	public void setCoord(Coord coord) {
		this.coord = coord;
	}
	
	public GregorianCalendar getStartTimeGregorianCalendar() {
		return startTime;
	}
	
	public GregorianCalendar getEndTimeGregorianCalendar() {
		return endTime;
	}
	
	public boolean isAtSameCoord(DigicoreActivity da){
		if(this.coord.getX() == da.getCoord().getX() &&
				this.coord.getY() == da.getCoord().getY()){
			return true;
		} else{
			return false;
		}
	}
	
	/**
	 * Calculates the duration of the activity in seconds.
	 * @return duration (in sec).
	 */
	public double getDuration(){
		return this.getEndTime().seconds() - this.getStartTime().seconds();
	}

	@Override
	public void setStartTime(double seconds) {
		startTime.setTimeInMillis(Math.round(seconds * 1000.0));		
	}

	@Override
	public void setStartTimeUndefined() {
		/*FIXME Not sure how to deal with undefined start times, Jan'21. */
		throw new UnsupportedOperationException();
	}

	@Override
	public void setEndTime(double seconds) {
		endTime.setTimeInMillis(Math.round(seconds * 1000.0));		
	}

	@Override
	public void setEndTimeUndefined() {
		/*FIXME Not sure how to deal with undefined end times, Jan'21. */
		throw new UnsupportedOperationException();
	}

	@Override
	public void setType(String type) {
		this.type = type;		
	}


	@Override
	public Id<Link> getLinkId() {
		return this.linkId;
	}
	
	public void setLinkId(Id<Link> linkId){
		this.linkId = linkId;
	}

	@Override
	public Id<ActivityFacility> getFacilityId() {
		return this.facilityId;
	}
	
	public void setFacilityId(Id<ActivityFacility> facilityId){
		this.facilityId = facilityId;
	}

	@Override
	public OptionalTime getEndTime() {
		return OptionalTime.defined((double)this.endTime.getTimeInMillis() / 1000.0);
	}

	@Override
	public OptionalTime getStartTime() {
		return OptionalTime.defined((double)this.startTime.getTimeInMillis() / 1000.0);
	}
	

	@Override
	public OptionalTime getMaximumDuration() {
		return OptionalTime.defined(this.maximumDuration);
	}


	@Override
	public void setMaximumDuration(double seconds) {
		this.maximumDuration = seconds;
	}

	@Override
	public void setMaximumDurationUndefined() {
		/*FIXME Not sure how to deal with undefined durations, Jan'21. */
		throw new UnsupportedOperationException();
	}


	/**
	 * Returns the number of seconds since midnight. This method does
	 * <b>not</b> consider the position of the activity in the activity
	 * chain. The consequence is that activities that run over 24:00:00
	 * must be manually corrected if they appear in an activity chain. 
	 * 
	 * @return
	 */
	public double getStartTimeInSecondsFromMidnight(){
		int hour = startTime.get(Calendar.HOUR_OF_DAY);
		int minute = startTime.get(Calendar.MINUTE);
		int second = startTime.get(Calendar.SECOND);
		return hour*60*60 + minute*60 + second;
	}
	
	
	/**
	 * Returns the number of seconds since midnight. This method does
	 * <b>not</b> consider the position of the activity in the activity
	 * chain. The consequence is that activities that run over 24:00:00
	 * must be manually corrected if they appear in an activity chain. 
	 * 
	 * @return
	 */
	public double getEndTimeInSecondsFromMidnight(){
		int hour = endTime.get(Calendar.HOUR_OF_DAY);
		int minute = endTime.get(Calendar.MINUTE);
		int second = endTime.get(Calendar.SECOND);
		return hour*60*60 + minute*60 + second;
	}

	@Override
	public Attributes getAttributes() {
		throw new UnsupportedOperationException();
	}
}
