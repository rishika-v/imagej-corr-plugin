/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package com.mycompany.imagej;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.PlugIn;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.filter.RankFilters;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.image.RenderedImage;
import java.util.concurrent.TimeUnit;

import static javax.imageio.ImageIO.write;

/**
 * A template for processing each pixel of either
 * GRAY8, GRAY16, GRAY32 or COLOR_RGB images.
 *
 * @author Johannes Schindelin
 */
public class Process_Pixels implements PlugInFilter, PlugIn {
	protected ImagePlus image;

	// image property members
	private int width;
	private int height;

	// plugin parameters
	public double value;
	public boolean update;

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
	}

	@Override
	public void run(String arg) {
		//Main
		image = IJ.getImage();
		if (image == null) return;
		//ImagePlus image = IJ.openImage("/Users/rishika/Desktop/example-legacy-plugin-master/src/main/resources/2021-06-24-09hr-24min-53sec-844ms-BLFL-frac.tiff");
		//image.show();

		//average of ROI
		WaitForUserDialog wd = new WaitForUserDialog("Select Regression ROI");
		wd.show();
		Roi regression_roi = image.getRoi();
		getDetails();
		smoothStack(image, value);

		int width = image.getStack().getProcessor(1).getWidth();
		int height = image.getStack().getProcessor(1).getHeight();
		int imgStackSize = image.getStackSize();

		//Global time trace
		float[] globalTimeTrace = new float[imgStackSize]; //global
		for (int i = 0; i < imgStackSize; i++) {
			globalTimeTrace[i] = (float) averagePixelValue(image.getStack().getProcessor(i+1), width, height);
		}

		float[] currentTimeTrace = new float[imgStackSize];
		float[][][] errorStack = new float[width][height][imgStackSize]; //change dimensions, double/single
		float[] errorTimeTrace = new float[imgStackSize];
		for (int j = 0; j < height; j++) {
			for (int k = 0; k < width; k++) {
				for (int i = 0; i < imgStackSize; i++) {
					currentTimeTrace[i] = image.getStack().getProcessor(i+1).getPixelValue(j, k);
				}
				LinearRegressionModel l = new LinearRegressionModel(globalTimeTrace, currentTimeTrace);
				l.compute();
				for (int i = 0; i < imgStackSize; i++) {
					errorTimeTrace[i] = l.getError(globalTimeTrace[i], currentTimeTrace[i]);
				}
				if (imgStackSize >= 0) System.arraycopy(errorTimeTrace, 0, errorStack[k][j], 0, imgStackSize);
			}
		}

		ImageStack errorImageStack = new ImageStack(width, height);
		float[][] tempTimeTrace = new float[height][width];
		for (int i = 0; i < imgStackSize; i++) {
			for (int j = 0; j < height; j++) {
				for (int k = 0; k < width; k++) {
					tempTimeTrace[j][k] = errorStack[k][j][i];
				}
			}
			FloatProcessor fp = new FloatProcessor(tempTimeTrace);
			errorImageStack.addSlice(fp);
		}
		ImagePlus errorImg = new ImagePlus("error image", errorImageStack);
		errorImg.show();

		int seed_x, seed_y;
		ImagePlus corrImage;
		WaitForUserDialog wd_main = new WaitForUserDialog("Select ROI");
		wd_main.show();
		Roi roi = image.getRoi();
		while (roi != null && runProgram()){
			java.awt.Point[] points = roi.getContainedPoints();
			seed_x = arrayXAverage(points);
			seed_y = arrayYAverage(points);
			corrImage = correlationImage(errorStack, seed_x,seed_y, width, height, imgStackSize);
			corrImage.show();
			wd_main.show();
			roi = image.getRoi();
		}
		showMessage("End of program run");
	}


	@Override
	public void run(ImageProcessor ip) {
		// get width and height
		width = ip.getWidth();
		height = ip.getHeight();

		if (showDialog()) {
			process(ip);
			//AFTER BUILDING JAR: Call functions here- make them non-static
			//Image corrImage = correlationImage(image, value); //make correlationImage non static
			//ImageIO.write((RenderedImage) corrImage, "jpg", new java.io.File("/Users/rishika/Desktop/corrimage.jpg"));
			image.updateAndDraw();
		}
	}

	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("Process pixels");

		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("value", 0.00, 2);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		value = gd.getNextNumber();
		return true;
	}

	public void showMessage(String message) {
		IJ.showMessage("ProcessPixels", message);
	}

	private static boolean runProgram() {
		GenericDialog gd = new GenericDialog("Run Program");
		gd.showDialog();
		return !gd.wasCanceled();
	}

	private void getDetails() {
		GenericDialog gd = new GenericDialog("Smooth Filter Radius");
		gd.addNumericField("value", 20.00, 2);
		gd.showDialog();
		value = gd.getNextNumber();
	}


	/**
	 * Process an image.
	 * <p>
	 * Please provide this method even if {@link ij.plugin.filter.PlugInFilter} does require it;
	 * the method {@link ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)} can only
	 * handle 2-dimensional data.
	 * </p>
	 * <p>
	 * If your plugin does not change the pixels in-place, make this method return the results and
	 * change the {@link #setup(java.lang.String, ij.ImagePlus)} method to return also the
	 * <i>DOES_NOTHING</i> flag.
	 * </p>
	 *
	 * @param image the image (possible multi-dimensional)
	 */
	public void process(ImagePlus image) {
		// slice numbers start with 1 for historical reasons
		for (int i = 1; i <= image.getStackSize(); i++)
			process(image.getStack().getProcessor(i));
	}

	// Select processing method depending on image type
	public void process(ImageProcessor ip) {
		int type = image.getType();
		if (type == ImagePlus.GRAY8)
			process( (byte[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY16)
			process( (short[]) ip.getPixels() );
		else if (type == ImagePlus.GRAY32)
			process( (float[]) ip.getPixels() );
		else if (type == ImagePlus.COLOR_RGB)
			process( (int[]) ip.getPixels() );
		else {
			throw new RuntimeException("not supported");
		}
	}

	// processing of GRAY8 images
	public void process(byte[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (byte)value;
			}
		}
	}

	// processing of GRAY16 images
	public void process(short[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (short)value;
			}
		}
	}

	// processing of GRAY32 images
	public void process(float[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (float)value;
			}
		}
	}

	// processing of COLOR_RGB images
	public void process(int[] pixels) {
		for (int y=0; y < height; y++) {
			for (int x=0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y * width] += (int)value;
			}
		}
	}

	public void showAbout() {
		IJ.showMessage("ProcessPixels",
			"a template for processing each pixel of an image"
		);
	}

	public int arrayXAverage(java.awt.Point[] array){
		int sum = 0;
		for (java.awt.Point d : array) sum += d.getX();
		double X_average = 1.0d * sum / array.length;
		return (int) X_average;
	}

	public int arrayYAverage(java.awt.Point[] array){
		int sum = 0;
		for (java.awt.Point d : array) sum += d.getY();
		double Y_average = 1.0d * sum / array.length;
		return (int) Y_average;
	}

	public double averagePixelValue(ImageProcessor ip, int width, int height){
		double sum = 0;
		for (int i = 0; i < height; i ++){
			for (int j = 0; j < width; j++){
				sum += ip.getPixelValue(i, j);
			}
		}
		double average_val = 1.0d * sum / (height * width);
		return average_val;

	}

	public void smoothStack(ImagePlus img, double smoothRadius) {
		ImageStack stack= img.getStack();
		ImageProcessor ip;
		RankFilters RF= new RankFilters();

		for (int slice= 1; slice<= stack.getSize(); slice++) { // for all slices
			ip = stack.getProcessor(slice);
			RF.rank( ip, smoothRadius, 0); // perform filter with mean, radius
		}
		img.setStack(null, stack); // update img
	}



	public ImagePlus returnImage(ImagePlus image) {
		ImageProcessor ip = image.getStack().getProcessor(1);
		return new ImagePlus("firstimage", ip); //Now returns a 32 bit image
	}


	// function that returns correlation coefficient.
	private float correlationCoefficient(float[] X, float[] Y) {

		float sum_X = 0, sum_Y = 0, sum_XY = 0;
		float squareSum_X = 0, squareSum_Y = 0;

		int n = X.length;
		for (int i = 0; i < n; i++)
		{
			// sum of elements of array X.
			sum_X = sum_X + X[i];

			// sum of elements of array Y.
			sum_Y = sum_Y + Y[i];

			// sum of X[i] * Y[i].
			sum_XY = sum_XY + X[i] * Y[i];

			// sum of square of array elements.
			squareSum_X = squareSum_X + X[i] * X[i];
			squareSum_Y = squareSum_Y + Y[i] * Y[i];
		}

		// use formula for calculating correlation
		// coefficient.
		float num1 = n * sum_XY - sum_X * sum_Y;
		float num2 = (float) Math.sqrt((n * squareSum_X - sum_X * sum_X) * (n * squareSum_Y - sum_Y * sum_Y));
		float corr = num1 / num2;

		return corr;
	}

	public ImagePlus getImageFromArray(float[][] pixels) {
		FloatProcessor ip = new FloatProcessor(pixels);
		return new ImagePlus("corrimage", ip); //Now returns a 32 bit image
	}

	public ImagePlus correlationImage(float[][][] imageStack, int seedpix_x, int seedpix_y, int width, int height, int imgStackSize) { //time the function
		float[] seedTimeTrace = new float[imgStackSize];
		if (imgStackSize >= 0)
			System.arraycopy(imageStack[seedpix_y][seedpix_x], 0, seedTimeTrace, 0, imgStackSize);
		float[][] correlationTimeTrace = new float[height][width];
		float[] currentTimeTrace = new float[imgStackSize];
		for (int j = 0; j < height; j++) {
			for (int k = 0; k < width; k++) {
				if (imgStackSize >= 0) System.arraycopy(imageStack[k][j], 0, currentTimeTrace, 0, imgStackSize);
				correlationTimeTrace[j][k] = correlationCoefficient(currentTimeTrace, seedTimeTrace);
			}
		}
		return getImageFromArray(correlationTimeTrace);
	}

	/**
	 * Main method for debugging.
	 *
	 * For debugging, it is convenient to have a method that starts ImageJ, loads
	 * an image and calls the plugin, e.g. after setting breakpoints.
	 *
	 * @param args unused
	 */

	/*
	public static void main(String[] args) throws Exception {
		// set the plugins.dir property to make the plugin appear in the Plugins menu
		// see: https://stackoverflow.com/a/7060464/1207769
		Class<?> clazz = Process_Pixels.class;
		java.net.URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
		java.io.File file = new java.io.File(url.toURI());
		System.setProperty("plugins.dir", file.getAbsolutePath());

		// start ImageJ
		new ImageJ();
		ImagePlus image = IJ.openImage("/Users/rishika/Desktop/2021-06-24-09hr-24min-53sec-844ms-BLFL-frac.tiff");
		image.show();

		smoothStack(image, 20); //manual enter radius of 20

		int width = image.getStack().getProcessor(1).getWidth();
		int height = image.getStack().getProcessor(1).getHeight();
		int imgStackSize = image.getStackSize();

		//Global time trace
		float[] globalTimeTrace = new float[imgStackSize]; //global
		for (int i = 0; i < imgStackSize; i++) {
			globalTimeTrace[i] = (float) averagePixelValue(image.getStack().getProcessor(i+1), width, height);
		}

		float[] currentTimeTrace = new float[imgStackSize];
		float[][][] errorStack = new float[width][height][imgStackSize]; //change dimensions, double/single
		float[] errorTimeTrace = new float[imgStackSize];
		for (int j = 0; j < height; j++) {
			for (int k = 0; k < width; k++) {
				for (int i = 0; i < imgStackSize; i++) {
					currentTimeTrace[i] = image.getStack().getProcessor(i+1).getPixelValue(j, k);
				}
				LinearRegressionModel l = new LinearRegressionModel(globalTimeTrace, currentTimeTrace);
				l.compute();
				for (int i = 0; i < imgStackSize; i++) {
					errorTimeTrace[i] = l.getError(globalTimeTrace[i], currentTimeTrace[i]);
				}
				if (imgStackSize - 1 >= 0) System.arraycopy(errorTimeTrace, 1, errorStack[k][j], 1, imgStackSize - 1);
			}
		}

		ImageStack errorImageStack = new ImageStack(width, height);
		float[][] tempTimeTrace = new float[height][width];
		for (int i = 0; i < imgStackSize; i++) {
			for (int j = 0; j < height; j++) {
				for (int k = 0; k < width; k++) {
					tempTimeTrace[j][k] = errorStack[k][j][i];
				}
			}
			FloatProcessor fp = new FloatProcessor(tempTimeTrace);
			errorImageStack.addSlice(fp);
		}
		ImagePlus errorImg = new ImagePlus("error image", errorImageStack);
		errorImg.show();

		//correlation input- errorstack
		int seed_x, seed_y;
		ImagePlus corrImage = null;
		Roi roi = image.getRoi();
		while (roi != null && runProgram()){
			java.awt.Point[] points = roi.getContainedPoints();
			//choose ROI from errorstack- corresponding values, change corr function too
			seed_x = arrayXAverage(points);
			seed_y = arrayYAverage(points);
			corrImage = correlationImage(errorStack, seed_x,seed_y, width, height, imgStackSize);
			corrImage.show();
			roi = image.getRoi();
		}

		write((RenderedImage) corrImage, "jpg", new java.io.File("/Users/rishika/Desktop/corrimage.jpg"));
		ImagePlus image2 = IJ.openImage("/Users/rishika/Desktop/corrimage.jpg");
		image2.show();
		IJ.runPlugIn(clazz.getName(), "");
	}
	 */


}
