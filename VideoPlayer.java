import java.awt.*;
import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.lang.Math;

public class VideoPlayer {

	class HSV {
		double h, s, v;
		public HSV(double h, double s, double v){
			this.h =  h;
			this.s =  s;
			this.v =  v;
		}
	}

	class RGB {
		double r, g, b;
		public RGB(double r, double g, double b){
			this.r = r;
			this.g= g;
			this.b = b;
		}
	}

	// Standard video dimensions
	JFrame frame;
	JLabel lbIm1;
	JLabel lbIm2;
	BufferedImage imgOne;
	BufferedImage imgTwo;
	BufferedImage prevImg;
	int WIDTH = 640;
	int HEIGHT = 480;
	double MIN_HUE;
	double MAX_HUE;
	double VAL;
	double SAT;

	public ArrayList<BufferedImage> ov;	// Stores original video
	public ArrayList<BufferedImage> processedfgVideo;
	public ArrayList<BufferedImage> nv;	// Stores output video
	public HSV[][] originalHSV = new HSV[HEIGHT][WIDTH];
	public RGB[][] originalRGB = new RGB[HEIGHT][WIDTH];

	private String fileName;				// Name of the input video file
	private int option;						// 0 = default, 1 = analysis of aspect ratio

	public static void main(String[] args) {
		// Initializing our class
		VideoPlayer vp = new VideoPlayer();

		if(args[2].equals("1")) {
			vp.load(args,1); // Reading the background video
			vp.reference(args,0); // Reading the reference frame.
			vp.load(args,0); // Reading the foreground video
			vp.removeGreenBoundaries();

			JFrame frame = new JFrame();
			JLabel label = new JLabel(new ImageIcon(vp.ov.get(0)));
			frame.getContentPane().add(label, BorderLayout.CENTER);
			frame.pack();
			frame.setVisible(true);

			//Set frame rate
			for(int i=1; i < vp.ov.size(); i++) {
				label.setIcon(new ImageIcon(vp.ov.get(i)));
				try {
					Thread.sleep(1000/24); //
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		}
		else if(args[2].equals("0")) {
			vp.load(args,1);
			vp.load(args,0);
			//System.out.println(vp.ov.size());
			//System.out.println(vp.nv.size());
			//vp.subtractVideo();

			JFrame frame = new JFrame();
			JLabel label = new JLabel(new ImageIcon(vp.ov.get(0)));
			frame.getContentPane().add(label, BorderLayout.CENTER);
			frame.pack();
			frame.setVisible(true);

			//Set frame rate
			for(int i=1; i < vp.ov.size(); i++) {
				label.setIcon(new ImageIcon(vp.ov.get(i)));
				try {
					Thread.sleep(1000/24); //
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		}
	} // End of main
	/**
	 *	Constructor
	 */
	public VideoPlayer() {
		// Initialize variables
		ov = new ArrayList<BufferedImage>();
		nv = new ArrayList<BufferedImage>();
		processedfgVideo = new ArrayList<BufferedImage>();
	}

	public void removeGreenBoundaries() {
		int count = 0;
		BufferedImage imgThree = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		while (count < 480) {
			try {
				imgOne = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
				imgThree = ov.get(count);
				imgOne = nv.get(count);
				for (int y = 0; y < HEIGHT; y++) {
					for (int x = 0; x < WIDTH; x++) {
							Color mycolor = new Color(imgThree.getRGB(x, y));
							int red = mycolor.getRed();
							int green = mycolor.getGreen();
							int blue = mycolor.getBlue();
							int pix = 0xff000000 | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
							if(red == 255 && green == 255 && blue == 255) {
								Color bgcolor = new Color(imgOne.getRGB(x, y));
								int bgred = bgcolor.getRed();
								int bggreen = bgcolor.getGreen();
								int bgblue = bgcolor.getBlue();
								red = (byte) bgred;
								green = (byte) bggreen;
								blue = (byte) bgblue;
								pix = 0xff000000 | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
							}
							else {
								double[] hsv = new double[3];
								hsv = rgb_to_hsv(red, green, blue);
								if(hsv[0] >= 60 && hsv[0] <= (MAX_HUE+15) && hsv[1] >= SAT && hsv[2] >= VAL) {
									//RGB avg_rgb;
									//pix = avgRGB(x,y,imgOne);
									Color bgcolor = new Color(imgOne.getRGB(x, y));
									int bgred = bgcolor.getRed();
									int bggreen = bgcolor.getGreen();
									int bgblue = bgcolor.getBlue();
									pix = 0xff000000 | ((bgred & 0xff) << 16) | ((bggreen & 0xff) << 8) | (bgblue & 0xff);
									pix = changeAlpha(pix, 1);
								}
							}
						imgThree.setRGB(x, y, pix);
					}
				}
				ov.set(count,imgThree);
			}
					catch(Exception e)
			{
				e.printStackTrace();
			}
			count++;
		}
	}

	int changeAlpha(int origColor, int userInputedAlpha) {
		origColor = origColor & 0x00ffffff; //drop the previous alpha value
		return (userInputedAlpha << 24) | origColor; //add the one the user inputted
	}

	public void reference(String[] args, int flag) {
		System.out.println("1st frame for reference");
		int width = WIDTH;
		int height = HEIGHT;
		double maxSat = 0.0;
		double maxVal = 0.0;
		int count = 0;
		while (count < 480) {
			try {
				int frameLength = width * height * 3;
				String filePath = args[flag];
				String fileName = filePath.split("/")[1];
				String str = String.format("%04d", count);
				String newFileName = args[flag] + "/" + fileName + "." + str + ".rgb";

				File file = new File(newFileName);
				RandomAccessFile raf = new RandomAccessFile(file, "r");
				raf.seek(0);

				long len = file.length();
				byte[] bytes = new byte[(int) len];

				int offset = 0;
				int numRead = 0;

				raf.read(bytes);
				int ind = 0;
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						if ((x == 0 || x == 639) || (y == 0 || y == 479)) {
							int R = bytes[ind];
							int G = bytes[ind + height * width];
							int B = bytes[ind + height * width * 2];
							R = R & 0xFF;
							G = G & 0xFF;
							B = B & 0xFF;
							double[] hsv = new double[3];
							hsv = rgb_to_hsv(R, G, B);
							if (x == 0 && y == 0) {
								MIN_HUE = hsv[0];
								MAX_HUE = hsv[0];
								SAT = hsv[1];
								VAL = hsv[2];
							} else {
								MIN_HUE = Math.min(MIN_HUE, hsv[0]);
								MAX_HUE = Math.max(MAX_HUE, hsv[0]);
								SAT = Math.min(SAT, hsv[1]);
								VAL = Math.min(VAL, hsv[2]);
								maxSat = Math.max(maxSat, hsv[1]);
								maxVal = Math.max(maxVal, hsv[2]);
							}
							ind++;
						}
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			count++;
		}
		System.out.println("Min Hue :" + MIN_HUE);
		System.out.println("Max Hue :" + MAX_HUE);
		System.out.println("Saturation :" + SAT);
		System.out.println("max Saturation :" + maxSat);
		System.out.println("Value :" + VAL);
		System.out.println("max Value :" + maxVal);
		if(VAL >= 0.55) {
			VAL = VAL - 0.35;
		}
		else if(VAL >= 0.45) {
			VAL = VAL - 0.25;
		}
		else if(VAL >= 0.35) {
			VAL = VAL - 0.15;
		}
		else {
			VAL = VAL - 0.1;
		}
		if(SAT >= 0.45) {
			SAT = SAT - 0.225;
		}
		else {
			SAT = SAT - 0.125;
		}
	}

	/**
	 *	Loads the video file into an ArrayList<BufferedImage>
	 */
	public void load(String[] args,int flag) {
		System.out.println("Loading input video for flag :" + flag);
		int width = WIDTH;
		int height = HEIGHT;
		int count = 0;
		prevImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		// Read the input video into the ov and the nv ArrayList
		while (count < 480) {
			try {
				int frameLength = width * height * 3;
				String filePath = args[flag];
				String fileName = filePath.split("/")[1];
				String str = String.format("%04d", count);
				String newFileName = args[flag] + "/" + fileName + "." + str + ".rgb";

				File file = new File(newFileName);
				RandomAccessFile raf = new RandomAccessFile(file, "r");
				raf.seek(0);

				long len = file.length();
				byte[] bytes = new byte[(int) len];

				int offset = 0;
				int numRead = 0;

				raf.read(bytes);

				imgTwo = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				if(flag == 0) {
					imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
					imgOne = nv.get(count);
				}
				int ind = 0;
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						byte a = 0;
						byte r = bytes[ind];
						byte g = bytes[ind + height * width];
						byte b = bytes[ind + height * width * 2];
						int R = bytes[ind];
						int G = bytes[ind + height * width];
						int B = bytes[ind + height * width * 2];
						R = R & 0xFF;
						G = G & 0xFF;
						B = B & 0xFF;
						double newRed = (double) R;
						double newGreen = (double) G;
						double newBlue = (double) B;
						if (flag == 0 && args[2].equalsIgnoreCase("1")) {
							double[] hsv = new double[3];
							hsv = rgb_to_hsv(R, G, B);
							if (hsv[0] >= 60.0 && hsv[0] <= 175.0 && hsv[1] >= 0.35 && hsv[2] >= 0.2) {
								/*Color mycolor = new Color(imgOne.getRGB(x, y));
								int red = mycolor.getRed();
								int green = mycolor.getGreen();
								int blue = mycolor.getBlue();*/
								r = (byte) 255;
								g = (byte) 255;
								b = (byte) 255;
							}
						}
						else if(flag == 0 && args[2].equalsIgnoreCase("0") && prevImg != null) {
							if(count == 0) {
								RGB sum = new RGB(newRed,newGreen,newBlue);
								originalRGB[y][x] = sum;
							}
							if(count > 0) {
								RGB prev = originalRGB[y][x];
								double red = prev.r;
								double green = prev.g;
								double blue = prev.b;
								newRed = red + R;
								newGreen = green + G;
								newBlue = blue + B;
								RGB sum = new RGB(newRed,newGreen,newBlue);
								originalRGB[y][x] = sum;
								red = red/count;
								green = green/count;
								blue = blue/count;
								Color mycolor = new Color(prevImg.getRGB(x, y));
								int prevRed = mycolor.getRed();
								int prevGreen = mycolor.getGreen();
								int prevBlue = mycolor.getBlue();
								if(Math.abs(red-R) <= 10  && Math.abs(blue-B) <= 10  && Math.abs(green-G) <= 10) {
									Color bgcolor = new Color(imgOne.getRGB(x, y));
									int bgred = bgcolor.getRed();
									int bggreen = bgcolor.getGreen();
									int bgblue = bgcolor.getBlue();
									r = (byte)bgred;
									g = (byte)bggreen;
									b = (byte)bgblue;
								}
							}
						}
						ind++;            // Skip to the next R
						int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
						imgTwo.setRGB(x, y, pix);
						if(flag == 0 && args[2].equalsIgnoreCase("0")) prevImg.setRGB(x, y, pix);
					}
				}
				if (flag == 0) {
					ov.add(imgTwo);
				} else if (flag == 1) {
					nv.add(imgTwo);
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			count++;
		}
	}

	public void subtractVideo() {
		System.out.println("Subtraction video mode");
		int width = WIDTH;
		int height = HEIGHT;
		calculateMean();
		for (int i = 0; i < ov.size(); i++) {
			BufferedImage fgimg = ov.get(i);
			BufferedImage bgimg = nv.get(i);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					Color fgcolor = new Color(fgimg.getRGB(x, y));
					int red = fgcolor.getRed();
					int green = fgcolor.getGreen();
					int blue = fgcolor.getBlue();
					double[] hsv = new double[3];
					hsv = rgb_to_hsv(red, green, blue);
					HSV mean = originalHSV[y][x];
					if(Math.abs(hsv[0]-mean.h) <= 30  && Math.abs(hsv[1]-mean.s) <= 0.05 && Math.abs(hsv[2]-mean.v) <= 0.2) {
						Color bgcolor = new Color(bgimg.getRGB(x, y));
						int bgred = bgcolor.getRed();
						int bggreen = bgcolor.getGreen();
						int bgblue = bgcolor.getBlue();
						int r = (byte)bgred;
						int g = (byte)bggreen;
						int b = (byte)bgblue;
						int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
						fgimg.setRGB(x, y, pix);
					}
				}
			}
			processedfgVideo.add(fgimg);
		}
	}

	public void calculateMean() {
		System.out.println("Calculating the mean hsv values");
		for(int i=0;i<480;i++) {
			BufferedImage fgimg = ov.get(i);
			for (int y = 0; y < HEIGHT; y++) {
				for (int x = 0; x < WIDTH; x++) {
					Color fgcolor = new Color(fgimg.getRGB(x, y));
					double[] hsv = new double[3];
					hsv = rgb_to_hsv(fgcolor.getRed(), fgcolor.getGreen(), fgcolor.getBlue());
					if(i == 0) {
						originalHSV[y][x] = new HSV(hsv[0],hsv[1],hsv[2]);
					}
					else if(i > 0 && i < 479)  {
						HSV prev = originalHSV[y][x];
						prev.h = prev.h + hsv[0];
						prev.s = prev.s + hsv[1];
						prev.v = prev.v + hsv[2];
						originalHSV[y][x] = prev;
					}
					else if(i == 479) {
						HSV prev = originalHSV[y][x];
						prev.h = (prev.h + hsv[0])/480.0;
						prev.s = (prev.s + hsv[1])/480.0;
						prev.v = (prev.v + hsv[2])/480.0;
						originalHSV[y][x] = prev;
					}
				}
			}
		}
	}

	public double[] rgb_to_hsv(double r, double g, double b)
	{

		// R, G, B values are divided by 255
		// to change the range from 0..255 to 0..1
		r = r / 255.0;
		g = g / 255.0;
		b = b / 255.0;

		// h, s, v = hue, saturation, value
		double cmax = Math.max(r, Math.max(g, b)); // maximum of r, g, b
		double cmin = Math.min(r, Math.min(g, b)); // minimum of r, g, b
		double diff = cmax - cmin; // diff of cmax and cmin.
		double h = -1, s = -1;

		// if cmax and cmax are equal then h = 0
		if (cmax == cmin)
			h = 0;

			// if cmax equal r then compute h
		else if (cmax == r)
			h = (60 * ((g - b) / diff) + 360) % 360;

			// if cmax equal g then compute h
		else if (cmax == g)
			h = (60 * ((b - r) / diff) + 120) % 360;

			// if cmax equal b then compute h
		else if (cmax == b)
			h = (60 * ((r - g) / diff) + 240) % 360;

		// if cmax equal zero
		if (cmax == 0)
			s = 0;
		else
			s = (diff / cmax);

		// compute v
		double v = cmax;
		return new double[] {h,s,v};

	}
}

	/**
	 * Default implementation
	 * Resize the video and anti-alias if ON 
	 */
	/*public void aliasing() {
		System.out.println("Aliasing");
		for(int i=0; i < ov.size(); i++) {
			BufferedImage img = ov.get(i);	// Original frame
			BufferedImage new_img = new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB); // New frame
			for(int y=0; y < new_img.getHeight(); y++) {
				for(int x=0; x < new_img.getWidth(); x++) {
					int rgb = img.getRGB(x,y);
					rgb = avgRGB(x,y,img);
					new_img.setRGB(x,y,rgb);

				}
			}
			ov.set(i,new_img);	// Add to output
		}
	} */

	/**
	 * Returns int RGB average of 3x3 where pixel (x,y) is in the center for a given BufferedImage img
	 * Check image boundaries
	 */
/*	public int avgRGB(int x, int y, BufferedImage img) {
		Color c = new Color(img.getRGB(x,y));
		int rsum = 0;
		int	gsum = 0;
		int	bsum = 0;
		int count = 0;

		int w = img.getWidth();
		int h = img.getHeight();

		for(int j=-1; j < 2; j++) {
			for(int i=-1; i < 2; i++) {
				if(x+i > -1 && x+i < w && y+j > -1 && y+j < h) {
					c = new Color(img.getRGB(x+i,y+j));
					rsum += c.getRed();
					gsum += c.getGreen();
					bsum += c.getBlue();
					count++;
				}
			}
		}

		int a = 0;
		int r = rsum/count;
		int g = gsum/count;
		int b = bsum/count;

		return ((a << 24) + (r << 16) + (g << 8) + b);
	}*/

	/**
	 * Nonlinear mapping implementation
	 */
/*	public void nonlinear() {
		System.out.println("Nonlinear mapping");

		int frame_width = (int)(WIDTH*width_scaling_factor);
		int frame_height = (int)(HEIGHT*height_scaling_factor);
		//System.out.println("Frame dimensions = " + frame_width + "x" + frame_height);
		//System.out.println("Scaling factors, wsf = " + width_scaling_factor + ", hsf = " + height_scaling_factor);

		// Dimensions for same aspect ratio in frame
		int new_width = WIDTH;
		int new_height = HEIGHT;
/*
   		// Scaling factors for same aspect ratio in frame	
   		double new_wsf = 1.0;
   		double new_hsf = 1.0;
   		// Scale width if necessary
   		if(frame_width != WIDTH) {
   			new_width = frame_width;
   			new_height = (new_width*HEIGHT) / WIDTH; 	// Get proportional height with ratio
   			new_wsf = (double)new_width / (double)WIDTH;
   			new_hsf = (double)new_height / (double)HEIGHT;
   		}
   		// Scale height if necessary
   		if(new_height > frame_height) {
   			new_height = frame_height;
   			new_width = (new_height*WIDTH) / HEIGHT;	// Get proportional width with ratio
   			new_wsf = (double)new_width / (double)WIDTH;
   			new_hsf = (double)new_height / (double)HEIGHT;
   		}
*/
		//System.out.println("Video dimensions = " + new_width + "x" + new_height);
		//System.out.println("New wsf = " + new_wsf + ", new hsf = " + new_hsf);

		/*double ratio = ((double)WIDTH/(double)HEIGHT)/((double)frame_width/(double)frame_height);
		// Same aspect ratio, just resize to frame
		if(ratio == 1) {
			resize();
			return;
		}

		if(isAntialiased == 1)
			System.out.println("Anti-aliasing");

		for(int i=0; i < ov.size(); i++) {
			BufferedImage img = ov.get(i);	// Original frame
			BufferedImage new_img = new BufferedImage(frame_width,frame_height,BufferedImage.TYPE_INT_RGB); // New frame

			// Scale to % width
			if(ratio < 1) {
				for(int y=0; y < new_img.getHeight(); y++) {
					int w = (int)(0.6*(double)WIDTH);
					double ar = w/HEIGHT;
					int center_width = new_height*w/HEIGHT;
					int side_width = (frame_width - center_width)/2;

					int lowerbound = side_width;
					int upperbound = center_width+side_width;

					// Render left side with different aspect ratio
					for(int x=0; x < lowerbound; x++) {
						double wsf = (double)side_width/(0.2*WIDTH);
						int x_orig = (int)((double)x/wsf);
						int y_orig = (int)((double)y/height_scaling_factor);

						int rgb = img.getRGB(x_orig,y_orig);

						// Check if anti-aliasing is ON
						if(isAntialiased == 1) {
							rgb = avgRGB(x_orig,y_orig,img);
						}

						new_img.setRGB(x,y,rgb);
					}

					// Keep 60% of original width in focus/same aspect ratio
					BufferedImage center = img.getSubimage((int)(0.2*WIDTH), 0, (int)(0.6*(double)WIDTH),HEIGHT);
					for(int x=0; x < center_width; x++) {

						double wsf = (double)center_width/(0.6*WIDTH);
						int x_orig = (int)((double)x/wsf);
						int y_orig = (int)((double)y/height_scaling_factor);

						int rgb = center.getRGB(x_orig,y_orig);

						// Check if anti-aliasing is ON
						if(isAntialiased == 1) {
							rgb = avgRGB(x_orig,y_orig,center);
						}

						new_img.setRGB(x+lowerbound,y,rgb);
					}

					// Render right side with different aspect ratio
					BufferedImage right = img.getSubimage((int)(0.8*WIDTH), 0, (int)(0.2*WIDTH),HEIGHT);
					for(int x=0; x < lowerbound; x++) {
						double wsf = (double)side_width/(0.2*WIDTH);
						int x_orig = (int)((double)x/wsf);
						int y_orig = (int)((double)y/height_scaling_factor);

						if(x_orig >= right.getWidth())
							continue;

						int rgb = right.getRGB(x_orig,y_orig);

						// Check if anti-aliasing is ON
						if(isAntialiased == 1) {
							rgb = avgRGB(x_orig,y_orig,right);
						}
						if(x+upperbound >= new_img.getWidth() || y >= new_img.getHeight())
							continue;

						new_img.setRGB(x+upperbound,y,rgb);
					}
				} // end of ratio < 1
			}

			// Scale to % height
			else if(ratio > 1) {
				for(int x=0; x < new_img.getWidth(); x++) {
					int h = (int)(0.6*(double)HEIGHT);
					double ar = WIDTH/h;
					int center_height = new_width*h/WIDTH;
					int side_height = (frame_height - center_height)/2;

					int lowerbound = side_height;
					int upperbound = center_height+side_height;

					// Render top with different aspect ratio
					for(int y=0; y < lowerbound; y++) {
						double hsf = (double)side_height/(0.2*HEIGHT);
						int x_orig = (int)((double)x/width_scaling_factor);
						int y_orig = (int)((double)y/hsf);

						int rgb = img.getRGB(x_orig,y_orig);

						// Check if anti-aliasing is ON
						if(isAntialiased == 1) {
							rgb = avgRGB(x_orig,y_orig,img);
						}

						new_img.setRGB(x,y,rgb);
					}

					// Keep 60% of original height in focus/same aspect ratio
					BufferedImage center = img.getSubimage(0,(int)(0.2*HEIGHT),WIDTH,(int)(0.6*HEIGHT));
					for(int y=0; y < center_height; y++) {
						double hsf = (double)center_height/(0.6*HEIGHT);
						int x_orig = (int)((double)x/width_scaling_factor);
						int y_orig = (int)((double)y/hsf);

						int rgb = center.getRGB(x_orig,y_orig);

						// Check if anti-aliasing is ON
						if(isAntialiased == 1) {
							rgb = avgRGB(x_orig,y_orig,center);
						}

						new_img.setRGB(x,y+lowerbound,rgb);
					}

					// Render bottom with different aspect ratio
					BufferedImage bottom = img.getSubimage(0,(int)(0.8*HEIGHT),WIDTH,(int)(0.2*HEIGHT));
					for(int y=0; y < lowerbound; y++) {
						double hsf = (double)side_height/(0.2*HEIGHT);
						int x_orig = (int)((double)x/width_scaling_factor);
						int y_orig = (int)((double)y/hsf);

						if(y_orig >= bottom.getHeight())
							continue;

						int rgb = bottom.getRGB(x_orig,y_orig);

						// Check if anti-aliasing is ON
						if(isAntialiased == 1) {
							rgb = avgRGB(x_orig,y_orig,bottom);
						}
						if(x >= new_img.getWidth() || y+upperbound >= new_img.getHeight())
							continue;

						new_img.setRGB(x,y+upperbound,rgb);
					}
				} // end of for loop

			} // end of ratio > 1

			nv.add(new_img);
		}

	}*/
