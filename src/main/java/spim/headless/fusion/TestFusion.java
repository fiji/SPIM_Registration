package spim.headless.fusion;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.headless.boundingbox.TestBoundingBox;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.TransformView;
import spim.process.fusion.transformed.TransformVirtual;
import spim.process.fusion.transformed.TransformWeight;
import spim.process.fusion.weightedavg.ProcessFusion;
import spim.process.fusion.weightedavg.ProcessVirtual;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TestFusion
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testFusion( spimData );
	}

	public static void testFusion( final SpimData2 spimData )
	{
		final BoundingBox bb = TestBoundingBox.testBoundingBox( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// downsampling
		double downsampling = Double.NaN;

		final long[] dim = new long[ bb.numDimensions() ];
		bb.dimensions( dim );

		final ArrayList< RandomAccessibleInterval< FloatType > > images = new ArrayList<>();
		final ArrayList< RandomAccessibleInterval< FloatType > > weights = new ArrayList<>();
		
		for ( final ViewId viewId : viewIds )
		{
			final ImgLoader imgloader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			AffineTransform3D model = vr.getModel();

			final float[] blending = ProcessFusion.defaultBlendingRange.clone();
			final float[] border = ProcessFusion.defaultBlendingBorder.clone();

			ProcessVirtual.adjustBlending( spimData.getSequenceDescription().getViewDescription( viewId ), blending, border );

			if ( !Double.isNaN( downsampling ) )
			{
				model = model.copy();
				TransformVirtual.scaleTransform( model, 1.0 / downsampling );
			}

			final RandomAccessibleInterval inputImg = TransformView.openDownsampled( imgloader, viewId, model );

			images.add( TransformView.transformView( inputImg, model, bb, 0, 1 ) );
			weights.add( TransformWeight.transformBlending( inputImg, border, blending, model, bb ) );

			//images.add( TransformWeight.transformBlending( inputImg, border, blending, vr.getModel(), bb ) );
			//weights.add( Views.interval( new ConstantRandomAccessible< FloatType >( new FloatType( 1 ), 3 ), new FinalInterval( dim ) ) );
		}

		DisplayImage.getImagePlusInstance( new FusedRandomAccessibleInterval( new FinalInterval( dim ), images, weights ), true, "Fused, Virtual", 0, 255 ).show();

	}
}
