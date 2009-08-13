import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * Calibrates two cameras. This consists of two things: 
 * 
 * 1. Shift the image of the second camera to match that of the first camera.
 * 2. Distort the colors of the second camera to match those of the first.
 */
public class CameraCalibrator {
	private boolean initialCalibrationDone = false;
	int offsetX = -17, offsetY = 9;
	int height, width;
	int globalOffsetR = 0, globalOffsetG = 0, globalOffsetB = 0;
	int[][] individualOffsetR, individualOffsetG, individualOffsetB;
	Raster raster0, raster1;

	/**
	 * Checks to make sure that both cameras remain in calibration. If 
	 * necessary this method will also perform initial calibration of 
	 * the cameras.
	 */
	public void checkCameraCalibration (Raster raster0, Raster raster1) {
		this.raster0 = raster0;
		this.raster1 = raster1;

		assert (raster0.getBounds().equals(raster1.getBounds()));
		width = raster0.getWidth();
		height = raster0.getHeight();

		if (!initialCalibrationDone) {
			performInitialCalibration();
			initialCalibrationDone = true;
		}
		
		if (((int) (Math.random() * 10)) == 0)
			findGlobalRGBOffset();
	}

	/**
	 * Given the x and y coordinates of a pixel from camera1,
	 * this method returns the rgba values from the color adjusted
	 * camera2 equivalent pixel.
	 */
	public int[] getMatchingPixel (int x, int y) {
		if (x >= width || x < 0 || y >= height || y < 0)
			return null;
		if (x + offsetX >= width || x + offsetX < 0)
			return null;
		if (y + offsetY >= height || y + offsetY < 0)
			return null;

		int[] rgba = raster1.getPixel(x + offsetX, y + offsetY, new int[4]);
		//return adjustColorGlobally(rgba);
		return adjustColorLocally(x,y,rgba);
	}

	/**
	 * Aligns camera background images and calibrates their 
	 * color settings. 
	 */
	private void performInitialCalibration () { 
		findGlobalRGBOffset();
		findBestImageOffset();
		findGlobalRGBOffset();
		findIndividualRGBOffset();
	}

	/**
	 * Attempts to overlap the images between both cameras by minimizing
	 * the total pixel error.
	 */
	private void findBestImageOffset () {
		long timeStart = System.currentTimeMillis();
		double bestDiff = Double.MAX_VALUE;

		BufferedImage imageOut = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		int consecutiveFail = 0, consecutiveFailThreshold = 20;

		while (consecutiveFail < consecutiveFailThreshold) {
			WritableRaster rasterOut = imageOut.getRaster();

			int totalDiff = 0;
			int pixelsDiffed = 0;

			int xShift = ((int) (Math.random() * 3)) - 1;
			int yShift = ((int) (Math.random() * 3)) - 1;

			int currXOffset = offsetX + xShift;
			int currYOffset = offsetY + yShift;

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					rasterOut.setPixel(x, y, new int[] {0,0,0,255});
					if (x + currXOffset >= width || x + currXOffset < 0)
						continue;
					if (y + currYOffset >= height || y + currYOffset < 0)
						continue;

					int[] rgb0 = raster0.getPixel(x, y, new int[4]);
					int[] rgb1 = raster1.getPixel(x + currXOffset, 
							y + currYOffset, 
							new int[4]);
					rgb1 = adjustColorGlobally(rgb1);

					int[] pixAvg = new int[4];
					pixAvg[0] = (rgb0[0] + rgb1[0]) / 2;
					pixAvg[1] = (rgb0[1] + rgb1[1]) / 2;
					pixAvg[2] = (rgb0[2] + rgb1[2]) / 2;
					pixAvg[3] = 255;

					pixelsDiffed++;

					int pixDiff = Math.abs(rgb0[0] - rgb1[0]) +
					Math.abs(rgb0[1] - rgb1[1]) +
					Math.abs(rgb0[2] - rgb1[2]);

					totalDiff += pixDiff;

					rasterOut.setPixel(x, y, pixAvg);
				}
			}

			double currDiff = totalDiff / (double) pixelsDiffed;			

