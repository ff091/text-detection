import static com.googlecode.javacv.cpp.opencv_core.*;
import static com.googlecode.javacv.cpp.opencv_imgproc.*;

import com.googlecode.javacv.cpp.opencv_core.IplImage;

public class RGBRatioEraseProcessor implements ImageProcessor {
	private double rgMinRatio;
	private double rgMaxRatio;
	private double rbMinRatio;
	private double rbMaxRatio;
	private double gbMinRatio;
	private double gbMaxRatio;
	private int border;
	private boolean and;

	public RGBRatioEraseProcessor(double rgMinRatio, double rgMaxRatio,
			double rbMinRatio, double rbMaxRatio, double gbMinRatio,
			double gbMaxRatio, int border, boolean and) {
		this.rgMinRatio = rgMinRatio;
		this.rgMaxRatio = rgMaxRatio;
		this.rbMinRatio = rbMinRatio;
		this.rbMaxRatio = rbMaxRatio;
		this.gbMinRatio = gbMinRatio;
		this.gbMaxRatio = gbMaxRatio;
		this.border = border;
		this.and = and;
	}

	@Override
	public void process(ImageCollection images) {
		IplImage processed = images.getProcessed();
		IplImage temp = images.getTemp();

		IplImage rgTemp = cvCloneImage(images.getRGRatio());
		IplImage rbTemp = cvCloneImage(images.getRBRatio());
		IplImage gbTemp = cvCloneImage(images.getGBRatio());

		cvInRangeS(rgTemp, cvScalarAll(rgMinRatio), cvScalarAll(rgMaxRatio),
				rgTemp);
		cvInRangeS(rbTemp, cvScalarAll(rbMinRatio), cvScalarAll(rbMaxRatio),
				rbTemp);
		cvInRangeS(gbTemp, cvScalarAll(gbMinRatio), cvScalarAll(gbMaxRatio),
				gbTemp);

		cvSetZero(temp);

		if (and) {
			cvAnd(rgTemp, rbTemp, temp, null);
			cvAnd(temp, gbTemp, temp, null);
		} else {
			cvOr(rgTemp, rbTemp, temp, null);
			cvOr(temp, gbTemp, temp, null);
		}

		cvSet(temp, CvScalar.WHITE, temp);

		new DilateProcessor(border).process(temp, null);

		cvSet(processed, CvScalar.BLACK, temp);
	}

}
