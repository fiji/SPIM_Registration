package spim.fiji.spimdata.imgloaders;

import java.io.File;

import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import spim.fiji.datasetmanager.StackList;


public abstract class StackImgLoader extends AbstractImgLoader
{
	protected File path = null;
	protected String fileNamePattern = null;
	
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	protected int layoutTP, layoutChannels, layoutIllum, layoutAngles; // 0 == one, 1 == one per file, 2 == all in one file
		
	protected SequenceDescription sequenceDescription;

	protected < T extends NativeType< T > > Img< T > instantiateImg( final long[] dim, final T type )
	{
		Img< T > img;
		
		try
		{
			img = getImgFactory().imgFactory( type ).create( dim, type );
		}
		catch ( Exception e1 )
		{
			try
			{
				img = new CellImgFactory< T >( 256 ).create( dim, type );				 
			}
			catch ( Exception e2 )
			{
				img = null;
			}
		}
		
		return img;
	}
	
	protected File getFile( final ViewId view )
	{
		final TimePoint tp = sequenceDescription.getTimePoints().getTimePoints().get( view.getTimePointId() );
		final ViewSetup vs = sequenceDescription.getViewSetups().get( view.getViewSetupId() );

		final String timepoint = tp.getName();
		final String angle = vs.getAngle().getName();
		final String channel = vs.getChannel().getName();
		final String illum = vs.getIllumination().getName();

		final String fileName = StackList.getFileNameFor( fileNamePattern, replaceTimepoints, replaceChannels,
				replaceIlluminations, replaceAngles, timepoint, channel, illum, angle,
				numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles );

		return new File( path, fileName );
	}

	/**
	 * For a local initialization without the XML
	 * 
	 * @param path
	 * @param fileNamePattern
	 * @param imgFactory
	 * @param layoutTP - 0 == one, 1 == one per file, 2 == all in one file
	 * @param layoutChannels - 0 == one, 1 == one per file, 2 == all in one file
	 * @param layoutIllum - 0 == one, 1 == one per file, 2 == all in one file
	 * @param layoutAngles - 0 == one, 1 == one per file, 2 == all in one file
	 */
	public StackImgLoader(
			final File path, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles,
			final SequenceDescription sequenceDescription )
	{
		super();
		this.path = path;
		this.fileNamePattern = fileNamePattern;
		this.layoutTP = layoutTP;
		this.layoutChannels = layoutChannels;
		this.layoutIllum = layoutIllum;
		this.layoutAngles = layoutAngles;
		this.sequenceDescription = sequenceDescription;
		
		this.init( imgFactory );
	}

	protected void init( final ImgFactory< ? extends NativeType< ? > > imgFactory )
	{
		setImgFactory( imgFactory );
		
		replaceTimepoints = replaceChannels = replaceIlluminations = replaceAngles = null;
		numDigitsTimepoints = numDigitsChannels = numDigitsIlluminations = numDigitsAngles = -1;
		
		replaceTimepoints = IntegerPattern.getReplaceString( fileNamePattern, StackList.TIMEPOINT_PATTERN );
		replaceChannels = IntegerPattern.getReplaceString( fileNamePattern, StackList.CHANNEL_PATTERN );
		replaceIlluminations = IntegerPattern.getReplaceString( fileNamePattern, StackList.ILLUMINATION_PATTERN );
		replaceAngles = IntegerPattern.getReplaceString( fileNamePattern, StackList.ANGLE_PATTERN );

		if ( replaceTimepoints != null )
			numDigitsTimepoints = replaceTimepoints.length() - 2;

		if ( replaceChannels != null )
			numDigitsChannels = replaceChannels.length() - 2;
		
		if ( replaceIlluminations != null )
			numDigitsIlluminations = replaceIlluminations.length() - 2;
		
		if ( replaceAngles != null )
			numDigitsAngles = replaceAngles.length() - 2;
		/*
		IOFunctions.println( replaceTimepoints );
		IOFunctions.println( replaceChannels );
		IOFunctions.println( replaceIlluminations );
		IOFunctions.println( replaceAngles );
		
		IOFunctions.println( layoutTP );
		IOFunctions.println( layoutChannels );
		IOFunctions.println( layoutIllum );
		IOFunctions.println( layoutAngles );
		
		IOFunctions.println( path );
		IOFunctions.println( fileNamePattern );
		*/		
	}
}
