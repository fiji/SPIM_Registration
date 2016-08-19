package spim.fiji.spimdata.imgloaders;

import ij.IJ;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.ChannelSeparator;

//import java.nio.ByteBuffer;
//import java.nio.ShortBuffer;
//import java.nio.ByteOrder;

import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.in.SlideBook6Reader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.*;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
//import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
//import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.fiji.datasetmanager.LightSheetZ1MetaData;
import spim.fiji.datasetmanager.SlideBook6;

import org.scijava.nativelib.NativeLibraryUtil;

public class LegacySlideBook6ImgLoader extends AbstractImgFactoryImgLoader
{
	final File sldFile;
	final AbstractSequenceDescription<?, ?, ?> sequenceDescription;
	
	// TODO: once the metadata is loaded for one view, it is available for all other ones

	// -- Static initializers --

	private static boolean libraryFound = false;
	
	public LegacySlideBook6ImgLoader(
			final File sldFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super();
		this.sldFile = sldFile;
		this.sequenceDescription = sequenceDescription;

		try {
			// load JNI wrapper of SBReadFile.dll
			if (!libraryFound) {
				libraryFound = NativeLibraryUtil.loadNativeLibrary(LegacySlideBook6ImgLoader.class, "SlideBook6Reader");
			}
		}
		catch (UnsatisfiedLinkError e) {
			// log level debug, otherwise a warning will be printed every time a file is initialized without the .dll present
			IOFunctions.println("3i SlideBook SlideBook6Reader native library not found.");
			libraryFound = false;
		}
		catch (SecurityException e) {
			IOFunctions.println("Insufficient permission to load native library");
			libraryFound = false;
		}
		
		setImgFactory( imgFactory );
	}

	public File getSLDFile() { return sldFile; }

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		try
		{
			int[] dim = new int[ 3 ];
			float[] voxelSize = new float[ 3 ];
			final Img< FloatType > img = openSLD(new FloatType(), view, dim, voxelSize);

			if ( img == null )
				throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			if ( normalize )
				normalize( img );

			// update the MetaDataCache of the AbstractImgLoader
			// this does not update the XML ViewSetup but has to be called explicitly before saving
			updateMetaDataCache(
					view, dim[ 0 ], dim[ 1 ], dim[ 2 ],
					voxelSize[ 0 ], voxelSize[ 1 ], voxelSize[ 2 ] );

			return img;
		}
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		try
		{
			int[] dim = new int[ 3 ];
			float[] voxelSize = new float[ 3 ];
			final Img< UnsignedShortType > img = openSLD(new UnsignedShortType(), view, dim, voxelSize);

			if ( img == null )
				throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() );

			// update the MetaDataCache of the AbstractImgLoader
			// this does not update the XML ViewSetup but has to be called explicitly before saving
			updateMetaDataCache(
					view, dim[ 0 ], dim[ 1 ], dim[ 2 ],
					voxelSize[ 0 ], voxelSize[ 1 ], voxelSize[ 2 ] );

