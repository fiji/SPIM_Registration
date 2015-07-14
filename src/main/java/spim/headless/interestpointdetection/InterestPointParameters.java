package spim.headless.interestpointdetection;

import java.util.Collection;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;

public class InterestPointParameters
{
	public Collection<ViewDescription> toProcess;
	public ImgLoader<?> imgloader;

	public double minIntensity = Double.NaN;
	public double maxIntensity = Double.NaN;

	// downsampleXY == 0 : a bit less then z-resolution
	// downsampleXY == -1 : a bit more then z-resolution
	public int downsampleXY = 1, downsampleZ = 1;

	public InterestPointParameters() {}

	public InterestPointParameters(
			final Collection<ViewDescription> toProcess,
			final ImgLoader<?> imgloader )
	{
		this.toProcess = toProcess;
		this.imgloader = imgloader;
	}
}
