import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import au.edu.jcu.v4l4j.FrameGrabber;
import au.edu.jcu.v4l4j.VideoDevice;
import au.edu.jcu.v4l4j.exceptions.V4L4JException;

public class DualViewer extends WindowAdapter implements Runnable {
	private VideoDevice vd0, vd1;
	private static JLabel l0, l1, l2;
	private JFrame f;
	private FrameGrabber fg0, fg1;
	private Thread captureThread;
	private boolean stop = false;
	private boolean grabPrimary = true;
	private Image img0, img1;
	private StereoVisionProcessor stereoProc = new StereoVisionProcessor();

	public static final int w=640, h=480, std=0, channel = 0, qty = 60;

	/**
	 * Creates the Dual Viewer with using the identifier for each device.
	 */
	public DualViewer (String device0Identifier, String device1Identifier) {
		try {
			vd0 = new VideoDevice(device0Identifier);
			fg0 = initFrameGrabber(vd0);
			vd1 = new VideoDevice(device1Identifier);
			fg1 = initFrameGrabber(vd1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		initGUI();
		captureThread = new Thread(this, "Capture Thread");
		captureThread.start();
	}

	/** 
	 * Creates the graphical interface components and initialises them
	 */
	private void initGUI(){
		f = new JFrame("Dual Viewer");
		JPanel panel = new JPanel(new BorderLayout());
		l0 = new JLabel();
		l0.setPreferredSize(new Dimension(fg0.getWidth(), fg0.getHeight()));
		l1 = new JLabel();
		l1.setPreferredSize(new Dimension(fg1.getWidth(), fg1.getHeight()));
		
		l2 = new JLabel();
		l2.setPreferredSize(new Dimension(fg1.getWidth(), fg1.getHeight()));

		panel.add(l0, BorderLayout.EAST);
		panel.add(l1, BorderLayout.WEST);
		panel.add(l2, BorderLayout.SOUTH);

		f.add(panel);
		f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		f.addWindowListener(this);
		f.pack();
		f.setVisible(true);
	}

	/**
	 * Initializes the FrameGrabber object
	 */
	private FrameGrabber initFrameGrabber(VideoDevice vd) throws V4L4JException{
		FrameGrabber fg = vd.getJPEGFrameGrabber(w, h, channel, std, qty);
		fg.startCapture();
		System.out.println("Starting capture at "+fg.getWidth()+"x"+fg.getHeight());    
		return fg;
	}

	/**
	 * Implements the capture thread: get a frame from the FrameGrabber, and display it
	 */
	public void run(){
		ByteBuffer bb;
		byte[] b;
		try {                   
			while(!stop){
				grabPrimary = !grabPrimary;
				if (grabPrimary) {
					bb = fg0.getFrame();
					b = new byte[bb.limit()];
					bb.get(b);
					l0.setIcon(new ImageIcon(b));
					img0 = convertToImage(b);
				} else {
					bb = fg1.getFrame();
					b = new byte[bb.limit()];
					bb.get(b);
					l1.setIcon(new ImageIcon(b));
					img1 = convertToImage(b);
					if (img0 != null && img1 != null)
						stereoProc.processImagePair(img0, img1);
				}
			}
		} catch (V4L4JException e) {
			e.printStackTrace();
			System.out.println("Failed to capture image");
		}
	}

	private Image convertToImage (byte[] b) {
		ImageIcon testicon = new ImageIcon(b);
		return testicon.getImage();
	}

	/**
	 * Catch window closing event so we can free up resources before exiting
	 */
	public void windowClosing(WindowEvent e) {
		if(captureThread.isAlive()){
			stop = true;
			try {
				captureThread.join();
			} catch (InterruptedException e1) {}
		}

		fg0.stopCapture();
		vd0.releaseFrameGrabber();
		fg1.stopCapture();
		vd1.releaseFrameGrabber();

		f.dispose();            
	}
	
	public static void displayImage (Image img) {
		l2.setIcon(new ImageIcon(img));
		l2.repaint();
	}

	public static void main(String[] args) throws V4L4JException, IOException {
		String dev0 = "/dev/video0";
		String dev1 = "/dev/video1";
		new DualViewer(dev0, dev1);
	}
}