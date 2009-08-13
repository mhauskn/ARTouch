import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

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

		clusters = findClusters();
		displayClusters();
		diffCallCount = 0;
		long timeStart = System.currentTimeMillis();
		for (Cluster cluster : clusters)
			findQuickShift(cluster);

		System.out.println("-------------- Shift Diff Called " + diffCallCount
				+ " times Time: " + (System.currentTimeMillis() - timeStart));
	}
	
	private void shiftRectangles () {
		
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
			int start = diffCallCount;
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
	 * The cluster class is a simple way to store lists of x,y points.
	 */
	public class Cluster {
		public ArrayList<Integer> x = new ArrayList<Integer>();
		public ArrayList<Integer> y = new ArrayList<Integer>();
		public int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;

		public void add (int x, int y) {
			this.x.add(x);
			this.y.add(y);

			if (x < minX)
				minX = x;
			if (x > maxX)
				maxX = x;
		}

		public int size () {
			return x.size();
		}

		public String toString() {
			return "" + size();
		}

		/**
		 * Provides a rough estimate of the width of this cluster
		 */
		public int getWidth () {
			return maxX - minX;
		}
	}

	/**
	 * Paints each cluster red and displays the resulting image
	 */
	private void displayClusters () {
		BufferedImage imageOut = new BufferedImage(width, height, 
				BufferedImage.TYPE_INT_ARGB);

		WritableRaster rasterOut = imageOut.getRaster();

		rasterOut.setDataElements(0, 0, raster0);

		for (Cluster cluster : clusters) {
			ArrayList<Integer> xLocs = cluster.x;
			ArrayList<Integer> yLocs = cluster.y;

			for (int i = 0; i < xLocs.size(); i++) {
				int[] rgba = rasterOut.getPixel(xLocs.get(i), yLocs.get(i),
						new int[4]);
				rgba[0] = 255;
				rasterOut.setPixel(xLocs.get(i), yLocs.get(i), new int[] { 255,0,0,255});
			}
		}

		DualViewer.displayImage(imageOut);
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

	/**
	 * Performs a single scan through the image looking for contiguous 
	 * "hot" pixels. These contiguous hot pixels are segmented into 
	 * clusters. Only clusters over a certain threshold are returned.
	 */
	private ArrayList<Cluster> findClusters () {
		Hashtable<Cluster, Boolean> clusters = 
			new Hashtable<Cluster,Boolean>();

		Hashtable<Integer,Cluster> clusterCoords = 
			new Hashtable<Integer,Cluster>();

		Cluster currentCluster = null;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] rgb0 = raster0.getPixel(x, y, new int[4]);
				int[] rgb1 = cameraCalibrator.getMatchingPixel(x, y);

				if (rgb1 == null)
					continue;

				int pixDiff = Math.abs(rgb0[0] - rgb1[0]) +
				Math.abs(rgb0[1] - rgb1[1]) +
				Math.abs(rgb0[2] - rgb1[2]);

				if (pixDiff < MIN_DIFF_THRESHOLD) {
					currentCluster = null;
					clusterCoords.remove(x);
					continue;
				}

				if (currentCluster != null && clusterCoords.containsKey(x) &&
						!clusterCoords.get(x).equals(currentCluster)) {
					// merge clusters
					Cluster other = clusterCoords.get(x);
					Cluster larger, smaller;

					if (currentCluster.size() > other.size()) {
						larger = currentCluster;
						smaller = other;
					} else {
						larger = other;
						smaller = currentCluster;
					}

					ArrayList<Integer> smallerX = smaller.x;
					ArrayList<Integer> smallerY = smaller.y;

					for (int i = 0; i < smallerX.size(); i++) {
						larger.add(smallerX.get(i), smallerY.get(i));
						if (smallerY.get(i) >= y - 1) {
							//TODO: Why does this assertion fail?
							//assert clusterCoords.containsKey(smallerX.get(i));
							clusterCoords.put(smallerX.get(i), larger);
						}
					}
					clusters.remove(smaller);
					currentCluster = larger;
				} else if (clusterCoords.containsKey(x))
					currentCluster = clusterCoords.remove(x);

				if (currentCluster == null) { // Create a new cluster
					Cluster newCluster = new Cluster();
					newCluster.add(x,y);

					clusters.put(newCluster, true);
					clusterCoords.put(x, newCluster);
					currentCluster = newCluster;
				} else { // Expand Current Cluster
					currentCluster.add(x,y);
					clusterCoords.put(x, currentCluster);
					if (x > 0)
						clusterCoords.put(x-1, currentCluster);
				}
			}
		}
		return findLargeClusters(clusters);
	}

	/**
	 * Returns a list containing only the clusters over a certain
	 * threshold.
	 */
	private ArrayList<Cluster> findLargeClusters 
	(Hashtable<Cluster,Boolean> clusterHT) {
		ArrayList<Cluster> out = new ArrayList<Cluster>();
		Enumeration<Cluster> clusters = clusterHT.keys();

		while (clusters.hasMoreElements()) {
			Cluster c = clusters.nextElement();
			if (c.size() > MIN_CLUSTER_THRESHOLD)
				out.add(c);
		}

		return out;
	}
}
