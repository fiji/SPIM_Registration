package spim.fiji.spimdata.imgloaders;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ij.IJ;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import mpicbg.imglib.wrapper.ImgLib1;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;


public class LegacyFileMapImgLoaderLOCI extends AbstractImgFactoryImgLoader
{
	
	private HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap;
	private AbstractSequenceDescription< ?, ?, ? > sd;
	private IFormatReader reader;
	private boolean allTimepointsInSingleFiles;

	public LegacyFileMapImgLoaderLOCI(
			HashMap<BasicViewDescription< ? >, Pair<File, Pair<Integer, Integer>>> fileMap,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription<?, ?, ?> sequenceDescription )
	{
		super();
		this.fileMap = fileMap;
		this.sd = sequenceDescription;
		this.reader = new ImageReader();

		setImgFactory( imgFactory );
		
		allTimepointsInSingleFiles = true;
		Map<Integer, Set<File>> tpSources = new HashMap<>();
		for (TimePoint tp : sd.getTimePoints().getTimePointsOrdered()){
			for (BasicViewDescription< ? > vd : fileMap.keySet())
			{
				if (vd.getTimePoint().equals( tp ))
				{
					if (!tpSources.containsKey( tp.getId() ))
						tpSources.put( tp.getId(), new HashSet<>() );
					
					if (tpSources.get( tp.getId() ).contains( fileMap.get( vd ).getA() ))
					{
						allTimepointsInSingleFiles = false;
						break;
					}
						
					
					tpSources.get( tp.getId() ).add( fileMap.get( vd ).getA() );
				}
			}
			
			if (!allTimepointsInSingleFiles)
				break;
		}
		System.out.println( allTimepointsInSingleFiles );
		
	}
	
	
	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage(ViewId view, boolean normalize)
	{
		try
		{
			final Img< FloatType > img = openImg( new FloatType(), view );

			if ( img == null )
				throw new RuntimeException( "Could not load '" + fileMap.get( sd.getViewDescriptions( ).get( view ) ).getA() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			if ( normalize )
				normalize( img );

			return img;
		}
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + fileMap.get( sd.getViewDescriptions( ).get( view ) ).getA() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	
	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage(ViewId view)
	{
		// TODO should the Type here be fixed to UnsignedShortTyppe?
		try
		{
			final Img< UnsignedShortType > img = openImg( new UnsignedShortType(), view );

			if ( img == null )
				throw new RuntimeException( "Could not load '" + fileMap.get( sd.getViewDescriptions( ).get( view ) ).getA() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			
			return img;
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			throw new RuntimeException( "Could not load '" + fileMap.get( sd.getViewDescriptions( ).get( view ) ).getA() + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	protected void loadMetaData(ViewId view)
	{
		BasicViewDescription< ? > vd = sd.getViewDescriptions().get( view );
		try
		{
//			System.out.println( fileMap.get( vd ).getA().getAbsolutePath() );
			if (reader.getCurrentFile() == null || !reader.getCurrentFile().equals( fileMap.get( vd ).getA().getAbsolutePath()))
				reader.setId( fileMap.get( vd ).getA().getAbsolutePath() );
		
			
		}
		catch ( FormatException | IOException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		reader.setSeries( fileMap.get( vd ).getB().getA() );
		
		
		// we already read view sizes and voxel dimensions when setting up sd
		// here, we pass them to the ImgLoaders metaDataCache, so that that knows the sizes as well		
		int d0 = (int) vd.getViewSetup().getSize().dimension( 0 );
		int d1 = (int) vd.getViewSetup().getSize().dimension( 1 );
		int d2 = (int) vd.getViewSetup().getSize().dimension( 2 );
		
		double vox0 = vd.getViewSetup().getVoxelSize().dimension( 0 );
		double vox1 = vd.getViewSetup().getVoxelSize().dimension( 1 );
		double vox2 = vd.getViewSetup().getVoxelSize().dimension( 2 );
		updateMetaDataCache( view, d0, d1, d2, vox0, vox1, vox2 );

	}
	
	protected < T extends RealType< T > & NativeType< T > > Img< T > openImg( final T type, final ViewId view ) throws Exception
	{
		// sets reader to correct File and series
		loadMetaData( view );

		final BasicViewDescription< ? > vd = sd.getViewDescriptions().get( view );
		final BasicViewSetup vs = vd.getViewSetup();		
		File file = fileMap.get( vd).getA();

		final TimePoint t = vd.getTimePoint();
		final Angle a = getAngle( vd );
		final Channel c = getChannel( vd );
		final Illumination i = getIllumination( vd );
		final Tile tile = getTile( vd ); 

		// we assume the size to have been set beforehand
		final int[] dim;
		dim = new int[vs.getSize().numDimensions()];
		for ( int d = 0; d < vs.getSize().numDimensions(); ++d )
			dim[d] = (int) vs.getSize().dimension( d );

		final Img< T > img = imgFactory.imgFactory( type ).create( dim, type );

		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + file + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ", most likely out of memory." );

		final boolean isLittleEndian = reader.isLittleEndian();
		final boolean isArray = ArrayImg.class.isInstance( img );
		final int pixelType = reader.getPixelType();
		final int width = dim[ 0 ];
		final int height = dim[ 1 ];
		final int depth = dim[ 2 ];
		final int numPx = width * height;

		final byte[] b = new byte[ numPx * reader.getBitsPerPixel() / 8 ];

		try
		{
			// we have already openend the file

			IOFunctions.println(
					new Date( System.currentTimeMillis() ) + ": Reading image data from '" + file.getName() + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] +
					" angle=" + a.getName() + " ch=" + c.getName() + " illum=" + i.getName() + " tp=" + t.getName() + " type=" + FormatTools.getPixelTypeString( reader.getPixelType()) +
					" img=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

			
			int ch = fileMap.get( vd ).getB().getB();
			int tpNo = allTimepointsInSingleFiles ? 0 : t.getId();

			for ( int z = 0; z < depth; ++z )
			{
				IJ.showProgress( (double)z / (double)depth );

				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();

				reader.openBytes( reader.getIndex( z, ch, tpNo ), b );

				if ( pixelType == FormatTools.UINT8 )
				{
					if ( isArray )
						LegacyLightSheetZ1ImgLoader.readBytesArray( b, cursor, numPx );
					else
						LegacyLightSheetZ1ImgLoader.readBytes( b, cursor, width );
				}
				else if ( pixelType == FormatTools.UINT16 )
				{
					if ( isArray )
						LegacyLightSheetZ1ImgLoader.readUnsignedShortsArray( b, cursor, numPx, isLittleEndian );
					else
						LegacyLightSheetZ1ImgLoader.readUnsignedShorts( b, cursor, width, isLittleEndian );
				}
				else if ( pixelType == FormatTools.INT16 )
				{
					if ( isArray )
						LegacyLightSheetZ1ImgLoader.readSignedShortsArray( b, cursor, numPx, isLittleEndian );
					else
						LegacyLightSheetZ1ImgLoader.readSignedShorts( b, cursor, width, isLittleEndian );
				}
				else if ( pixelType == FormatTools.UINT32 )
				{
					//TODO: Untested
					if ( isArray )
						LegacyLightSheetZ1ImgLoader.readUnsignedIntsArray( b, cursor, numPx, isLittleEndian );
					else
						LegacyLightSheetZ1ImgLoader.readUnsignedInts( b, cursor, width, isLittleEndian );
				}
				else if ( pixelType == FormatTools.FLOAT )
				{
					if ( isArray )
						LegacyLightSheetZ1ImgLoader.readFloatsArray( b, cursor, numPx, isLittleEndian );
					else
						LegacyLightSheetZ1ImgLoader.readFloats( b, cursor, width, isLittleEndian );
				}
			}

			IJ.showProgress( 1 );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "File '" + file.getAbsolutePath() + "' could not be opened: " + e );
			IOFunctions.println( "Stopping" );

			e.printStackTrace();
			try { reader.close(); } catch (IOException e1) { e1.printStackTrace(); }
			return null;
		}

		return img;
	}
	
	protected static Angle getAngle( final AbstractSequenceDescription< ?, ?, ? > seqDesc, final ViewId view )
	{
		return getAngle( seqDesc.getViewDescriptions().get( view ) );
	}

	protected static Angle getAngle( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Angle angle = vs.getAttribute( Angle.class );

		if ( angle == null )
			throw new RuntimeException( "This XML does not have the 'Angle' attribute for their ViewSetup. Cannot continue." );

		return angle;
	}

	protected static Channel getChannel( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Channel channel = vs.getAttribute( Channel.class );

		if ( channel == null )
			throw new RuntimeException( "This XML does not have the 'Channel' attribute for their ViewSetup. Cannot continue." );

		return channel;
	}

	protected static Illumination getIllumination( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Illumination illumination = vs.getAttribute( Illumination.class );

		if ( illumination == null )
			throw new RuntimeException( "This XML does not have the 'Illumination' attribute for their ViewSetup. Cannot continue." );

		return illumination;
	}
	
	protected static Tile getTile( final BasicViewDescription< ? > vd )
	{
		final BasicViewSetup vs = vd.getViewSetup();
		final Tile tile = vs.getAttribute( Tile.class );

		if ( tile == null )
			throw new RuntimeException( "This XML does not have the 'Illumination' attribute for their ViewSetup. Cannot continue." );

		return tile;
	}


	public HashMap< BasicViewDescription< ? >, Pair< File, Pair< Integer, Integer > > > getFileMap()
	{
		return fileMap;
	}

}
