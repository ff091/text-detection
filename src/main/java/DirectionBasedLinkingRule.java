import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_highgui.cvSaveImage;
import static com.googlecode.javacv.cpp.opencv_highgui.cvShowImage;
import static com.googlecode.javacv.cpp.opencv_highgui.cvWaitKey;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import java.util.*;

import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class DirectionBasedLinkingRule implements LinkingRule {
	private HashMap<Feature, Histogram> hists;
	private double minLinkRating;
	private double minFeatureRating;
	private IplImage img;
	private IplImage direction;
	private IplImage mask;
	private IplImage temp8u;
	private IplImage temp32f;

	IplConvKernel[] strels;

	public DirectionBasedLinkingRule(List<Feature> features, IplImage img,
			int filterSize, int dilateSize, int numAngles, int lineWidth,
			double minLinkRating, double minFeatureRating) {
		this.minLinkRating = minLinkRating;
		this.minFeatureRating = minFeatureRating;

		IplImage input;
		IplImage max;

		// initialize images
		{
			int width = img.width();
			int height = img.height();

			this.img = img;
			
			input = IplImage.create(width, height, IPL_DEPTH_32F, 1);
			cvSet(input, CvScalar.ONE);
			cvMul(input, img, input, 1 / 255.0);

			direction = IplImage.create(width, height, IPL_DEPTH_8U, 1);
			cvSetZero(direction);

			max = IplImage.create(width, height, IPL_DEPTH_32F, 1);
			cvSetZero(max);

			temp8u = IplImage.create(width, height, IPL_DEPTH_8U, 1);
			temp32f = IplImage.create(width, height, IPL_DEPTH_32F, 1);
			mask = IplImage.create(width, height, IPL_DEPTH_8U, 1);
		}

		// create filter kernels
		CvMat[] filters;
		{
			filters = new CvMat[numAngles];
			strels = new IplConvKernel[numAngles];

			for (int i = 0; i < numAngles; i++) {
				filters[i] = CvMat.create(filterSize, filterSize, CV_32FC1);
				cvSetZero(filters[i]);
			}

			double r = filterSize / 2;
			int c = (int) Math.round(r);
			for (int i = 0; i < numAngles; i++) {
				double angle = i / (double) numAngles * Math.PI;
				double sin = Math.sin(angle);
				double cos = Math.cos(angle);

				int x = (int) Math.round(cos * r);
				int y = (int) Math.round(sin * r);

				filters[i] = CvMat.create(filterSize, filterSize, CV_32FC1);
				cvSetZero(filters[i]);

				cvDrawLine(filters[i], cvPoint(c + x, c + y),
						cvPoint(c - x, c - y), CvScalar.ONE, lineWidth, 8, 0);

				double sum = cvSum(filters[i]).blue();
				cvScale(filters[i], filters[i], 1 / sum, 0);

				// create strel for linking
				int[] temp = new int[filterSize * filterSize];
				drawLine(temp, filterSize, c - x, c - y, c + x, c + y);
				strels[i] = cvCreateStructuringElementEx(filterSize,
						filterSize, filterSize / 2, filterSize / 2,
						CV_SHAPE_CUSTOM, temp);
			}
		}

		// get the best angle for each pixel
		// the best angle is the one with the greatest value after filtering
		// the filters are stored in the array filters
		{
			IplImage filtered = temp32f;
			for (int i = 0; i < filters.length; i++) {
				CvMat filter = filters[i];
				// int sum = filterSums[i];

				cvFilter2D(input, filtered, filter, cvPoint(-1, -1));

				// cvScale(filtered, filtered, 1.0 / sum, 0);

				cvCmp(max, filtered, mask, CV_CMP_LT);
				cvSet(direction, cvScalarAll(i), mask);
				cvMax(max, filtered, max);
			}

			// cvShowImage("max", max);
			// cvWaitKey();
		}

		// debug output
		// outputFilteredImages(img, direction, mask, temp32f);

		// Dilate angles to privilege better angles
		{
			IplImage temp = direction.clone();
			IplImage maxDil = max.clone();
			IplImage dil = temp32f;

			DilateProcessor processor = new DilateProcessor(dilateSize);

			cvSetZero(direction);
			for (int i = 0; i < filters.length; i++) {
				cvCmpS(temp, i, mask, CV_CMP_EQ);
				cvSetZero(dil);
				cvSet(dil, CvScalar.ONE, mask);
				cvMul(max, dil, dil, 1);

				processor.process(dil, null);

				cvCmp(maxDil, dil, mask, CV_CMP_LE);
				cvSet(direction, cvScalarAll(i), mask);
				cvMax(maxDil, dil, maxDil);
			}

			// cvShowImage("max", maxDil);
			// cvWaitKey();
		}

		// debug output
		// outputDirections(img, direction, mask, temp32f, numAngles);

		hists = new HashMap<Feature, Histogram>();
		for (Feature feature : features) {
			// create a mask of the feature
			cvSetZero(mask);
			feature.fill(mask, CvScalar.WHITE);

			// set sub-images of direction and mask
			CvMat subDirection;
			CvMat subMask;
			{
				Vector2D fmin = feature.box().min();
				Vector2D fmax = feature.box().max();

				int xmin = (int) Math.min(Math.max(fmin.x, 0), img.width() - 1);
				int xmax = (int) Math.min(Math.max(fmax.x, 0), img.width() - 1);
				int ymin = (int) Math
						.min(Math.max(fmin.y, 0), img.height() - 1);
				int ymax = (int) Math
						.min(Math.max(fmax.y, 0), img.height() - 1);
				int width = xmax - xmin;
				int height = ymax - ymin;

				subDirection = CvMat.createHeader(height, width, CV_8UC1);
				subMask = CvMat.createHeader(height, width, CV_8UC1);

				CvRect rect = cvRect(xmin, ymin, width, height);

				cvGetSubRect(direction, subDirection, rect);
				cvGetSubRect(mask, subMask, rect);
			}

			// set histogram
			Histogram hist = new Histogram(subDirection, subMask,
					filters.length);
			/*
			 * double[] hist = new double[filters.length]; for (int i = 0; i <
			 * subDirection.rows(); i++) { for (int j = 0; j <
			 * subDirection.cols(); j++) { if (cvGetReal2D(subMask, i, j) != 0)
			 * { int val = (int) cvGetReal2D(subDirection, i, j); hist[val]++; }
			 * } }
			 * 
			 * // System.out.println(Arrays.toString(hist));
			 * 
			 * // calculate sum double sum = 0; for (int i = 0; i < hist.length;
			 * i++) { sum += hist[i]; }
			 * 
			 * // calculate percentages for (int i = 0; i < hist.length; i++) {
			 * hist[i] = hist[i] / sum; }
			 */

			hists.put(feature, hist);

			// cvShowImage("mask", mask);
			// cvWaitKey();
		}
	}

	@Override
	public boolean link(Feature f0, Feature f1) {
		Histogram hist0 = hists.get(f0);
		Histogram hist1 = hists.get(f1);

		double angle = 0;
		int idx = 0;

		{
			double max = 0;

			for (int i = 0; i < hist0.size(); i++) {
				double val = Math.min(hist0.get(i), hist1.get(i));
				if (max < val) {
					idx = i;
					max = val;
				}
			}

			angle = idx * Math.PI / hist0.size();
		}

		/*
		 * // revoke links between features that are not in one line { Angle180
		 * angle = new Angle180(f0.position(), f1.position()); int i = (int)
		 * Math.round(angle.getRadians() * hist0.length / Math.PI);
		 * 
		 * if (i == hist0.length) { i = 0; }
		 * 
		 * double val = Math.max(hist0[i], hist1[i]); if (val < minLinkRating) {
		 * return false; } }
		 */

		// revoke links where the features don't have the same direction
		{
			double bestVal = 0;

			for (int i = 0; i < hist0.size(); i++) {
				double val = Math.min(hist0.get(i), hist1.get(i));
				if (bestVal < val) {
					bestVal = val;
				}
			}

			if (bestVal < minFeatureRating) {
				return false;
			}
		}

		//
		{
			LinkedFeature lf = LinkedFeature.create(Arrays.asList(f0, f1));
			CvRect rect = Image.clip(direction, lf.box().min(), lf.box().max());
			int width = rect.width();
			int height = rect.height();

			CvMat imgSub = cvCreateMatHeader(width, height, CV_8UC1);
			CvMat dirSub = cvCreateMatHeader(width, height, CV_8UC1);
			CvMat maskSub = cvCreateMatHeader(width, height, CV_8UC1);
			CvMat tempSub = cvCreateMatHeader(width, height, CV_8UC1);
			cvGetSubRect(img, imgSub, rect);
			cvGetSubRect(direction, dirSub, rect);
			cvGetSubRect(mask, maskSub, rect);
			cvGetSubRect(temp8u, tempSub, rect);

			IplConvKernel strel = strels[idx];
			cvMorphologyEx(imgSub, maskSub, tempSub, strel, CV_MOP_BLACKHAT, 1);

			
			/*cvSet(maskSub, CvScalar.WHITE, maskSub);
			cvShowImage("img", imgSub);
			cvShowImage("mask", maskSub);
			cvWaitKey();*/

			Histogram hist = new Histogram(dirSub, maskSub, hist0.size());
			int max = hist.max();
			if (Math.abs(idx - max) > 1) {
				return false;
			}
		}

		return true;
	}

	private void drawLine(int[] img, int width, int x0, int y0, int x1, int y1) {
		int dx = Math.abs(x1 - x0);
		int dy = Math.abs(y1 - y0);
		int sx;
		int sy;

		sx = x0 < x1 ? 1 : -1;
		sy = y0 < y1 ? 1 : -1;

		int err = dx - dy;

		img[y0 * width + x0] = 1;
		while (x0 != x1 || y0 != y1) {
			int e2 = 2 * err;
			if (e2 > -dy) {
				err = err - dy;
				x0 = x0 + sx;
			}
			if (e2 < dx) {
				err = err + dx;
				y0 = y0 + sy;
			}
			img[y0 * width + x0] = 1;
		}
	}

	private void outputFilteredImages(IplImage img, IplImage direction,
			IplImage mask, IplImage temp32f, int numAngles) {
		for (int i = 0; i < numAngles; i++) {
			cvCmpS(direction, i, mask, CV_CMP_EQ);
			cvAnd(mask, img, mask, null);
			cvSetZero(temp32f);
			cvSet(temp32f, CvScalar.ONE, mask);

			cvShowImage("map", temp32f);
			cvWaitKey();
		}
	}

	private void outputDirections(IplImage img, IplImage direction,
			IplImage mask, IplImage temp32f, int numAngles) {
		int scale = 5;
		int width = img.width() * scale;
		int height = img.height() * scale;
		IplImage imgL = IplImage.create(img.width() * scale, img.height()
				* scale, IPL_DEPTH_8U, 1);
		IplImage directionL = IplImage.create(img.width() * scale, img.height()
				* scale, IPL_DEPTH_8U, 1);
		IplImage maskL = IplImage.create(img.width() * scale, img.height()
				* scale, IPL_DEPTH_8U, 1);
		IplImage angle = IplImage.create(img.width() * scale, img.height()
				* scale, IPL_DEPTH_32F, 1);
		cvResize(img, imgL, CV_INTER_NN);
		cvResize(direction, directionL, CV_INTER_NN);
		cvResize(mask, maskL, CV_INTER_NN);
		/*
		 * int width = 200*scale; int height = 120*scale; int px = 800, py =
		 * 600; //int px = 850, py = 650; //int px = 620, py = 580; CvRect roi =
		 * cvRect(px*scale, py*scale, width, height); cvSetImageROI(imgL, roi);
		 * cvSetImageROI(directionL, roi); cvSetImageROI(maskL, roi);
		 * cvSetImageROI(angle, roi);
		 */
		img = imgL;
		direction = directionL;
		mask = maskL;

		/*
		 * IplImage angle = temp32f;
		 */
		int size3 = 5;
		int border3 = size3 / 2 + 1;

		img = img.clone();

		// get angle-map
		cvSetZero(angle);
		for (int i = 0; i < numAngles; i++) {
			double a = i / (double) numAngles * Math.PI;

			cvCmpS(direction, i, mask, CV_CMP_EQ);
			cvSet(angle, cvScalarAll(a), mask);
		}

		// average angles over the displayed area
		// not always correct (180� == 0�)
		// cvSmooth(angle, angle, CV_BLUR, size3);

		// draw angles
		for (int y = border3; y < height - border3; y += size3) {
			for (int x = border3; x < width - border3; x += size3) {
				double a = cvGetReal2D(angle, y, x);
				CvPoint p0 = cvPoint(
						x - (int) Math.round(Math.cos(a) * border3), y
								- (int) Math.round(Math.sin(a) * border3));
				CvPoint p1 = cvPoint(
						x + (int) Math.round(Math.cos(a) * border3), y
								+ (int) Math.round(Math.sin(a) * border3));
				cvDrawLine(img, p0, p1, CvScalar.GRAY, 1, 0, 0);
			}
		}

		cvSaveImage("direction.png", img);
		// cvShowImage("img", img);
		cvWaitKey();
	}
}
