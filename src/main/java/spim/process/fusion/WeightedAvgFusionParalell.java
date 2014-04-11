package spim.process.fusion;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RealRandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.InterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.spimdata.SpimData2;

public class WeightedAvgFusionParalell< T extends RealType< T > >
{
	final T type;
	final BoundingBox bb;
	final ImgFactory< T > imgFactory;
	final SpimData2 spimData;
	
	public WeightedAvgFusionParalell(
			final BoundingBox bb,
			final T type,
			final ImgFactory< T > imgFactory,
			final SpimData2 spimData )
	{
		this.type = type;				
		this.bb = bb;
		this.imgFactory = imgFactory;
		this.spimData = spimData;
	}
	
	public Img< T > fuseData(
			final InterpolatorFactory<FloatType, RandomAccessible< FloatType > > interpolatorFactory,
			final TimePoint timepoint,
			final Channel channel,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< Illumination > illumsToProcess ) 
	{
		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image.");

		final Img< T > fusedImage = imgFactory.create( bb, type );

		if ( fusedImage == null )
		{
			IOFunctions.println( "WeightedAvgFusionParalell: Cannot create output image. "  );
			return null;
		}
		
		final ArrayList< FusionView > inputData = assembleInputData( spimData, timepoint, channel, anglesToProcess, illumsToProcess );
		final int numViews = inputData.size();
		
		final ArrayList< RealRandomAccess< FloatType > > interpolators = new ArrayList< RealRandomAccess< FloatType > >( numViews );
		final AffineTransform3D[] transforms = new AffineTransform3D[ numViews ];
		
		for ( int i = 0; i < numViews; ++i )
		{
			interpolators.add( Views.interpolate( Views.extendZero( inputData.get( i ).getImg() ), interpolatorFactory ).realRandomAccess()  );
			transforms[ i ] = inputData.get( i ).getRegistration().getModel();
		}
		
		final Cursor< T > cursor = fusedImage.localizingCursor();
		final float[] s = new float[ 3 ];
		final float[] t = new float[ 3 ];
		
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.localize( s );
			
			s[ 0 ] += bb.min( 0 );
			s[ 1 ] += bb.min( 1 );
			s[ 2 ] += bb.min( 2 );
			
			float sum = 0;
			
			for ( int i = 0; i < numViews; ++i )
			{				
				final RealRandomAccess< FloatType > r = interpolators.get( i );
				
				transforms[ i ].applyInverse( s, t );
				r.setPosition( t );
				sum += r.get().get();
			}
			
			cursor.get().setReal( sum / numViews );
		}

		return fusedImage;
	}

	protected ArrayList< FusionView > assembleInputData(
			final SpimData2 spimData,
			final TimePoint timepoint,
			final Channel channel,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< Illumination > illumsToProcess )
	{
		final ArrayList< FusionView > inputData = new ArrayList< FusionView >();
		
		for ( final Illumination i : illumsToProcess )
			for ( final Angle a : anglesToProcess )
			{
				// bureaucracy
				final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), timepoint, channel, a, i );
				
				final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
						viewId.getTimePointId(), viewId.getViewSetupId() );

				if ( !viewDescription.isPresent() )
					continue;

				inputData.add( new FusionView( spimData, viewDescription ) );
			}

		return inputData;
	}
}
