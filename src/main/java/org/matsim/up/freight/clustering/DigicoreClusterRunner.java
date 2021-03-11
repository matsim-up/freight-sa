/* *********************************************************************** *
 * project: org.matsim.*
 * DigicoreClusterRunner.java
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.network.algorithms.intersectionSimplifier.HullConverter;
//import org.matsim.core.network.algorithms.intersectionSimplifier.containers.ClusterActivity;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacilitiesImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.up.freight.clustering.containers.*;
import org.matsim.up.freight.containers.DigicoreVehicle;
import org.matsim.up.freight.containers.DigicoreVehicles;
import org.matsim.up.freight.io.DigicoreVehiclesReader;
import org.matsim.up.utils.FileUtils;
import org.matsim.up.utils.Header;
import org.matsim.utils.objectattributes.AttributeConverter;
import org.matsim.utils.objectattributes.attributable.Attributes;


/**
 * Class to cluster the activities of Digicore vehicles' activity chains using
 * the {@link DJCluster} approach. Once clustered, the activity chains are <i>not</i>
 * adjusted. Rather, the clustering outputs can be used as inputs to a class
 * such as  {@link org.matsim.up.freight.clustering.postclustering.FacilityToActivityAssigner}.
 *
 * @author jwjoubert
 */
public class DigicoreClusterRunner {
    private final static Logger LOG = Logger.getLogger(DigicoreClusterRunner.class);
    private final static int BLOCK_SIZE = 100;

    private final int numberOfThreads;
    private Map<Id<MyZone>, List<Coord>> zoneMap = null;
    private ActivityFacilities facilities;

    /**
     * Clustering the minor activities from Digicore vehicle chains. The following
     * parameters are required, and in the following order:
     *
     * @param args the following arguments:
     *             <ol>
     *                 <li> the input source. This may be an absolute path of the folder
     *                         containing the Digicore vehicle files, in XML-format, or the
     *                         {@link DigicoreVehicles} container file. The former (XML folder)
     *                         is deprecated but still retained for backward compatibility.
     *                 <li> The shapefile within which activities will be clustered. Activities
     *                         outside the shapefile are ignored. NOTE: It is actually recommended
     *                         that smaller demarcation areas, such as the Geospatial Analysis
     *                         Platform (GAP) zones, be used.
     *                 <li> Field of the shapefile that will be used as identifier;
     *                 <li> Number of threads to use for the run;
     *                 <li> Absolute path of the output folder to which the facilities,
     *                         facility attributes, and the facility CSV file will be written.
     *              </ol>
     */
    public static void main(String[] args) {
        long jobStart = System.currentTimeMillis();
        Header.printHeader(DigicoreClusterRunner.class, args);

        String input = args[0];
        String shapefile = args[1];
        int idField = Integer.parseInt(args[2]);
        int numberOfThreads = Integer.parseInt(args[3]);
        String outputFolderName = args[4];

        /* Read all the `minor' DigicoreActivities from the *.xml.gz Vehicle files. */
        LOG.info(" Reading points to cluster...");
        DigicoreClusterRunner dcr = new DigicoreClusterRunner(numberOfThreads);
        try {
            dcr.buildPointLists(input, shapefile, idField);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not build minor points list.");
        }
        long readTime = System.currentTimeMillis() - jobStart;

        /* Cluster the points. */
        LOG.info("-------------------------------------------------------------");
        LOG.info(" Clustering the points...");

        /* These values should be set following Meintjes and Joubert, City Logistics paper?
         * Update (March 2021, JWJ): rather use Dirk de Beer's first PhD paper's values. */
        double[] radii = {16.6}; ////, 10, 15, 20, 25, 30, 35, 40};
        int[] pmins = {11}; //, 10, 15, 20, 25};

        for (double thisRadius : radii) {
            for (int thisPmin : pmins) {
                /* Just write some indication to the log file as to what we're
                 * busy with at this point in time. */
                LOG.info("================================================================================");
                LOG.info("Executing clustering for radius " + thisRadius + ", and pmin of " + thisPmin);
                LOG.info("================================================================================");

                /* Create configuration-specific filenames. */
                String outputFolder = String.format(Locale.US, "%s%.1f_%d/", outputFolderName, thisRadius, thisPmin);
                String theFacilityFile = outputFolder + String.format(Locale.US, "%.1f_%d_%s", thisRadius, thisPmin, ClusterUtils.SUFFIX_FILENAME_FACILITIES_XML);
                String theFacilityCsvFile = outputFolder + String.format(Locale.US, "%.1f_%d_%s", thisRadius, thisPmin, ClusterUtils.SUFFIX_FILENAME_FACILITIES_CSV);
                String facilityPointFolder = String.format(Locale.US,"%s%s", outputFolder, ClusterUtils.SUFFIX_FACILITY_FOLDER);

                /* Create the output folders. If it exists... first delete it. */
                File folder = new File(outputFolder);
                if (folder.exists()) {
                    LOG.warn("Output folder exists, and will be deleted. ");
                    LOG.warn("  --> " + folder.getAbsolutePath());
                    FileUtils.delete(folder);
                }
                //noinspection ResultOfMethodCallIgnored
                folder.mkdirs();

                /* Cluster. */
                dcr.facilities = FacilitiesUtils.createActivityFacilities(String.format(Locale.US, "Digicore clustered facilities: %.1f (radius); %d (pmin)", thisRadius, thisPmin));
                try {
                    dcr.clusterPointLists(thisRadius, thisPmin, facilityPointFolder);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e.getMessage());
                }

                /* Write output. */
                dcr.writeOutput(theFacilityFile);
                dcr.writePrettyCsv(theFacilityCsvFile);
            }
        }
        long clusterTime = System.currentTimeMillis() - jobStart - readTime;

