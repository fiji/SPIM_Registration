package spim.headless.interestpointdetection;

import java.util.Collection;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;

public class InterestPointParameters
{
	Collection< ViewDescription > toProcess;
	ImgLoader< ? > imgloader;

	protected double minIntensity = Double.NaN;
	protected double maxIntensity = Double.NaN;

	// downsampleXY == 0 : a bit less then z-resolution
	// downsampleXY == -1 : a bit more then z-resolution
	protected int downsampleXY = 1, downsampleZ = 1;
}
