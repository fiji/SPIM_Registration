package fiji.spimdata.sequence;

import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUPS_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_ANGLE_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_CHANNEL_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_DEPTH_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_HEIGHT_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_ID_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_ILLUMINATION_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_PIXELDEPTH_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_PIXELHEIGHT_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_PIXELWIDTH_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_UNIT_TAG;
import static mpicbg.spim.data.sequence.XmlKeys.VIEWSETUP_WIDTH_TAG;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.sequence.XmlIoViewSetupsAbstract;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class XmlIoViewSetupsBeads extends XmlIoViewSetupsAbstract< ViewSetupBeads >
{
	public static final String VIEWSETUP_HASBEADS_TAG = "hasbeads"; 
	
	@Override
	public ArrayList< ViewSetupBeads > fromXml( final Element viewSetups )
	{
		final ArrayList< ViewSetupBeads > setups = new ArrayList< ViewSetupBeads >();
		final NodeList nodes = viewSetups.getElementsByTagName( VIEWSETUP_TAG );
		for ( int i = 0; i < nodes.getLength(); ++i )
		{
			final Element elem = ( Element ) nodes.item( i );
			final int id = XmlHelpers.getInt( elem, VIEWSETUP_ID_TAG );
			final int angle = XmlHelpers.getInt( elem, VIEWSETUP_ANGLE_TAG );
			final int illumination = XmlHelpers.getInt( elem, VIEWSETUP_ILLUMINATION_TAG );
			final int channel = XmlHelpers.getInt( elem, VIEWSETUP_CHANNEL_TAG );

			final int width = XmlHelpers.getInt( elem, VIEWSETUP_WIDTH_TAG, -1 );
			final int height = XmlHelpers.getInt( elem, VIEWSETUP_HEIGHT_TAG, -1 );
			final int depth = XmlHelpers.getInt( elem, VIEWSETUP_DEPTH_TAG, -1 );
			final String unit = XmlHelpers.getText( elem, VIEWSETUP_UNIT_TAG, "" );
			final double pixelWidth = XmlHelpers.getDouble( elem, VIEWSETUP_PIXELWIDTH_TAG, -1 );
			final double pixelHeight = XmlHelpers.getDouble( elem, VIEWSETUP_PIXELHEIGHT_TAG, -1 );
			final double pixelDepth = XmlHelpers.getDouble( elem, VIEWSETUP_PIXELDEPTH_TAG, -1 );
			
			// by default we assume there are beads (if not stated otherwise)
			final boolean hasBeads = XmlHelpers.getBoolean( elem, VIEWSETUP_HASBEADS_TAG );
			System.out.println( hasBeads );

			setups.add( new ViewSetupBeads( id, angle, illumination, channel, width, height, depth, unit, pixelWidth, pixelHeight, pixelDepth, hasBeads ) );
		}
		Collections.sort( setups ); // sorts by id
		return setups;
	}

	@Override
	public Element toXml( final Document doc, final List< ViewSetupBeads > viewSetups )
	{
		final Element setups = doc.createElement( VIEWSETUPS_TAG );
		for ( final ViewSetupBeads s : viewSetups )
		{
			final Element setup = doc.createElement( VIEWSETUP_TAG );
			setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_ID_TAG, s.getId() ) );
			setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_ANGLE_TAG, s.getAngle() ) );
			setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_ILLUMINATION_TAG, s.getIllumination() ) );
			setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_CHANNEL_TAG, s.getChannel() ) );

			if ( s.getWidth() != -1 )
				setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_WIDTH_TAG, s.getWidth() ) );
			if ( s.getHeight() != -1 )
				setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_HEIGHT_TAG, s.getHeight() ) );
			if ( s.getDepth() != -1 )
				setup.appendChild( XmlHelpers.intElement( doc, VIEWSETUP_DEPTH_TAG, s.getDepth() ) );
			if ( s.getPixelSizeUnit() != null && !s.getPixelSizeUnit().isEmpty() )
				setup.appendChild( XmlHelpers.textElement( doc, VIEWSETUP_UNIT_TAG, s.getPixelSizeUnit() ) );
			if ( s.getPixelWidth() != -1 )
				setup.appendChild( XmlHelpers.doubleElement( doc, VIEWSETUP_PIXELWIDTH_TAG, s.getPixelWidth() ) );
			if ( s.getPixelHeight() != -1 )
				setup.appendChild( XmlHelpers.doubleElement( doc, VIEWSETUP_PIXELHEIGHT_TAG, s.getPixelHeight() ) );
			if ( s.getPixelDepth() != -1 )
				setup.appendChild( XmlHelpers.doubleElement( doc, VIEWSETUP_PIXELDEPTH_TAG, s.getPixelDepth() ) );

			setup.appendChild( XmlHelpers.booleanElement( doc, VIEWSETUP_HASBEADS_TAG, s.hasBeads() ) );

			setups.appendChild( setup );
		}
		return setups;
	}
}
