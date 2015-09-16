package spim.fiji.spimdata.imgloaders;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.ByteOrder;

import loci.formats.in.SlideBook6Reader;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayCursor;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.datasetmanager.SlideBook6;

import org.scijava.nativelib.NativeLibraryUtil;

public class SlideBook6ImgLoader extends AbstractImgFactoryImgLoader
{
	final File sldFile;
	final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription;
	
	// -- Static initializers --

	private static boolean libraryFound = false;
	
	public SlideBook6ImgLoader(
			final File sldFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription )
	{
		super();
		this.sldFile = sldFile;
		this.sequenceDescription = sequenceDescription;

		try {
			// load JNI wrapper of SBReadFile.dll
			if (!libraryFound) {
				libraryFound = NativeLibraryUtil.loadNativeLibrary(SlideBook6ImgLoader.class, "SlideBook6Reader");
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

	public File getFile() { return sldFile; }

	final public < T extends RealType< T > & NativeType< T > > void populateImage( final ArrayImg< T, ? > img, final BasicViewDescription< ? > vd, final SlideBook6Reader r)
	{
		final ArrayCursor< T > cursor = img.cursor();
		
		final int t = vd.getTimePoint().getId();
		final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
		final int ch = vd.getViewSetup().getAttribute( Channel.class ).getId();
		final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();
		final int c = i / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture

		final int bpp = r.getBytesPerPixel(c);

		byte[] data = new byte[(int) (bpp * img.dimension(0) * img.dimension(1))];
		
		ByteBuffer byteBuffer = ByteBuffer.wrap(data);

		for ( int z = 0; z < img.dimension(2); ++z )
		{
			// SlideBook6Reader.dll
			// i = illumination id (SPIMdata) = capture index * 8 (SlideBook)
			// a = angle id (SPIMdata) = channel index  (SlideBook)
			r.readImagePlaneBuf(data, c, 0, t, z, (ch*2) + a);

			ShortBuffer shortBuffer = byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
			while ( shortBuffer.hasRemaining())
				cursor.next().setReal( shortBuffer.get() );
		}
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		try
		{
			// SlideBook6Reader.dll
			SlideBook6Reader reader = new SlideBook6Reader();

			reader.openFile(sldFile.getPath());
			int position = 0;
		
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
			final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();
			final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
			final int c = i / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture
			final int w = reader.getNumXColumns(c);
			final int h = reader.getNumYRows(c);
			final int d = reader.getNumZPlanes(c);
			final ArrayImg< FloatType, ? > img = ArrayImgs.floats( w, h, d );
			
			populateImage( img, vd, reader );

			if ( normalize )
				normalize( img );
			
			final float voxelSize = reader.getVoxelSize(i);
			final float zSpacing = SlideBook6.getZSpacing(reader, c, position);
			
			updateMetaDataCache( view, w, h, d, voxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			reader.closeFile();

			return img;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		try
		{
			// SlideBook6Reader.dll
			SlideBook6Reader reader = new SlideBook6Reader();

			reader.openFile(sldFile.getPath());
			int position = 0;
			
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );
			final int i = vd.getViewSetup().getAttribute( Illumination.class).getId();
			final int c = i / 8; // map from illumination id to SlideBook capture index, up to 8 channels per SlideBook capture
			final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
			final int w = reader.getNumXColumns(c);
			final int h = reader.getNumYRows(c);
			final int d = reader.getNumZPlanes(c);
			final ArrayImg< UnsignedShortType, ? > img = ArrayImgs.unsignedShorts( w, h, d );

			populateImage( img, vd, reader );
					
			final float voxelSize = reader.getVoxelSize(c);
			final float zSpacing = SlideBook6.getZSpacing(reader, c, position);
			
			updateMetaDataCache( view, w, h, d, voxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			reader.closeFile();

			return img;
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
			return null;
		}
	}

	@Override
	protected void loadMetaData( final ViewId view )
	{
		// SlideBook6Reader.dll
		SlideBook6Reader reader = new SlideBook6Reader();

		try
		{
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
		catch ( Exception e )
		{
			IOFunctions.println( "Failed to load metadata for viewsetup=" + view.getViewSetupId() + " timepoint=" + view.getTimePointId() + ": " + e );
			e.printStackTrace();
		}
	}

	@Override
	public String toString()
	{
		return new SlideBook6().getTitle() + ", ImgFactory=" + imgFactory.getClass().getSimpleName();
	}
}
