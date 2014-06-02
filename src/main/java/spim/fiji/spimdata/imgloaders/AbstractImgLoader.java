package spim.fiji.spimdata.imgloaders;

import java.util.HashMap;

import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Pair;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.ValuePair;

public abstract class AbstractImgLoader implements ImgLoader< UnsignedShortType >
{
	protected ImgFactory< ? extends NativeType< ? > > imgFactory;
	
	private final HashMap< ViewId, Pair< Dimensions, VoxelDimensions > > imageMetaData;
	
	private final UnsignedShortType type;

	protected AbstractImgLoader()
	{
		imgFactory = null;
		imageMetaData = new HashMap< ViewId, Pair< Dimensions, VoxelDimensions > >();
		type = new UnsignedShortType();
	}

	/**
	 * Updates the cached ViewSetup if the respective values are still not cached,
	 * or <code>forceUpdate == true</code>; 
	 */
	protected void updateXMLMetaData( final ViewId view,
			final int w, final int h, final int d,
			final double calX, final double calY, final double calZ,
			final boolean forceUpdate )
	{
		final Pair< Dimensions, VoxelDimensions > metadata = imageMetaData.get( view );
		if ( metadata == null || forceUpdate )
			imageMetaData.put( view, new ValuePair< Dimensions, VoxelDimensions >(
					new FinalDimensions( w, h, d ),
					new FinalVoxelDimensions( "", calX, calY, calZ ) ) );
	}

	protected abstract void loadMetaData( final ViewId view );

	@Override
	public Dimensions getImageSize( final ViewId view )
	{
		if ( ! imageMetaData.containsKey( view ) )
			loadMetaData( view );
		return imageMetaData.get( view ).getA();
	}

	@Override
	public VoxelDimensions getVoxelSize( final ViewId view )
	{
		if ( ! imageMetaData.containsKey( view ) )
			loadMetaData( view );
		return imageMetaData.get( view ).getB();
	}
	
	@Override
	public UnsignedShortType getImageType()
	{
		return new UnsignedShortType();
	}	
		
	public ImgFactory< ? extends NativeType< ? > > getImgFactory() { return imgFactory; }
	public void setImgFactory( final ImgFactory< ? extends NativeType< ? > > imgFactory ) { this.imgFactory = imgFactory; }
}
