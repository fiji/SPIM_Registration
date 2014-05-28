package spim.fiji.spimdata;

import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;

public class ViewSetupUtils
{
	public static VoxelDimensions getVoxelSizeOrDefault( final BasicViewSetup setup, final VoxelDimensions defaultVoxelSize )
	{
		if ( setup.hasVoxelSize() )
			return setup.getVoxelSize();
		else
			return defaultVoxelSize;
	}

	public static VoxelDimensions getVoxelSizeOrDefault( final BasicViewSetup setup )
	{
		if ( setup.hasVoxelSize() )
			return setup.getVoxelSize();
		else
			return new FinalVoxelDimensions( "px", 1, 1, 1 );
	}

	public static Dimensions getSizeOrDefault( final BasicViewSetup setup, final Dimensions defaultSize )
	{
		if ( setup.hasSize() )
			return setup.getSize();
		else
			return defaultSize;
	}

	public static Dimensions getSizeOrDefault( final BasicViewSetup setup )
	{
		if ( setup.hasSize() )
			return setup.getSize();
		else
			return new FinalDimensions( 0, 0, 0 );
	}
}
