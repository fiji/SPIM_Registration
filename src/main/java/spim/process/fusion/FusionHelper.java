package spim.process.fusion;

import java.util.ArrayList;
import java.util.Vector;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import spim.fiji.spimdata.SpimData2;

public class FusionHelper 
{
	/**
	 * Do not instantiate
	 */
	private FusionHelper() {}
	
	public static final boolean intersects( final float x, final float y, final float z, final int sx, final int sy, final int sz )
	{
		if ( x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz )
			return true;
		else
			return false;
	}

	public static final ArrayList< ViewDescription< TimePoint, ViewSetup > > assembleInputData(
			final SpimData2 spimData,
			final TimePoint timepoint,
			final Channel channel,
			final ArrayList< Angle > anglesToProcess,
			final ArrayList< Illumination > illumsToProcess )
	{
		final ArrayList< ViewDescription< TimePoint, ViewSetup > > inputData = new ArrayList< ViewDescription< TimePoint, ViewSetup > >();
		
		for ( final Illumination i : illumsToProcess )
			for ( final Angle a : anglesToProcess )
			{
				// bureaucracy
				final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), timepoint, channel, a, i );
				
				final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
						viewId.getTimePointId(), viewId.getViewSetupId() );

				if ( !viewDescription.isPresent() )
					continue;
				
				// get the most recent model
				spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();
				
				inputData.add( viewDescription );
			}

		return inputData;
	}

	public static final Vector<ImagePortion> divideIntoPortions( final long imageSize, final int numPortions )
	{
        final long threadChunkSize = imageSize / numPortions;
        final long threadChunkMod = imageSize % numPortions;
        
        final Vector<ImagePortion> portions = new Vector<ImagePortion>();
        
        for ( int portionID = 0; portionID < numPortions; ++portionID )
        {
        	// move to the starting position of the current thread
        	final long startPosition = portionID * threadChunkSize;

            // the last thread may has to run longer if the number of pixels cannot be divided by the number of threads
            final long loopSize;		                    
            if ( portionID == numPortions - 1 )
            	loopSize = threadChunkSize + threadChunkMod;
            else
            	loopSize = threadChunkSize;
        	
            portions.add( new ImagePortion( startPosition, loopSize ) );
        }
        
        return portions;
	}

}
