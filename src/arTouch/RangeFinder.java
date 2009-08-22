package arTouch;

import java.awt.image.Raster;

/**
 * An image matcher is a class able to overlap images or portions
 * of images looking for the offset. This offset gives us a notion
 * of how far the matched object is from the camera.
 */
public interface RangeFinder {
	public void findRange (Raster raster0, Raster raster1);
}
