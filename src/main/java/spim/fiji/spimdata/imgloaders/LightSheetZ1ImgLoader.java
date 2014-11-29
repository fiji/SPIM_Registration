package spim.fiji.spimdata.imgloaders;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.datasetmanager.LightSheetZ1MetaData;

public class LightSheetZ1ImgLoader extends AbstractImgLoader
{
	final File cziFile;
	final SequenceDescription sequenceDescription;

	// once the metadata is loaded for one view, it is available for all other ones
	LightSheetZ1MetaData meta;
	
	public LightSheetZ1ImgLoader(
			final File cziFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final SequenceDescription sequenceDescription )
	{
		super();
		this.cziFile = cziFile;
		this.sequenceDescription = sequenceDescription;
		
		setImgFactory( imgFactory );
	}
	
	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		try
		{
			final Img< FloatType > img = openCZI( new FloatType(), view );
			
			if ( img == null )
				throw new RuntimeException( "Could not load '" + cziFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			if ( normalize )
			{
				float min = Float.MAX_VALUE;
				float max = -Float.MAX_VALUE;

				for ( final FloatType t : img )
				{
					final float v = t.get();

					if ( v < min )
						min = v;

					if ( v > max )
						max = v;
				}

				for ( final FloatType t : img )
					t.set( ( t.get() - min ) / ( max - min ) );
			}

			// update the MetaDataCache of the AbstractImgLoader
			// this does not update the XML ViewSetup but has to be called explicitly before saving
			final ViewDescription vd = sequenceDescription.getViewDescription( view );
			final int[] dim = meta.imageSizes().get( vd.getViewSetup().getAngle().getId() );
			updateMetaDataCache(
					view, dim[ 0 ], dim[ 1 ], dim[ 2 ],
					meta.calX(), meta.calY(), meta.calZ() );

			return img;
		}
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + cziFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		try
		{
			final Img< UnsignedShortType > img = openCZI( new UnsignedShortType(), view );
			
			if ( img == null )
				throw new RuntimeException( "Could not load '" + cziFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );
			
			// update the MetaDataCache of the AbstractImgLoader
			// this does not update the XML ViewSetup but has to be called explicitly before saving
			final ViewDescription vd = sequenceDescription.getViewDescription( view );
			final int[] dim = meta.imageSizes().get( vd.getViewSetup().getAngle().getId() );
			updateMetaDataCache(
					view, dim[ 0 ], dim[ 1 ], dim[ 2 ],
					meta.calX(), meta.calY(), meta.calZ() );

			return img;
		} 
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + cziFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		if ( meta == null )
		{
			meta = new LightSheetZ1MetaData();

			if ( !meta.loadMetaData( cziFile ) )
			{
				IOFunctions.println( "Failed to analyze file: '" + cziFile.getAbsolutePath() + "'." );
				meta = null;
				return;
			}
		}

		// update the MetaDataCache of the AbstractImgLoader
		// this does not update the XML ViewSetup but has to be called explicitly before saving
		final ViewDescription vd = sequenceDescription.getViewDescription( view );
		final int[] dim = meta.imageSizes().get( vd.getViewSetup().getAngle().getId() );
		
		updateMetaDataCache(
				view, dim[ 0 ], dim[ 1 ], dim[ 2 ],
				meta.calX(), meta.calY(), meta.calZ() );
	}

	protected < T extends RealType< T > & NativeType< T > > Img< T > openCZI( final T type, final ViewId view ) throws Exception
	{
		if ( meta == null )
		{
			meta = new LightSheetZ1MetaData();

			if ( !meta.loadMetaData( cziFile ) )
			{
				IOFunctions.println( "Failed to analyze file: '" + cziFile.getAbsolutePath() + "'." );
				meta = null;
				return null;
			}
		}

		final ViewDescription vd = sequenceDescription.getViewDescription( view );
		final ViewSetup vs = vd.getViewSetup();

		final TimePoint t = vd.getTimePoint();
		final Angle a = vs.getAngle();
		final Channel c = vs.getChannel();
		final Illumination i = vs.getIllumination();

		if ( !vs.hasSize() )
		{
			loadMetaData( view );
			updateXMLMetaData( vs, false );
		}

		if ( !vs.hasSize() )
		{
			IOFunctions.println( "Failed to load meta data for CZI dataset: " + cziFile.getAbsolutePath() + ". Stopping." );
			return null;
		}

		final Dimensions dim = vs.getSize();
		final Img< T > img = imgFactory.imgFactory( type ).create( dim, type );

		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + cziFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ", most likely out of memory." );

