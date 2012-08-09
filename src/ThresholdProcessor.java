import static com.googlecode.javacv.cpp.opencv_imgproc.CV_THRESH_BINARY;
import static com.googlecode.javacv.cpp.opencv_imgproc.cvThreshold;

import com.googlecode.javacv.cpp.opencv_core.IplImage;


public class ThresholdProcessor implements ImageProcessor{
	private int threshold;
	
	public ThresholdProcessor(int threshold) {
		this.threshold = threshold;
	}

	@Override
	public void process(IplImage img) {
		cvThreshold(img, img, threshold, 255, CV_THRESH_BINARY);
	}
}
