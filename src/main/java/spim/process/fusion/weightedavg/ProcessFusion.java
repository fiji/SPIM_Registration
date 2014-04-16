package spim.process.fusion.weightedavg;

import java.util.ArrayList;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.RandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;

public abstract class ProcessFusion
{
	final protected SpimData2 spimData;
	final protected ArrayList<Angle> anglesToProcess;
	final protected ArrayList<Illumination> illumsToProcess;
	final BoundingBox bb;
	
	public ProcessFusion(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final BoundingBox bb )
	{
		this.spimData = spimData;
		this.anglesToProcess = anglesToProcess;
		this.illumsToProcess = illumsToProcess;
		this.bb = bb;
	}
	
	public abstract < T extends RealType< T > & NativeType< T > > Img< T > fuseStack(
			final T type,
			final InterpolatorFactory< T, RandomAccessible< T > > interpolatorFactory,
			final TimePoint timepoint, 
			final Channel channel );

	protected AffineTransform3D[] getTransforms( final ArrayList< ViewDescription< TimePoint, ViewSetup > > inputData )
	{
		final int numViews = inputData.size();
		final AffineTransform3D[] transforms = new AffineTransform3D[ numViews ];

		for ( int i = 0; i < numViews; ++i )
		{
			final ViewDescription< TimePoint, ViewSetup > view = inputData.get( i );
			transforms[ i ] = spimData.getViewRegistrations().getViewRegistration( view ).getModel(); 			
		}
		
		return transforms;
	}

}