		IOFunctions.println(
				new Date( System.currentTimeMillis() ) + ": Opening '" + cziFile.getName() + "' [" + dim.dimension( 0 ) + "x" + dim.dimension( 1 ) + "x" + dim.dimension( 2 ) +
				" angle=" + a.getName() + " ch=" + c.getName() + " illum=" + i.getName() + " tp=" + t.getName() + " type=" + meta.pixelTypeString() + 
				" img=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

		final boolean isLittleEndian = meta.isLittleEndian();
		final boolean isArray = ArrayImg.class.isInstance( img );
		final int pixelType = meta.pixelType();
		final int width = (int)dim.dimension( 0 );
		final int height = (int)dim.dimension( 1 );
		final int numPx = width * height;
		final IFormatReader r = LightSheetZ1ImgLoader.instantiateImageReader();

		final byte[] b = new byte[ numPx * meta.bytesPerPixel() ];

		try
		{
			// open the file
			r.setId( cziFile.getAbsolutePath() );

			// set the right angle
			r.setSeries( a.getId() );

			// compute the right channel from channelId & illuminationId
			int ch = c.getId() * meta.numIlluminations() + i.getId();

			for ( int z = 0; z < (int)dim.dimension( 2 ); ++z )
			{
				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();

				r.openBytes( r.getIndex( z, ch, t.getId() ), b );

				if ( pixelType == FormatTools.UINT8 )
				{
					if ( isArray )
						readBytesArray( b, cursor, numPx );
					else
						readBytes( b, cursor, width );
				}
				else if ( pixelType == FormatTools.UINT16 )
				{
					if ( isArray )
						readUnsignedShortsArray( b, cursor, numPx, isLittleEndian );
					else
						readUnsignedShorts( b, cursor, width, isLittleEndian );
				}
				else if ( pixelType == FormatTools.INT16 )
				{
					if ( isArray )
						readSignedShortsArray( b, cursor, numPx, isLittleEndian );
					else
						readSignedShorts( b, cursor, width, isLittleEndian );
				}
				else if ( pixelType == FormatTools.UINT32 )
				{
					//TODO: Untested
					if ( isArray )
						readUnsignedIntsArray( b, cursor, numPx, isLittleEndian );
					else
						readUnsignedInts( b, cursor, width, isLittleEndian );
				}
				else if ( pixelType == FormatTools.FLOAT )
				{
					if ( isArray )
						readFloatsArray( b, cursor, numPx, isLittleEndian );
					else
						readFloats( b, cursor, width, isLittleEndian );
				}
			}

			r.close();
		}
		catch ( Exception e )
		{
			IOFunctions.println( "File '" + cziFile.getAbsolutePath() + "' could not be opened: " + e );
			IOFunctions.println( "Stopping" );

			e.printStackTrace();
			try { r.close(); } catch (IOException e1) { e1.printStackTrace(); }
			return null;
		}

		return img;
	}

	protected static final < T extends RealType< T > > void readBytes( final byte[] b, final Cursor< T > cursor, final int width )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd(); // otherwise the position is off below
			cursor.get().setReal( b[ cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ] & 0xff );
		}
	}

	protected static final < T extends RealType< T > > void readBytesArray( final byte[] b, final Cursor< T > cursor, final int numPx )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( b[ i ] & 0xff );
	}

	protected static final < T extends RealType< T > > void readUnsignedShorts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( StackImgLoaderLOCI.getShortValueInt( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 2, isLittleEndian ) );
		}
	}

	protected static final < T extends RealType< T > > void readUnsignedShortsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( StackImgLoaderLOCI.getShortValueInt( b, i * 2, isLittleEndian ) );
	}

	protected static final < T extends RealType< T > > void readSignedShorts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( StackImgLoaderLOCI.getShortValue( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 2, isLittleEndian ) );
		}
	}

	protected static final < T extends RealType< T > > void readSignedShortsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( StackImgLoaderLOCI.getShortValue( b, i * 2, isLittleEndian ) );
	}

	protected static final < T extends RealType< T > > void readUnsignedInts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( StackImgLoaderLOCI.getIntValue( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 4, isLittleEndian ) );
		}
	}

	protected static final < T extends RealType< T > > void readUnsignedIntsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( StackImgLoaderLOCI.getIntValue( b, i * 4, isLittleEndian ) );
	}

	protected static final < T extends RealType< T > > void readFloats( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( StackImgLoaderLOCI.getFloatValue( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 4, isLittleEndian ) );
		}
	}

	protected static final < T extends RealType< T > > void readFloatsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( StackImgLoaderLOCI.getFloatValue( b, i * 4, isLittleEndian ) );
	}

	public static IFormatReader instantiateImageReader()
	{
		// should I use the ZeissCZIReader here directly?
		return new ChannelSeparator();// new ZeissCZIReader();
	}

	public static boolean createOMEXMLMetadata( final IFormatReader r )
	{
		try 
		{
			final ServiceFactory serviceFactory = new ServiceFactory();
			final OMEXMLService service = serviceFactory.getInstance( OMEXMLService.class );
			final IMetadata omexmlMeta = service.createOMEXMLMetadata();
			r.setMetadataStore(omexmlMeta);
		}
		catch (final ServiceException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (final DependencyException e)
		{
			e.printStackTrace();
			return false;
		}

		return true;
	}
}
