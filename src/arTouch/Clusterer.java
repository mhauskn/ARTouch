package arTouch;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * Creates clusters of interesting pixels
 * @author el
 *
 */
public class Clusterer {
	/**
	 * The cluster class is a simple way to store lists of x,y points.
	 */
	public static class Cluster {
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
		
		public double getAvgX () {
			int sum = 0;
			
			for (int xVal : x)
				sum += xVal;
			
			return sum / (double) x.size();
		}
	}
	
	public interface pixelAccess {
		int[] getPixel (int x, int y);
	}
	
	/**
	 * Simple pixel access class -- gets pixel information
	 * directly from the raster.
	 */
	public static class RasterPixelAccess implements pixelAccess {
		public Raster raster;
		
		public int[] getPixel(int x, int y) {
			return raster.getPixel(x, y, new int[4]);
		}
	}
	
	public static class CalibratedPixelAccess implements pixelAccess {
		public CameraCalibrator cameraCalibrator;

		public int[] getPixel(int x, int y) {
			return cameraCalibrator.getMatchingPixel(x, y);
		}
	}
	
	/**
	 * Performs a single scan through the image looking for contiguous 
	 * "hot" pixels. These contiguous hot pixels are segmented into 
	 * clusters. Only clusters over a certain threshold are returned.
	 */
	public static ArrayList<Cluster> findClusters (pixelAccess pa0, pixelAccess pa1,
			int width, int height, int minDiffThreshold, int minClusterSize) {
		Hashtable<Cluster, Boolean> clusters = 
			new Hashtable<Cluster,Boolean>();

		Hashtable<Integer,Cluster> clusterCoords = 
			new Hashtable<Integer,Cluster>();

		Cluster currentCluster = null;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] rgb0 = pa0.getPixel(x, y);
				int[] rgb1 = pa1.getPixel(x, y);

				if (rgb0 == null || rgb1 == null)
					continue;

				int pixDiff = Math.abs(rgb0[0] - rgb1[0]) +
				Math.abs(rgb0[1] - rgb1[1]) +
				Math.abs(rgb0[2] - rgb1[2]);

				if (pixDiff < minDiffThreshold) {
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
		return findLargeClusters(clusters, minClusterSize);
	}
	
	/**
	 * Returns a list containing only the clusters over a certain
	 * threshold.
	 */
	private static ArrayList<Cluster> findLargeClusters 
	(Hashtable<Cluster,Boolean> clusterHT, int minClusterSize) {
		ArrayList<Cluster> out = new ArrayList<Cluster>();
		Enumeration<Cluster> clusters = clusterHT.keys();

		while (clusters.hasMoreElements()) {
			Cluster c = clusters.nextElement();
			if (c.size() > minClusterSize)
				out.add(c);
		}

		return out;
	}
	
	/**
	 * Paints each cluster red and displays the resulting image
	 */
	public static void displayClusters (ArrayList<Cluster> clusters, int width,
			int height, Raster base, boolean left) {
		BufferedImage imageOut = new BufferedImage(width, height, 
				BufferedImage.TYPE_INT_ARGB);

		WritableRaster rasterOut = imageOut.getRaster();

		rasterOut.setDataElements(0, 0, base);

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

		if (left)
			DualViewer.displayImageLeft(imageOut);
		else
			DualViewer.displayImageRight(imageOut);
	}
}