			if (currDiff < bestDiff) {
				DualViewer.displayImage(imageOut);

				offsetX = currXOffset;
				offsetY = currYOffset;
				bestDiff = currDiff;
				consecutiveFail = 0;
			} else {
				consecutiveFail++;
			}
		}

		System.out.printf("Found Image Offset: X-Offset:%d Y-Offset:%d" +
				" Error: %f ... %dms\n",
				offsetX, offsetY, bestDiff, 
				System.currentTimeMillis() - timeStart);
	}

	/**
	 * Looks at the average RGB values of all pixels for each 
	 * webcam and comes up with a rough way to map colors 
	 * from one camera to the other.
	 */
	private void findGlobalRGBOffset () {
		long timeStart = System.currentTimeMillis();
		int primaryR = 0, primaryG = 0, primaryB = 0;
		int secondaryR = 0, secondaryG = 0, secondaryB = 0;

		int pixelsScanned = 0;

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (x + offsetX >= width || x + offsetX < 0)
					continue;
				if (y + offsetY >= height || y + offsetY < 0)
					continue;

				pixelsScanned++;

				int[] rgb0 = raster0.getPixel(x, y, new int[4]);
				int[] rgb1 = raster1.getPixel(x + offsetX, 
						y + offsetY, new int[4]);

				primaryR += rgb0[0];
				primaryG += rgb0[1];
				primaryB += rgb0[2];

				secondaryR += rgb1[0];
				secondaryG += rgb1[1];
				secondaryB += rgb1[2];
			}
		}

		int primaryAvgR = primaryR / pixelsScanned;
		int primaryAvgG = primaryG / pixelsScanned;
		int primaryAvgB = primaryB / pixelsScanned;

		int secondaryAvgR = secondaryR / pixelsScanned;
		int secondaryAvgG = secondaryB / pixelsScanned;
		int secondaryAvgB = secondaryG / pixelsScanned;

		globalOffsetR = primaryAvgR - secondaryAvgR;
		globalOffsetG = primaryAvgG - secondaryAvgG;
		globalOffsetB = primaryAvgB - secondaryAvgB;

		System.out.printf("Global RGB Offset: R:%d G:%d B:%d ... %dms\n", 
				globalOffsetR,globalOffsetG, globalOffsetB, 
				System.currentTimeMillis() - timeStart);
	}

	/**
	 * Saves the color differences of each individual pixel. This 
	 * is useful for compensating for offset colors on a more local level.
	 */
	private void findIndividualRGBOffset () {
		long timeStart = System.currentTimeMillis();

		if (individualOffsetR == null || individualOffsetG == null || 
				individualOffsetB == null) {
			individualOffsetR = new int[width][height];
			individualOffsetG = new int[width][height];
			individualOffsetB = new int[width][height];
		}

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (x + offsetX >= width || x + offsetX < 0)
					continue;
				if (y + offsetY >= height || y + offsetY < 0)
					continue;

				int[] rgb0 = raster0.getPixel(x, y, new int[4]);
				int[] rgb1 = raster1.getPixel(x + offsetX, 
						y + offsetY, new int[4]);

				individualOffsetR[x][y] = rgb0[0] - rgb1[0];
				individualOffsetG[x][y] = rgb0[1] - rgb1[1];
				individualOffsetB[x][y] = rgb0[2] - rgb1[2];
			}
		}

		System.out.printf("Found individual RGB Offset ... %dms\n", 
				System.currentTimeMillis() - timeStart);
	}
	
	/**
	 * Uses local color offset to specifically adjust the 
	 * color on the given rgba value.
	 */
	private int[] adjustColorLocally (int x, int y, int[] rgba) {
		rgba[0] += individualOffsetR[x][y];
		rgba[1] += individualOffsetG[x][y];
		rgba[2] += individualOffsetB[x][y];
		return rgba;
	}
	
	/**
	 * Uses global color offset values to adjust the color on the
	 * given rgba value.
	 */
	private int[] adjustColorGlobally (int[] rgba) {
		rgba[0] += globalOffsetR;
		rgba[1] += globalOffsetB;
		rgba[2] += globalOffsetG;
		return rgba;
	}
}
