package arTouch;

import java.awt.image.Raster;
import java.util.ArrayList;

import arTouch.Clusterer.Cluster;
import arTouch.Clusterer.RasterPixelAccess;
import arTouch.Clusterer.pixelAccess;

/**
 * This class will subtract away the background, leaving only the 
 * foreground image to be processed.
 * @author el
 *
 */
public class BGSubtractor {
	int[][] bg0R, bg0G, bg0B, bg1R, bg1G, bg1B;
	int width, height;
	RasterPixelAccess raster0PixelAccess = new Clusterer.RasterPixelAccess();
	RasterPixelAccess raster1PixelAccess = new Clusterer.RasterPixelAccess();
	BG0PixelAccess bg0PixelAccess = new BG0PixelAccess();
	BG1PixelAccess bg1PixelAccess = new BG1PixelAccess();
	boolean bg0Saved = false, bg1Saved = false;
	
	/**
	 * The number of contiguous pixels required to form an acceptable cluster
	 */
	public static final int MIN_CLUSTER_THRESHOLD = 1000;

	/**
	 * The total RGB difference necessary to classify a pixel as "hot"
	 */
	public static final int MIN_DIFF_THRESHOLD = 30;
	
	private void saveBG0 (Raster raster0) {
		bg0R = new int[width][height];
		bg0G = new int[width][height];
		bg0B = new int[width][height];

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] rgb0 = raster0.getPixel(x, y, new int[4]);
				
				bg0R[x][y] = rgb0[0];
				bg0G[x][y] = rgb0[1];
				bg0B[x][y] = rgb0[2];
			}
		}
	}
	
	private void saveBG1 (Raster raster1) {
		bg1R = new int[width][height];
		bg1G = new int[width][height];
		bg1B = new int[width][height];

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int[] rgb1 = raster1.getPixel(x, y, new int[4]);
				
				bg1R[x][y] = rgb1[0];
				bg1G[x][y] = rgb1[1];
				bg1B[x][y] = rgb1[2];
			}
		}
	}
	
	public class BG0PixelAccess implements pixelAccess {
		public int[] getPixel(int x, int y) {
			int[] rgb = new int[4];
			rgb[0] = bg0R[x][y];
			rgb[1] = bg0G[x][y];
			rgb[2] = bg0B[x][y];
			rgb[3] = 255;
			return rgb;
		}
	}
	
	public class BG1PixelAccess implements pixelAccess {
		public int[] getPixel(int x, int y) {
			int[] rgb = new int[4];
			rgb[0] = bg1R[x][y];
			rgb[1] = bg1G[x][y];
			rgb[2] = bg1B[x][y];
			rgb[3] = 255;
			return rgb;
		}
	}
	
	public ArrayList<Cluster> getForeground0 (Raster raster0) {
		if (!bg0Saved) {
			width = raster0.getWidth();
			height = raster0.getHeight();
			saveBG0(raster0);
			bg0Saved = true;
		}
		
		raster0PixelAccess.raster = raster0;
		return Clusterer.findClusters(raster0PixelAccess, bg0PixelAccess, width, 
				height, MIN_DIFF_THRESHOLD, MIN_CLUSTER_THRESHOLD);
	}
	
	public ArrayList<Cluster> getForeground1 (Raster raster1) {
		if (!bg1Saved) {
			width = raster1.getWidth();
			height = raster1.getHeight();
			saveBG1(raster1);
			bg1Saved = true;
		}
		
		raster1PixelAccess.raster = raster1;
		return Clusterer.findClusters(raster1PixelAccess, bg1PixelAccess, width, 
				height, MIN_DIFF_THRESHOLD, MIN_CLUSTER_THRESHOLD);
	}
}
