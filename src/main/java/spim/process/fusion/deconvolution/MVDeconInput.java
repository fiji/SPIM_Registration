package spim.process.fusion.deconvolution;

import java.util.ArrayList;

import spim.process.fusion.deconvolution.MVDeconFFT.PSFTYPE;

public class MVDeconInput
{
	public final static float minValue = 0.0001f;
	final ArrayList< MVDeconFFT > views = new ArrayList< MVDeconFFT >();
	
	public void add( final MVDeconFFT view )
	{
		views.add( view );
		
		for ( final MVDeconFFT v : views )
			v.setNumViews( getNumViews() );
	}
		
	/**
	 * init all views
	 * 
	 * @param exponentialKernel - use exponential kernel?
	 * 
	 * @return the same instance again for convinience
	 */
	public MVDeconInput init( final PSFTYPE iterationType )
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
