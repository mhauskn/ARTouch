package arTouch;

import java.awt.Image;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;

import arTouch.Clusterer.Cluster;
import arTouch.rangeFinders.ClusterMatcher;

public class StereoVisionProcessor {
	BufferedImage image0, image1;
	Raster raster0, raster1;
	
	CameraCalibrator cameraCalibrator = new CameraCalibrator();
	RangeFinder rangeFinder = new ClusterMatcher(cameraCalibrator);
	BGSubtractor bgSubtractor = new BGSubtractor();
	
	public void processImagePair (Image img0, Image img1) {
		image0 = getBufferedImage(img0);
		image1 = getBufferedImage(img1);

		raster0 = image0.getData();
		raster1 = image1.getData();

		cameraCalibrator.checkCameraCalibration(raster0, raster1);
		ArrayList<Cluster> bg0Clusters = bgSubtractor.getForeground0(raster0);
		ArrayList<Cluster> bg1Clusters = bgSubtractor.getForeground1(raster1);
		
		int width = raster0.getWidth(), height = raster0.getHeight();
		Clusterer.displayClusters(bg0Clusters, width, height, raster0, true);
		Clusterer.displayClusters(bg1Clusters, width, height, raster1, false);
		
		ClusterMatcher.matchClusters(bg0Clusters, bg1Clusters);
		//rangeFinder.findRange(raster0, raster1);
	}

	/**
	 * Converts an image into a buffered image
	 */
	public static BufferedImage getBufferedImage (Image image) {
		int w = image.getWidth(null);
		int h = image.getHeight(null);

		int[] pixels = new int[w * h];
		PixelGrabber pg = new PixelGrabber(image, 0, 0, w, h, pixels, 0, w);
		try {
			pg.grabPixels();
		} catch (InterruptedException e) {
			System.err.println("interrupted waiting for pixels!");
			System.exit(1);
		}
		if ((pg.getStatus() & ImageObserver.ABORT) != 0) {
			System.err.println("image fetch aborted or errored");
			System.exit(1);
		}

		DataBufferInt DB=new DataBufferInt(pixels,(w*h),0);

		int[] BM=new int[]{0xff0000,0xff00,0xff,0xff000000};
		SinglePixelPackedSampleModel SM=new    
		SinglePixelPackedSampleModel(DataBuffer.TYPE_INT,w,h,BM);                      

		Point P = new Point(0,0);      
		WritableRaster R = Raster.createWritableRaster(SM,DB,P); 

		return new BufferedImage(pg.getColorModel(),R,false,null);
	}
}
