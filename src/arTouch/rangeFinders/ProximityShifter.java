package arTouch.rangeFinders;

import arTouch.CameraCalibrator;
import arTouch.RangeFinder;

import java.awt.image.Raster;

/**
 * Attempts to find pixel offsets by a balancing RGB color match
 * with proximity toward neighbors.
 */
public class ProximityShifter implements RangeFinder {
	Raster raster0, raster1;
	int numPixelShifts = 1;
	int width, height;
	int[][] bestXShift;
	CameraCalibrator cameraCalibrator;
	
	public ProximityShifter (CameraCalibrator cameraCalibrator) {
		this.cameraCalibrator = cameraCalibrator;
	}
	
	public void findRange (Raster raster0, Raster raster1) {
		this.raster0 = raster0;
		this.raster1 = raster1;
		
		width = raster0.getWidth();
		height = raster0.getHeight();
		
		bestXShift = new int[width][height];
		
		while (numPixelShifts > 0) {
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int[] rgb1 = cameraCalibrator.getMatchingPixel(x, y);
					
					if (rgb1 == null)
						continue;
					
					int[] rgb0 = raster0.getPixel(x, y, new int[4]);
					double happiness = 0.0;
					
					for (int xShift = -width; xShift < width; xShift++) {
						rgb1 = cameraCalibrator.getMatchingPixel(x + xShift, y);
						
						if (rgb1 == null)
							continue;
						
						double newHappiness = getPixelHappiness();
						if (newHappiness > happiness) {
							
						}
					}
				}
			}
		}
	}
	
	private int getPixelHappiness () {
		return getRGBMatch() + getProximity();
	}
	
	private int getRGBMatch () {
		return 0;
	}
	
	private int getProximity () {
		return 0;
	}
}
