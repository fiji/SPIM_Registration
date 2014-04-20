package spim.process.fusion.boundingbox;

import java.util.ArrayList;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.FinalRealInterval;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.spimdata.SpimData2;
import spim.process.fusion.export.ImgExport;

public class CompleteBoundingBox extends ManualBoundingBox
{
	public CompleteBoundingBox(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
	{
		super( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		final double[] minBB = new double[ 3 ];
		final double[] maxBB = new double[ 3 ];
		
		computeMaximalBoundingBox( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess, minBB, maxBB );
		
		for ( int d = 0; d < minBB.length; ++d )
		{
			BoundingBox.minStatic[ d ] = (int)Math.floor( minBB[ d ] );
			BoundingBox.maxStatic[ d ] = (int)Math.floor( maxBB[ d ] );
		}
		
		return super.queryParameters( fusion, imgExport );
	}

	public static void computeMaximalBoundingBox(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess,
			final double[] minBB, final double[] maxBB )
	{
		for ( int d = 0; d < minBB.length; ++d )
		{
			minBB[ d ] = Double.MAX_VALUE;
			maxBB[ d ] = -Double.MAX_VALUE;
		}
		
		for ( final TimePoint t: timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						// bureaucracy
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
						
						final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );
		
						if ( !viewDescription.isPresent() )
							continue;
						
						final double[] min = new double[]{ 0, 0, 0 };
						final double[] max = new double[]{
								viewDescription.getViewSetup().getWidth() - 1,
								viewDescription.getViewSetup().getHeight() - 1,
								viewDescription.getViewSetup().getDepth() - 1 };
						
						final ViewRegistration r = spimData.getViewRegistrations().getViewRegistration( viewId );
						r.updateModel();
						final FinalRealInterval interval = r.getModel().estimateBounds( new FinalRealInterval( min, max ) );
						
						for ( int d = 0; d < minBB.length; ++d )
						{
							minBB[ d ] = Math.min( minBB[ d ], interval.realMin( d ) );
							maxBB[ d ] = Math.max( maxBB[ d ], interval.realMax( d ) );
						}
					}		
	}

	@Override
	public CompleteBoundingBox newInstance(
			final SpimData2 spimData,
			final ArrayList<Angle> anglesToProcess,
			final ArrayList<Channel> channelsToProcess,
			final ArrayList<Illumination> illumsToProcess,
			final ArrayList<TimePoint> timepointsToProcess )
	{
		return new CompleteBoundingBox( spimData, anglesToProcess, channelsToProcess, illumsToProcess, timepointsToProcess );
	}

	@Override
	public String getDescription() { return "Entire dataset"; }
}
