package spim.fiji.spimdata.imgloaders;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import loci.formats.FormatTools;
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
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.datasetmanager.SlideBook6;

import org.scijava.nativelib.NativeLibraryUtil;

public class SlideBook6ImgLoader extends AbstractImgLoader
{
	final File mmFile;
	final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription;

	// -- Static initializers --

	private static boolean libraryFound = false;
	
	public SlideBook6ImgLoader(
			final File mmFile,
			final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final AbstractSequenceDescription< ? extends BasicViewSetup, ? extends BasicViewDescription< ? >, ? > sequenceDescription )
	{
		super();
		this.mmFile = mmFile;
		this.sequenceDescription = sequenceDescription;

		try {
			// load JNI wrapper of SBReadFile.dll
			if (!libraryFound) {
				libraryFound = NativeLibraryUtil.loadNativeLibrary(SlideBook6ImgLoader.class, "SlideBook6Reader");
			}
		}
		catch (UnsatisfiedLinkError e) {
			// log level debug, otherwise a warning will be printed every time a file is initialized without the .dll present
			LOGGER.debug(NO_3I_MSG, e);
			libraryFound = false;
		}
		catch (SecurityException e) {
			LOGGER.warn("Insufficient permission to load native library", e);
			libraryFound = false;
		}
		
		setImgFactory( imgFactory );
	}

	public File getFile() { return mmFile; }

	final public < T extends RealType< T > & NativeType< T > > void populateImage( final ArrayImg< T, ? > img, final BasicViewDescription< ? > vd)
	{
		final ArrayCursor< T > cursor = img.cursor();
		
		final int t = vd.getTimePoint().getId();
		final int a = vd.getViewSetup().getAttribute( Angle.class ).getId();
		final int c = vd.getViewSetup().getAttribute( Channel.class ).getId();
		final int i = vd.getViewSetup().getAttribute( Illumination.class ).getId();

		int countDroppedFrames = 0;
		ArrayList< Integer > slices = null;
		
		byte[] buffer = new byte[FormatTools.getPlaneSize(this)];

		for ( int z = 0; z < r.depth(); ++z )
		{

			// SlideBook6Reader.dll
			// a = angle id (SPIMdata) = capture index (SlideBook)
			readImagePlaneBuf(buffer, a, 0, t, z, c);
			
			
			for ( final byte b :buffer )
				cursor.next().setReal( UnsignedByteType.getUnsignedByte( b ) );
		}
	}

	@Override
	public RandomAccessibleInterval< FloatType > getFloatImage( final ViewId view, final boolean normalize )
	{
		try
		{
			// SlideBook6Reader.dll
			openFile(mmFile);

			final ArrayImg< FloatType, ? > img = ArrayImgs.floats( r.width(), r.height(), r.depth() );
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );

			populateImage( img, vd );

			if ( normalize )
				normalize( img );

			// ASSERT(view < getNumCaptures());
			float voxelSize = getVoxelSize(view);
			float zSpacing = 1;
			if (getNumZPlanes(view) > 1) {
				zSpacing = getZPositin(view, 1) - getZPosition(view, 0);
			}
			updateMetaDataCache( view, getNumXColumns(view), getNumYRows(view), getNumZPlanes(view), 
					theVoxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			closeFile();

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
			openFile();
			
			final ArrayImg< UnsignedShortType, ? > img = ArrayImgs.unsignedShorts( r.width(), r.height(), r.depth() );
			final BasicViewDescription< ? > vd = sequenceDescription.getViewDescriptions().get( view );

			populateImage( img, vd, r );
			
			float voxelSize = getVoxelSize(view);
			float zSpacing = 1;
			if (getNumZPlanes(view) > 1) {
				zSpacing = getZPositin(view, 1) - getZPosition(view, 0);
			}
			updateMetaDataCache( view, getNumXColumns(view), getNumYRows(view), getNumZPlanes(view), 
					theVoxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			closeFile();

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
		try
		{
			// SlideBook6Reader.dll
			openFile();

			// ASSERT(view < getNumCaptures());
			float voxelSize = getVoxelSize(view.getViewSetupId());
			double zSpacing = 1;
			if (getNumZPlanes(view.getViewSetupId()) > 1) {
				zSpacing = getZPosition(view.getViewSetupId(),0, 1) - getZPosition(view.getViewSetupId(),0, 0);
			}
			updateMetaDataCache( view, getNumXColumns(view), getNumYRows(view), getNumZPlanes(view), 
					theVoxelSize, voxelSize, zSpacing );

			// SlideBook6Reader.dll
			closeFile();
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
	
	// -- Native methods --
	public native boolean openFile(String path);
	public native void closeFile();
	public native int getNumCaptures();
	public native int getNumPositions(int inCapture);
	public native int getNumTimepoints(int inCapture);
	public native int getNumChannels(int inCapture);
	public native int getNumXColumns(int inCapture);
	public native int getNumYRows(int inCapture);
	public native int getNumZPlanes(int inCapture);
	public native int getElapsedTime(int inCapture, int inTimepoint);

	public native int getExposureTime(int inCapture, int inChannel);
	public native float getVoxelSize(int inCapture);

	public native double getXPosition(int inCapture, int inPosition);
	public native double getYPosition(int inCapture, int inPosition);
	public native double getZPosition(int inCapture, int inPosition, int inZPlane);

	public native int getMontageRow(int inCapture, int inPosition);
	public native int getMontageColumn(int inCapture, int inPosition);

	public native String getChannelName(int inCapture, int inChannel);
	public native String getLensName(int inCapture);
	public native double getMagnification(int inCapture);
	public native String getImageName(int inCapture);
	public native String getImageComments(int inCapture);

	public native int getBytesPerPixel(int inCapture);

	public native boolean readImagePlaneBuf( byte outPlaneBuffer[],
			int inCapture,
			int inPosition,
			int inTimepoint,
			int inZ,
			int inChannel );	
}
