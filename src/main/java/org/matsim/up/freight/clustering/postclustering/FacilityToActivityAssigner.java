package org.matsim.up.freight.clustering.postclustering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.up.freight.clustering.ClusterUtils;
import org.matsim.up.freight.clustering.HullConverter;
import org.matsim.up.freight.clustering.containers.MyMultiFeatureReader;
import org.matsim.up.freight.clustering.containers.MyZone;
import org.matsim.up.freight.containers.DigicoreFacility;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.freight.io.DigicoreVehiclesWriter;
import org.matsim.up.utils.Header;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;


public class FacilityToActivityAssigner {
	private static Logger log = Logger.getLogger(FacilityToActivityAssigner.class);
	private static long reconstructDuration;
	private static long treeBuildDuration;
	private static long writeToFileDuration;

	/**
	 * This class will read in a set of facilities, along with their attributes, 
	 * and then adapt given vehicles' activity chains. If any activity in the
	 * chain occurs at a read facility - that is, it falls within the facility's 
	 * bounding polygon (concave hull), the activity is associated with that 
	 * facility. Consecutive activities belonging to the same cluster/facility 
	 * will, in this revised version, <b><i>NOT</i></b> be merged. The new 
	 * chains will be written out to new XML files.
	 * 
	 * <h4>Note:</h4>
	 * This class supersedes the original <code>ClusteredChainGenerator</code>
	 * (if it is still visible somewhere).
	 *
	 * @param args
	 * <ul>
	 * <li> args[0] = the absolute path of the input {@link DigicoreVehicles} 
	 * 				  container;
	 * <li> args[1] = the absolute path of the facilities file that was created
	 * 				  by the {@link org.matsim.up.freight.clustering.DigicoreClusterRunner} class;
	 * <li> args[2] = the number of threads to use in the multithreaded parts
	 * <li> args[3] = the absolute path of the shapefile of the study area. 
	 * 				  only vehicles with at least one activity inside the area
	 * 				  will be written out to the xml folder. NOTE: It is, to my
	 * 				  current knowledge (JWJ, Aug 2013), NECESSARY to use the 
	 * 				  shapefile of the entire area, and <i><b>not</b></i> a 
	 * 				  smaller demarcation shapefile, for example the GAP zones.
	 * <li> args[4] = the ID field for the shapefile.
	 * <li> args[5] = the absolute path of the output {@link DigicoreVehicles} 
	 * 				  container;
	 * </ul>
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		Header.printHeader(FacilityToActivityAssigner.class, args);
		long startTime = System.currentTimeMillis();

		String inputVehicles = args[0];
		String inputFacilityFile = args[1];
		int nThreads = Integer.parseInt(args[2]);
		String shapefile = args[3];
		int idField = Integer.parseInt(args[4]);
		String outputVehicles = args[5];

		/* Read the study area from shapefile. This is necessary as we
		 * only want to retain xml files of vehicles that performed at
		 * least one activity in the study area. */
		MyMultiFeatureReader mfr = new MyMultiFeatureReader();
		mfr.readMultizoneShapefile(shapefile, idField);
		List<MyZone> zones = mfr.getAllZones();
		if(zones.size() > 1){
			log.warn("The read shapefile contains multiple zones. Only the first will be used as study area.");
		}
		Geometry studyArea = zones.get(0);


		/* Read facility attributes. */
//		ObjectAttributes oa = new ObjectAttributes();
//		ObjectAttributesXmlReader oar = new ObjectAttributesXmlReader(oa);
//		oar.putAttributeConverter(Point.class, new HullConverter());
//		oar.putAttributeConverter(LineString.class, new HullConverter());
//		oar.putAttributeConverter(Polygon.class, new HullConverter());
//		oar.readFile(inputFacilityAttributeFile);

		FacilityToActivityAssigner ccg = new FacilityToActivityAssigner();

		/* Build facility QuadTree. */
		QuadTree<DigicoreFacility> facilityTree = ccg.buildFacilityQuadTree(inputFacilityFile);

		/* Run through vehicle files to reconstruct the chains */
		DigicoreVehicles newVehicles = ccg.reconstructChains(facilityTree, inputVehicles, nThreads, studyArea);
		new DigicoreVehiclesWriter(newVehicles).write(outputVehicles);

		long duration = System.currentTimeMillis() - startTime;
		log.info("	 Tree build time (s): " + treeBuildDuration/1000);
		log.info("	Reconstruct time (s): " + reconstructDuration/1000);
		log.info("Write to file time (s): " + writeToFileDuration/1000);
		log.info("	  Total run time (s): " + duration/1000);