        long totalTime = System.currentTimeMillis() - jobStart;
        LOG.info("-------------------------------------------------------------");
        LOG.info("  Done.");
        LOG.info("-------------------------------------------------------------");
        LOG.info("    Read time (s): " + readTime / 1000);
        LOG.info(" Cluster time (s): " + clusterTime / 1000);
        LOG.info("   Total time (s): " + totalTime / 1000);
        LOG.info("=============================================================");

    }


    /**
     * Write the {@link ActivityFacilities} to file along with the
     * {@link ConcaveHull} and number of points as {@link Attributes}.
     *
     * @param theFacilityFile absolute path of facilities file.
     */
    private void writeOutput(String theFacilityFile) {
        /* Write (for the current configuration) facilities, and the attributes, to file. */
        LOG.info("-------------------------------------------------------------");
        LOG.info(" Writing the facilities to file: " + theFacilityFile);
        FacilitiesWriter fw = new FacilitiesWriter(facilities);
        Map<Class<?>, AttributeConverter<?>> converters = new HashMap<>();
        converters.put(Point.class, new HullConverter());
        converters.put(LineString.class, new HullConverter());
        converters.put(Polygon.class, new HullConverter());
        fw.putAttributeConverters(converters);
        fw.write(theFacilityFile);
    }

    private void writePrettyCsv(String theFacilityCsvFile) {
        /* Write out pretty CSV file. */
        LOG.info(" Writing the facilities to csv: " + theFacilityCsvFile);
        BufferedWriter bw = IOUtils.getBufferedWriter(theFacilityCsvFile);
        try {
            bw.write("id,x,y,Count");
            bw.newLine();
            for (Id<ActivityFacility> id : this.facilities.getFacilities().keySet()) {
                ActivityFacility af = this.facilities.getFacilities().get(id);
                bw.write(id.toString());
                bw.write(",");
                bw.write(String.format(Locale.US, "%.1f,%.1f,", af.getCoord().getX(), af.getCoord().getY()));
                bw.write(String.valueOf(af.getAttributes().getAttribute(ClusterUtils.ATTR_DIGICORE_ACTIVITY_COUNT)));
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void clusterPointLists(double radius, int minimumPoints, String outputFolder) throws Exception {
        /* FIXME This counter checks how many facilities are ignored.
         * This is because the concave hull algorithm still returns
         * empty geometries. */
        int numberOfFacilitiesOmitted = 0;

        File folder = new File(outputFolder);
        if (folder.exists()) {
            LOG.warn("Facility points folder exists, and will be deleted. ");
            LOG.warn("  --> " + folder.getAbsolutePath());
            FileUtils.delete(folder);
        }
        boolean makeDirectory = folder.mkdirs();
        if(!makeDirectory){
            LOG.warn("Could not create the output folder.");
        }

        /* Check that zone maps have been read. */
        if (this.zoneMap == null) {
            throw new Exception("Must first read activities before you can cluster!");
        }

        ExecutorService threadExecutor = Executors.newFixedThreadPool(this.numberOfThreads);

        /* Break up the thread execution into blocks. */


        List<Future<List<DigicoreCluster>>> listOfJobs = new ArrayList<>();

        Counter counter = new Counter("   Zones completed: ");
        /* Submit the clustering jobs to the different threads. */
        for (Id<MyZone> id : zoneMap.keySet()) {
            Callable<List<DigicoreCluster>> job = new DigicoreClusterCallable(zoneMap.get(id), radius, minimumPoints, counter);
            Future<List<DigicoreCluster>> submit = threadExecutor.submit(job);
            listOfJobs.add(submit);
        }

        threadExecutor.shutdown();
        while (!threadExecutor.isTerminated()) {
        }
        counter.printCounter();

        int i = 0;
        for (Future<List<DigicoreCluster>> future : listOfJobs) {
            try {
                List<DigicoreCluster> list = future.get();
                for (DigicoreCluster dc : list) {
                    Id<ActivityFacility> facilityId = Id.create(i++, ActivityFacility.class);

                    /* Construct the concave hull for the clustered points. */
                    List<ClusterActivity> dcPoints = dc.getPoints();
                    if (dcPoints.size() > 0) {
                        GeometryFactory gf = new GeometryFactory();
                        Geometry[] ga = new Geometry[dcPoints.size()];
                        for (int j = 0; j < dcPoints.size(); j++) {
                            ga[j] = gf.createPoint(new Coordinate(dcPoints.get(j).getCoord().getX(), dcPoints.get(j).getCoord().getY()));
                        }

                        GeometryCollection points = new GeometryCollection(ga, gf);

                        ConcaveHull ch = new ConcaveHull(points, 10);
                        Geometry hull = ch.getConcaveHull(facilityId.toString());

                        /*FIXME For some reason there are empty hulls. For now
                         * we are only creating facilities for those with a valid
                         * Geometry for a hull: point, line or polygon.*/
                        if (!hull.isEmpty()) {
                            dc.setConcaveHull(hull);
                            dc.setCenterOfGravity();

                            ActivityFacility af = facilities.getFactory().createActivityFacility(facilityId, dc.getCenterOfGravity());
                            facilities.addActivityFacility(af);

                            af.getAttributes().putAttribute(ClusterUtils.ATTR_DIGICORE_ACTIVITY_COUNT, String.valueOf(dc.getPoints().size()));
                            af.getAttributes().putAttribute(ClusterUtils.ATTR_CONCAVE_HULL, hull);
                        } else {
                            LOG.debug("Facility " + facilityId.toString() + " is not added. Hull is an empty geometry!");
                            numberOfFacilitiesOmitted++;
                        }
                    }

                    /* First, remove duplicate points.
                     * TODO Consider the UniqueCoordinateArrayFilter class from vividsolutions.*/
                    List<Coord> coordList = new ArrayList<Coord>();
                    for (ClusterActivity ca : dc.getPoints()) {
                        if (!coordList.contains(ca.getCoord())) {
                            coordList.add(ca.getCoord());
                        }
                    }

                    /*TODO If we want to, we need to write all the cluster members out to file HERE.
                     * Update (20130627): Or, rather write out the concave hull. */
                    /* FIXME Consider 'not' writing the facilities to file, as
                     * this takes up a HUGE amount of disk space (JWJ Nov '13) */
                    String clusterFile = String.format(Locale.US, "%s%.1f_%d_points_%s.csv.gz", outputFolder, radius, minimumPoints, facilityId.toString());
                    BufferedWriter bw = IOUtils.getBufferedWriter(clusterFile);
                    try {
                        bw.write("Long,Lat");
                        bw.newLine();
                        for (Coord c : coordList) {
                            bw.write(String.format(Locale.US, "%f, %f\n", c.getX(), c.getY()));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Could not write to " + clusterFile);
                    } finally {
                        try {
                            bw.close();
                        } catch (IOException e) {
                            throw new RuntimeException("Could not close " + clusterFile);
                        }
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("InterruptedException caught in retrieving thread results.");
            } catch (ExecutionException e) {
                throw new RuntimeException("ExecutionException caught in retrieving thread results.");
            }
        }
        ActivityFacilitiesImpl r = ((ActivityFacilitiesImpl) facilities);

        LOG.info("    facility # " + r.getFacilities().size());

        /*TODO Can remove after debugging. Report the number of
         * facilities that were ignored because of empty geometries. */
        LOG.debug("Facilities omitted: " + radius + "_" + minimumPoints + "(" + numberOfFacilitiesOmitted + ")");
    }


    /**
     * Reads all activities from extracted Digicore vehicle files in a (possibly)
     * multi-threaded manner. This used to only read in 'minor' points, but since
     * July 2013, it now reads in <i>all</i> activity types.
     *
     * @param source
     * @param shapefile
     * @param idField
     * @throws IOException
     */
    private void buildPointLists(String source, String shapefile, int idField) throws IOException {
        MyMultiFeatureReader mfr = new MyMultiFeatureReader();
        mfr.readMultizoneShapefile(shapefile, idField);
        List<MyZone> zoneList = mfr.getAllZones();

        /* Build a QuadTree of the Zones. */
        LOG.info(" Building QuadTree from zones...");
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (MyZone mz : zoneList) {
            minX = Math.min(minX, mz.getEnvelope().getCoordinates()[0].x);
            maxX = Math.max(maxX, mz.getEnvelope().getCoordinates()[2].x);
            minY = Math.min(minY, mz.getEnvelope().getCoordinates()[0].y);
            maxY = Math.max(maxY, mz.getEnvelope().getCoordinates()[2].y);
        }
        QuadTree<MyZone> zoneQT = new QuadTree<MyZone>(minX, minY, maxX, maxY);
        for (MyZone mz : zoneList) {
            zoneQT.put(mz.getEnvelope().getCentroid().getX(), mz.getEnvelope().getCentroid().getY(), mz);
        }
        LOG.info("Done building QuadTree.");

        /* Read the activities from vehicle files. If the input is a single
         * DigicoreVehicles file, then the single (V2) container will be read,
         * and each vehicle will be passed to the multi-threaded infrastructure.
         * Alternatively, if the input is a folder containing individual (V1)
         * DigicoreVehicle files, then they will be sampled, and each will be
         * read by the multi-threaded infrastructure. */
        long startTime = System.currentTimeMillis();


        List<Object> vehicles = new ArrayList<>();
        File folder = new File(source);
        if (folder.isFile() && source.endsWith("xml.gz")) {
            /* It is a V2 DigicoreVehicles container. */
            DigicoreVehicles dvs = new DigicoreVehicles();
            new DigicoreVehiclesReader(dvs).readFile(source);
            vehicles.addAll(dvs.getVehicles().values());
        } else if (folder.isDirectory()) {
            /* It is a folder with individual V1 DigicoreVehicle files. */
            List<File> vehicleList = FileUtils.sampleFiles(folder, Integer.MAX_VALUE, FileUtils.getFileFilter("xml.gz"));
            vehicles.addAll(vehicleList);
        }
        int inActivities = 0;
        int outActivities = 0;


        /* Set up the infrastructure so that threaded code is executed in blocks. */
        ExecutorService threadExecutor;
        List<DigicoreActivityReaderRunnable> threadList;
        int vehicleCounter = 0;
        Counter counter = new Counter("   Vehicles completed: ");

        /* Set up the output infrastructure:
         * Create a new map with an empty list for each zone. These will be
         * passed to threads later. */
        zoneMap = new HashMap<>();
        for (MyZone mz : zoneList) {
            zoneMap.put(mz.getId(), new ArrayList<>());
        }
        Map<Id<MyZone>, List<Coord>> theMap;

        while (vehicleCounter < vehicles.size()) {
            int blockCounter = 0;
            threadExecutor = Executors.newFixedThreadPool(this.numberOfThreads);
            threadList = new ArrayList<>();

            /* Assign the jobs in blocks. */
            while (blockCounter++ < BLOCK_SIZE && vehicleCounter < vehicles.size()) {
                Object o = vehicles.get(vehicleCounter++);
                DigicoreActivityReaderRunnable rdar;
                if (o instanceof DigicoreVehicle) {
                    DigicoreVehicle vehicle = (DigicoreVehicle) o;
                    rdar = new DigicoreActivityReaderRunnable(vehicle, zoneQT, counter);
                } else if (o instanceof File) {
                    // This is just kept for backward compatability.
                    File vehicleFile = (File) o;
                    rdar = new DigicoreActivityReaderRunnable(vehicleFile, zoneQT, counter);
                } else {
                    throw new RuntimeException("Don't know what to do with a list with types " + o.getClass().toString());
                }

                threadList.add(rdar);
                threadExecutor.execute(rdar);
            }

            /* Shut down the thread executor for this block, and wait until it
             * is finished before proceeding. */
            threadExecutor.shutdown();
            while (!threadExecutor.isTerminated()) {
            }

            /* Aggregate the results of the current block. */
            /* Add all the coordinates from each vehicle to the main map. */
            for (DigicoreActivityReaderRunnable rdar : threadList) {
                theMap = rdar.getMap();
                for (Id<MyZone> id : theMap.keySet()) {
                    zoneMap.get(id).addAll(theMap.get(id));
                }
                inActivities += rdar.getInCount();
                outActivities += rdar.getOutCount();
            }
        }
        counter.printCounter();

        long time = (System.currentTimeMillis() - startTime) / 1000;
        int totalPoints = inActivities + outActivities;
        LOG.info("Total number of activities checked: " + totalPoints);
        LOG.info("   In: " + inActivities);
        LOG.info("  Out: " + outActivities);
        LOG.info("Time (s): " + time);
    }

    public DigicoreClusterRunner(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        facilities = FacilitiesUtils.createActivityFacilities("Digicore facilities");
    }


}

