package spim.process.deconvolution.iteration;

import java.util.List;
import java.util.concurrent.ExecutorService;

import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.deconvolution.DeconView;

public interface PsiInitialization
{
	public boolean runInitialization( final Img< FloatType > psi, final List< DeconView > views, final ExecutorService service );

	/**
	 * @return the average in the overlapping area
	 */
	public double getAvg();

	/**
	 * @return the maximal intensities (maybe approximated) of the views, in the same order as the list of DeconView
	 */
	public float[] getMax();
}
