package fiji.spimdata;

import static mpicbg.spim.data.XmlHelpers.loadPath;

import java.io.File;

import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.XmlKeys;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import fiji.datasetmanager.StackList;

public abstract class StackImgLoader extends AbstractImgLoader
{
	public static final String DIRECTORY_TAG = "imagedirectory";
	public static final String FILE_PATTERN_TAG = "filePattern";
	
	protected File path = null;
	protected String fileNamePattern = null;
	
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	
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
	
	protected File getFile( final ViewDescription<?, ?> view )
	{
		final TimePoint tp = view.getTimePoint();
		final ViewSetup vs = view.getViewSetup();

		// only integers are allowed as timepoint names
		final int timepoint = Integer.parseInt( tp.getName() );
		final int angle = vs.getAngle();
		final int channel = vs.getChannel();
		final int illum = vs.getIllumination();
		
		String fileName = StackList.getFileNameFor( fileNamePattern, replaceTimepoints, replaceChannels, 
				replaceIlluminations, replaceAngles, timepoint, channel, illum, angle );
		
		return new File( path, fileName );
	}

	/**
	 * For a local initialization without the XML
	 * 
	 * @param path
	 * @param fileNamePattern
	 */
	public void init( final String path, final File basePath, final String fileNamePattern )
	{
		this.path = new File( basePath.getAbsolutePath(), path );
		this.fileNamePattern = fileNamePattern;
		
		this.init();
	}

	/**
	 * initialize the loader from a &lt;{@value XmlKeys#IMGLOADER_TAG}&gt; DOM element.
	 */
	@Override
	public void init( final Element elem, final File basePath )
	{
		try
		{
			this.path = loadPath( elem, DIRECTORY_TAG, basePath );
			this.fileNamePattern = elem.getElementsByTagName( FILE_PATTERN_TAG ).item( 0 ).getTextContent();
			
			this.init();
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}
	}
	
	protected void init()
	{
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
		
		System.out.println( replaceTimepoints );
		System.out.println( replaceChannels );
		System.out.println( replaceIlluminations );
		System.out.println( replaceAngles );
		
		System.out.println( path );
		System.out.println( fileNamePattern );		
	}

	/**
	 * create a &lt;{@value XmlKeys#IMGLOADER_TAG}&gt; DOM element for this loader.
	 */
	@Override
	public Element toXml( final Document doc, final File basePath )
	{		
		final Element elem = doc.createElement( "ImageLoader" );
		elem.setAttribute( "class", getClass().getCanonicalName() );
		elem.appendChild( XmlHelpers.pathElement( doc, DIRECTORY_TAG, path, basePath ) );
		
		final Element e = doc.createElement( FILE_PATTERN_TAG );
		e.appendChild( doc.createTextNode( fileNamePattern ) );
		
		elem.appendChild( e );
		
		return elem;
	}
}
