package spim.fiji.spimdata.imgloaders;

import static mpicbg.spim.data.XmlHelpers.loadPath;

import java.io.File;

import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.XmlKeys;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import spim.fiji.datasetmanager.StackList;


public abstract class StackImgLoader extends AbstractImgLoader
{
	public static final String DIRECTORY_TAG = "imagedirectory";
	public static final String FILE_PATTERN_TAG = "filePattern";
	public static final String IMGLIB2CONTAINER_PATTERN_TAG = "imglib2container";

	public static final String LAYOUT_TP_TAG = "layoutTimepoints";
	public static final String LAYOUT_CHANNEL_TAG = "layoutChannels";
	public static final String LAYOUT_ILLUMINATION_TAG = "layoutIlluminations";
	public static final String LAYOUT_ANGLE_TAG = "layoutAngles";

	protected File path = null;
	protected String fileNamePattern = null;
	
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	protected int layoutTP, layoutChannels, layoutIllum, layoutAngles; // 0 == one, 1 == one per file, 2 == all in one file
		
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

		final String timepoint = tp.getName();
		final String angle = vs.getAngle().getName();
		final String channel = vs.getChannel().getName();
		final String illum = vs.getIllumination().getName();
		
		String fileName = StackList.getFileNameFor( fileNamePattern, replaceTimepoints, replaceChannels, 
				replaceIlluminations, replaceAngles, timepoint, channel, illum, angle );
				
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
	public void init( final String path, final File basePath, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
					  final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles )
	{
		this.path = new File( basePath.getAbsolutePath(), path );
		this.fileNamePattern = fileNamePattern;
		this.layoutTP = layoutTP;
		this.layoutChannels = layoutChannels;
		this.layoutIllum = layoutIllum;
		this.layoutAngles = layoutAngles;
		
		this.init( imgFactory );
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

			this.layoutTP = Integer.parseInt( elem.getElementsByTagName( LAYOUT_TP_TAG ).item( 0 ).getTextContent() );
			this.layoutChannels = Integer.parseInt( elem.getElementsByTagName( LAYOUT_CHANNEL_TAG ).item( 0 ).getTextContent() );
			this.layoutIllum = Integer.parseInt( elem.getElementsByTagName( LAYOUT_ILLUMINATION_TAG ).item( 0 ).getTextContent() );
			this.layoutAngles = Integer.parseInt( elem.getElementsByTagName( LAYOUT_ANGLE_TAG ).item( 0 ).getTextContent() );

			final NodeList nd = elem.getElementsByTagName( IMGLIB2CONTAINER_PATTERN_TAG );
			ImgFactory< ? extends NativeType< ? > > imgFactory = null;
			
			if ( nd.getLength() == 0 )
			{
				System.out.println( "WARNING: No Img implementation defined, using ArrayImg." );
				
				// if no factory is defined we define an ArrayImgFactory
				imgFactory = new ArrayImgFactory< FloatType >();
			}
			else
			{
				final String container = nd.item( 0 ).getTextContent();
				
				if ( container.toLowerCase().contains( "cellimg" ) )
				{
					imgFactory = new CellImgFactory< FloatType >( 256 );
				}
				else if ( container.toLowerCase().contains( "arrayimg" ) )
				{
					imgFactory = new ArrayImgFactory< FloatType >();
				}
				else
				{
					// if factory is unknown we define an ArrayImgFactory
					imgFactory = new ArrayImgFactory< FloatType >();
					
					System.out.println( "WARNING: Unknown Img implementation '" + container + "', using ArrayImg." );
				}
			}
			
			this.init( imgFactory );
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}
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
		
		Element e = doc.createElement( FILE_PATTERN_TAG );
		e.appendChild( doc.createTextNode( fileNamePattern ) );
		elem.appendChild( e );

		e = doc.createElement( LAYOUT_TP_TAG );
		e.appendChild( doc.createTextNode( Integer.toString( layoutTP ) ) );
		elem.appendChild( e );

		e = doc.createElement( LAYOUT_CHANNEL_TAG );
		e.appendChild( doc.createTextNode( Integer.toString( layoutChannels ) ) );
		elem.appendChild( e );

		e = doc.createElement( LAYOUT_ILLUMINATION_TAG );
		e.appendChild( doc.createTextNode( Integer.toString( layoutIllum ) ) );
		elem.appendChild( e );

		e = doc.createElement( LAYOUT_ANGLE_TAG );
		e.appendChild( doc.createTextNode( Integer.toString( layoutAngles ) ) );
		elem.appendChild( e );

		e = doc.createElement( IMGLIB2CONTAINER_PATTERN_TAG );
		e.appendChild( doc.createTextNode( getImgFactory().getClass().getSimpleName() ) );
		elem.appendChild( e );

		return elem;
	}
}