		Header.printFooter();
	}

	/**
	 * This method takes each vehicle file and reconstructs the chains.
	 * 
	 * @param facilityTree {@link QuadTree} of {@link DigicoreFacility}s built 
	 * 		  with the {@link #buildFacilityQuadTree(String)} method.
	 * @param inputVehicles original vehicles file location;
	 * @param nThreads number of threads to use.
	 * @param studyArea the geometry of the overall study area.
	 * @return {@link ConcurrentHashMap}
	 * @throws IOException
	 */
	public DigicoreVehicles reconstructChains(
			QuadTree<DigicoreFacility> facilityTree, String inputVehicles, 
			int nThreads, Geometry studyArea) throws IOException {
		long startTime = System.currentTimeMillis();

		/* Read the input vehicles container. */
		DigicoreVehicles dvs = new DigicoreVehicles();
		DigicoreVehiclesReader dvr = new DigicoreVehiclesReader(dvs);
		dvr.readFile(inputVehicles);

		/* Execute the multi-threaded jobs. */
		ExecutorService threadExecutor = Executors.newFixedThreadPool(nThreads);
		Counter threadCounter = new Counter("   vehicles completed: ");
		List<Future<DigicoreVehicle>> listOfJobs = new ArrayList<>(dvs.getVehicles().size());
		
		for(DigicoreVehicle vehicle : dvs.getVehicles().values()){
			Callable<DigicoreVehicle> job = new CallableChainReconstructor(vehicle, facilityTree, threadCounter, studyArea);
			Future<DigicoreVehicle> submit = threadExecutor.submit(job);
			listOfJobs.add(submit);
		}

		threadExecutor.shutdown();
		while(!threadExecutor.isTerminated()){
		}
		threadCounter.printCounter();
		log.info("  chains reconstructed.");

		reconstructDuration = System.currentTimeMillis() - startTime;
		
		/* Add all vehicles to the new vehicles container. */
		log.info("Adding all vehicles from multi-threaded run...");
		DigicoreVehicles newVehicles = null;
		if(dvs.getCoordinateReferenceSystem() == null) {
			newVehicles = new DigicoreVehicles();
		} else {
			newVehicles = new DigicoreVehicles(dvs.getCoordinateReferenceSystem());
		}
		
		String oldDescription = dvs.getDescription() == null ? "" : dvs.getDescription();
		oldDescription += oldDescription.endsWith(".") ? " " : ". ";
		oldDescription += "Facility Ids added.";
		
		for(Future<DigicoreVehicle> future : listOfJobs){
			try {
				DigicoreVehicle vehicle = future.get();
				if(vehicle != null){
					newVehicles.addDigicoreVehicle(vehicle);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new RuntimeException("Cannot add vehicle during multithreaded consolidation.");
			} catch (ExecutionException e) {
				e.printStackTrace();
				throw new RuntimeException("Cannot add vehicle during multithreaded consolidation.");
			}
		}
		log.info("Done adding all the vehicles.");
		return newVehicles;
	}
	

	/**
	 * This method reads a MATSim facilities file and builds and returns a
	 * {@link QuadTree} of {@link DigicoreFacility}s.
	 * 
	 * @param facilityFile absolute path to facilities.
	 */
	public QuadTree<DigicoreFacility> buildFacilityQuadTree(String facilityFile) {
		long startTime = System.currentTimeMillis();
		log.info("Building QuadTree of facilities...");

		/* Read facilities. */
		MutableScenario sc = (MutableScenario) ScenarioUtils.createScenario(ConfigUtils.createConfig());
		MatsimFacilitiesReader mfr = new MatsimFacilitiesReader(sc);
		mfr.putAttributeConverter(Point.class, new HullConverter());
		mfr.putAttributeConverter(LineString.class, new HullConverter());
		mfr.putAttributeConverter(Polygon.class, new HullConverter());
		mfr.readFile(facilityFile);

		/* Convert each MATSim facility to a specific DigicoreFacility. */
		List<DigicoreFacility> facilityList = new ArrayList<>();
		for(Id<ActivityFacility> id : sc.getActivityFacilities().getFacilities().keySet()){
			ActivityFacility af = sc.getActivityFacilities().getFacilities().get(id); 

			DigicoreFacility df = new DigicoreFacility(id);
			df.setCoord(af.getCoord());
			df.getAttributes().putAttribute(ClusterUtils.ATTR_CONCAVE_HULL,
					af.getAttributes().getAttribute(ClusterUtils.ATTR_CONCAVE_HULL));
			df.getAttributes().putAttribute(ClusterUtils.ATTR_DIGICORE_ACTIVITY_COUNT,
					af.getAttributes().getAttribute(ClusterUtils.ATTR_DIGICORE_ACTIVITY_COUNT));
			
			facilityList.add(df);
		}
		log.info("  " + facilityList.size() + " facilities were identified");

		/* Determine QuadTree extent. */
		double xMin = Double.MAX_VALUE;
		double yMin = Double.MAX_VALUE;
		double xMax = Double.MIN_VALUE;
		double yMax = Double.MIN_VALUE;

		for(DigicoreFacility df : facilityList){
			xMin = Math.min(xMin, df.getCoord().getX());
			xMax = Math.max(xMax, df.getCoord().getX());
			yMin = Math.min(yMin, df.getCoord().getY());
			yMax = Math.max(yMax, df.getCoord().getY());
		}

		QuadTree<DigicoreFacility> facilityTree = new QuadTree<>(xMin, yMin, xMax, yMax);

		/* Populate the QuadTree with the Digicore facilities. */
		for(DigicoreFacility df : facilityList){
			facilityTree.put(df.getCoord().getX(), df.getCoord().getY(), df);
		}

		treeBuildDuration = System.currentTimeMillis() - startTime;
		log.info(" QuadTree built with " + facilityTree.size() + " entries.");

		return facilityTree;
	}

	/* Default constructor */
	public FacilityToActivityAssigner() {

	}

}
