package spim.process.interestpointdetection.methods.dog;

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import spim.process.cuda.CUDADevice;
import spim.process.cuda.CUDASeparableConvolution;
import spim.process.interestpointdetection.methods.InterestPointParameters;

public class DoGParameters extends InterestPointParameters
{
	/**
	 * 0 = no subpixel localization
	 * 1 = quadratic fit
	 */
	public int localization = 1;

	public double sigma = 1.8;
	public double threshold = 0.01;
	public boolean findMin = false;
	public boolean findMax = true;

	public double percentGPUMem = 75;
	public ArrayList< CUDADevice > deviceList = null;
	public CUDASeparableConvolution cuda = null;
	public boolean accurateCUDA = false;

	public DoGParameters() { super(); }

	public DoGParameters(
			final Collection<ViewDescription> toProcess,
			final ImgLoader imgloader,
			final double sigma,
			final double threshold )
	{
		super( toProcess, imgloader );
		this.sigma = sigma;
		this.threshold = threshold;
	}

	public DoGParameters(
			final Collection<ViewDescription> toProcess,
			final ImgLoader imgloader,
			final double sigma, final int downsampleXY )
	{
		super( toProcess, imgloader );
		this.sigma = sigma;
		this.downsampleXY = downsampleXY;
	}
}
