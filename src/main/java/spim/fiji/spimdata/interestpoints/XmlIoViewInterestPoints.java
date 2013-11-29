package spim.fiji.spimdata.interestpoints;

import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTSFILE_TAG;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_TAG;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME;

import java.io.File;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlIoViewInterestPoints
{
	public String getTagName()
	{
		return VIEWINTERESTPOINTS_TAG;
	}
	
	public ArrayList< ViewInterestPoints > fromXml( final Element allViewBeads ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final ArrayList< ViewInterestPoints > beadsList = new ArrayList< ViewInterestPoints >();
		final NodeList nodes = allViewBeads.getElementsByTagName( VIEWINTERESTPOINTSFILE_TAG );

		for ( int i = 0; i < nodes.getLength(); ++i )
		{
			final Element viewBeads = ( Element ) nodes.item( i );
					
			final int timepointId = Integer.parseInt( viewBeads.getAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME ) );
			final int setupId = Integer.parseInt( viewBeads.getAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME ) );

			final String beadFile = viewBeads.getTextContent();
			
			beadsList.add( new ViewInterestPoints( timepointId, setupId, new File( beadFile ) ) );
		}

		return beadsList;
	}

	public Element toXml( final Document doc, final ArrayList< ViewInterestPoints > beadsList ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final Element elem = doc.createElement( VIEWINTERESTPOINTS_TAG );
		
		for ( final ViewInterestPoints vb : beadsList )
			elem.appendChild( viewBeadsToXml( doc, vb ) );
		
		return elem;
	}

	protected Node viewBeadsToXml( final Document doc, final ViewInterestPoints viewBeads )
	{
		final Element elem = doc.createElement( VIEWINTERESTPOINTSFILE_TAG );
		elem.setAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME, Integer.toString( viewBeads.getTimePointId() ) );
		elem.setAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME, Integer.toString( viewBeads.getViewSetupId() ) );
		elem.setTextContent( viewBeads.getBeadFile().toString() );
		
		return elem;
	}
}