			return img;
		}
		catch ( Exception e )
		{
			throw new RuntimeException( "Could not load '" + sldFile + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ": " + e );
		}
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		// SlideBook6Reader.dll
		SlideBook6Reader reader = new SlideBook6Reader();

		reader.openFile(sldFile.getPath());
		int position = 0;

		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();
		final int c = i / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture
		final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
		final int w = reader.getNumXColumns(c);
		final int h = reader.getNumYRows(c);
		final int d = reader.getNumZPlanes(c);

		final float voxelSize = reader.getVoxelSize(c);
		final float zSpacing = SlideBook6.getZSpacing(reader, c, position);

		updateMetaDataCache( view, w, h, d, 
					voxelSize, voxelSize, zSpacing );

		// SlideBook6Reader.dll
		reader.closeFile();
	}

	@Override
	public void finalize()
	{
		// TODO: do not close file between reads
		IOFunctions.println( "Closing SLD: " + sldFile );
	}

	protected < T extends RealType< T > & NativeType< T > > Img< T > openSLD( final T type, final ViewId view, int[] dim, float[] voxelSize ) throws Exception
	{
		IOFunctions.println( "Investigating file '" + sldFile.getAbsolutePath() + "'." );

		final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
		final BasicViewSetup vs = vd.getViewSetup();

		final TimePoint t = vd.getTimePoint();
		final Angle a = getAngle( vd );
		final Channel ch = getChannel( vd );
		final Illumination i = getIllumination( vd );
		final int c = i.getId() / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture

		// SlideBook6Reader.dll
		SlideBook6Reader reader = new SlideBook6Reader();
		reader.openFile(sldFile.getPath());
		final int width = reader.getNumXColumns(c);
		final int height = reader.getNumYRows(c);
		final int depth = reader.getNumZPlanes(c);
		final int channels = reader.getNumChannels(c);
		final int position = 0;
		voxelSize[ 0 ] = reader.getVoxelSize(c);
		voxelSize[ 1 ] = reader.getVoxelSize(c);
		voxelSize[ 2 ] = SlideBook6.getZSpacing(reader, c, position);

		final int numPx = width * height;

		dim[ 0 ] = width;
		dim[ 1 ] = height;
		dim[ 2 ] = depth;

		final Img< T > img = imgFactory.imgFactory( type ).create( dim, type );
		final int pixelType = FormatTools.UINT16;

		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + sldFile + "' captureId=" + c + "' viewId=" + view.getViewSetupId() + ", tpId=" + view.getTimePointId() + ", most likely out of memory." );

		IOFunctions.println(
				new Date( System.currentTimeMillis() ) + ": Opening '" + sldFile.getName() + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] +
						" angle=" + a.getName() + " ch=" + ch.getName() + " illum=" + i.getName() + " tp=" + t.getName() + " type=" + FormatTools.getPixelTypeString(pixelType) +
						" img=" + img.getClass().getSimpleName() + "<" + type.getClass().getSimpleName() + ">]" );

		final boolean isLittleEndian = true;
		final boolean isArray = ArrayImg.class.isInstance(img);

		final byte[] b = new byte[ numPx * FormatTools.getBytesPerPixel(pixelType) ];

		try
		{
			for ( int z = 0; z < depth; ++z )
			{
				IJ.showProgress( (double)z / (double)depth );

				final Cursor< T > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();

				// SlideBook6Reader.dll
				// i = illumination id (SPIMdata) = capture index * 8 (SlideBook)
				// a = angle id (SPIMdata) = channel index  (SlideBook)
				reader.readImagePlaneBuf(b, c, 0, t.getId(), z, (ch.getId()*channels) + a.getId());

				///r.openBytes( r.getIndex( z, ch, t.getId() ), b );

				if ( isArray )
					readUnsignedShortsArray( b, cursor, numPx, isLittleEndian );
				else
					readUnsignedShorts( b, cursor, width, isLittleEndian );
			}

			IJ.showProgress( 1 );
		}
		catch ( Exception e )
		{
			IOFunctions.println("File '" + sldFile.getAbsolutePath() + "' could not be opened: " + e);
			IOFunctions.println( "Stopping" );

			e.printStackTrace();
			reader.closeFile();
			return null;
		}
		reader.closeFile();

		return img;
	}

	protected static final < T extends RealType< T > > void readUnsignedShorts( final byte[] b, final Cursor< T > cursor, final int width, final boolean isLittleEndian )
	{
		while( cursor.hasNext() )
		{
			cursor.fwd();
			cursor.get().setReal( LegacyStackImgLoaderLOCI.getShortValueInt( b, ( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) * 2, isLittleEndian ) );
		}
	}

	protected static final < T extends RealType< T > > void readUnsignedShortsArray( final byte[] b, final Cursor< T > cursor, final int numPx, final boolean isLittleEndian )
	{
		for ( int i = 0; i < numPx; ++i )
			cursor.next().setReal( LegacyStackImgLoaderLOCI.getShortValueInt( b, i * 2, isLittleEndian ) );
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

	@Override
	public String toString()
	{
		return new SlideBook6().getTitle() + ", ImgFactory=" + imgFactory.getClass().getSimpleName();
	}

}

