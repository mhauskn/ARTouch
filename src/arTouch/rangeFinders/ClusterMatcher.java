package arTouch.rangeFinders;

import arTouch.CameraCalibrator;
import arTouch.Clusterer;
import arTouch.RangeFinder;
import arTouch.Clusterer.CalibratedPixelAccess;
import arTouch.Clusterer.Cluster;
import arTouch.Clusterer.RasterPixelAccess;

import java.awt.image.Raster;
import java.util.ArrayList;

/**
 * The cluster matcher attempts to find range in the following way:
 * 
 * 1. Find contiguous blocks of "hot" pixels. Hot is defined as the overall
 * 		RGB difference between the colors of a pixel on image1 and the
 * 		corresponding pixel on image2 being greater than a certain threshold.
 * 
 * 2. Shift these clusters of hot pixels over looking for the best 
 * 		shift that gives the lowest overall mass pixel difference.
 * 
 */
public class ClusterMatcher implements RangeFinder {
	CameraCalibrator cameraCalibrator;
	int height, width;
	Raster raster0, raster1;
	ArrayList<Cluster> clusters;
	int diffCallCount = 0;
	RasterPixelAccess rasterPixelAccess = new Clusterer.RasterPixelAccess();
	CalibratedPixelAccess calibratedPixelAccess = new Clusterer.CalibratedPixelAccess();

	/**
	 * The number of contiguous pixels required to form an acceptable cluster
	 */
	public static final int MIN_CLUSTER_THRESHOLD = 1000;

	/**
	 * The total RGB difference necessary to classify a pixel as "hot"
	 */
	public static final int MIN_DIFF_THRESHOLD = 50;

	public ClusterMatcher (CameraCalibrator cameraCalibrator) {
		this.cameraCalibrator = cameraCalibrator;
	}

	public void findRange(Raster raster0, Raster raster1) {
		this.raster0 = raster0;
		this.raster1 = raster1;

		assert (raster0.getBounds().equals(raster1.getBounds()));
		width = raster0.getWidth();
		height = raster0.getHeight();

		rasterPixelAccess.raster = raster0;
		calibratedPixelAccess.cameraCalibrator = this.cameraCalibrator;
		
		clusters = Clusterer.findClusters(rasterPixelAccess, calibratedPixelAccess,
				width, height, MIN_DIFF_THRESHOLD, MIN_CLUSTER_THRESHOLD);
		Clusterer.displayClusters(clusters, width, height, raster0, true);
		diffCallCount = 0;
		long timeStart = System.currentTimeMillis();
		for (Cluster cluster : clusters)
			findQuickShift(cluster);

		System.out.println("-------------- Shift Diff Called " + diffCallCount
				+ " times Time: " + (System.currentTimeMillis() - timeStart));
	}

	/**
	 * Attempts to quickly find the best shift for a cluster by 
	 * shifting the cluster over by large amounts first followed 
	 * by smaller amounts.
	 */
	private void findQuickShift (Cluster cluster) {
		int shiftWidth = cluster.getWidth() / 4;
		int bestShift = 0;
		int leftEdge = -width;
		int rightEdge = width;

		while (shiftWidth > 0) {
			//int start = diffCallCount;
			bestShift = skipShift(bestShift, leftEdge, rightEdge, shiftWidth, 
					cluster);
			//System.out.printf("Checking Shift - Mid: %d L: %d R: %d Width: %d" +
			//		" Call Count: %d\n",
			//		bestShift, leftEdge, rightEdge, shiftWidth, diffCallCount - start);
			leftEdge = bestShift - shiftWidth;
			rightEdge = bestShift + shiftWidth;
			shiftWidth = shiftWidth / 2;
		}
		
		System.out.printf("Patch Size %d. Best Offset %d.\n",
				cluster.size(), bestShift);
	}

	private int skipShift (int startX, int leftEdge, int rightEdge,
			int shiftAmount, Cluster cluster) {
		int currShift = startX;
		int bestShift = startX;
		double bestDiff = Double.MAX_VALUE;

		// Shift Left
		while (currShift > leftEdge) {
			double diff = getShiftDiff(cluster, currShift);
			//double diff = getMonteCarloShiftDiff(cluster, currShift);

			if (diff < bestDiff) {
				bestDiff = diff;
				bestShift = currShift;
			}

			currShift -= shiftAmount;
		}

		currShift = startX + shiftAmount;

		// Shift Right
		while (currShift < rightEdge) {
			double diff = getShiftDiff(cluster, currShift);
			//double diff = getMonteCarloShiftDiff(cluster, currShift);

			if (diff < bestDiff) {
				bestDiff = diff;
				bestShift = currShift;
			}

			currShift += shiftAmount;
		}

		return bestShift;
	}
	
	private double getShiftDiff (Cluster cluster, int shift) {
		diffCallCount++;
		ArrayList<Integer> xLocs = cluster.x;
		ArrayList<Integer> yLocs = cluster.y;
		
		int currDiff = 0;
		int pixelsDiffed = 0;

		for (int i = 0; i < xLocs.size(); i++) {
			int xLoc = xLocs.get(i);
			int yLoc = yLocs.get(i);

			int[] rgb0 = raster0.getPixel(xLoc, yLoc, new int[4]);
			int[] rgb1 = cameraCalibrator.getMatchingPixel(xLoc + shift,
					yLoc);

			if (rgb1 == null)
				continue;

			pixelsDiffed++;

			int pixDiff = 	Math.abs(rgb0[0] - rgb1[0]) +
			Math.abs(rgb0[1] - rgb1[1]) +
			Math.abs(rgb0[2] - rgb1[2]);

			currDiff += pixDiff;
		}

		return currDiff / (double) (pixelsDiffed * pixelsDiffed);
	}

	/**
	 * Attempts to shift a cluster by varying amounts in the horizontal
	 * direction. Ideally at some x-shift we should reach a minimum of 
	 * pixel difference.
	 */
	private void findOptimalShift (Cluster cluster) {
		double bestDiff = Double.MAX_VALUE;
		int bestOffset = 0;

		for (int shift = -width; shift < width; shift++) {
			double totalDiff = getShiftDiff(cluster, shift);

			if (totalDiff < bestDiff) {
				bestDiff = totalDiff;
				bestOffset = shift;
			}
		}
		System.out.printf("Patch Size %d. Best Offset %d. Best Diff %f.\n",
				cluster.size(), bestOffset, bestDiff);
	}
	
	public static void matchClusters (ArrayList<Cluster> fg0Clusters,
			ArrayList<Cluster> fg1Clusters) {
		
		if (fg0Clusters.isEmpty() || fg1Clusters.isEmpty())
			return;
		
		double fg0Avg = 0.0, fg1Avg = 0.0;
		
		for (Cluster c : fg0Clusters)
			fg0Avg += c.getAvgX();
		
		for (Cluster c : fg1Clusters)
			fg1Avg += c.getAvgX();
		
		fg0Avg /= fg0Clusters.size();
		fg1Avg /= fg1Clusters.size();
		
		System.out.println(fg0Avg - fg1Avg);
		//System.out.println("Cluster0Avg: " + fg0Avg + " Cluster1Avg: " + fg1Avg);
	}
}
