package spim.process.fusion.deconvolution;

import java.util.ArrayList;

import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import spim.process.fusion.deconvolution.MVDeconFFT.PSFTYPE;

public class MVDeconInput
{
	public final static float minValue = 0.0001f;
	final ArrayList< MVDeconFFT > views = new ArrayList< MVDeconFFT >();
	final private ImgFactory< FloatType > imgFactory;

	/**
	 * the imgfactory used for PSI, the temporary images and inputs
	 * @param imgFactory
	 */
	public MVDeconInput( final ImgFactory< FloatType > imgFactory )
	{
		this.imgFactory = imgFactory;
	}

	public ImgFactory< FloatType > imgFactory() { return imgFactory; }

	public void add( final MVDeconFFT view )
	{
		views.add( view );
		
		for ( final MVDeconFFT v : views )
			v.setNumViews( getNumViews() );
	}
		
	/**
	 * init all views
	 *
	 * @return the same instance again for convenience
	 * @throws IncompatibleTypeException 
	 */
	public MVDeconInput init( final PSFTYPE iterationType ) throws IncompatibleTypeException
	{
		for ( final MVDeconFFT view : views )
			view.init( iterationType, views );
		
		return this;
	}
	
	/**
	 * @return - the image data
	 */
	public ArrayList< MVDeconFFT > getViews() { return views; }
	
	/**
	 * The number of views for this deconvolution
	 * @return
	 */
	public int getNumViews() { return views.size(); }
}
