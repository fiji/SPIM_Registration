package fiji.spimdata.beads;

import static fiji.spimdata.XmlKeysBeads.VIEWBEADFILE_TAG;
import static fiji.spimdata.XmlKeysBeads.VIEWBEADS_SETUP_ATTRIBUTE_NAME;
import static fiji.spimdata.XmlKeysBeads.VIEWBEADS_TAG;
import static fiji.spimdata.XmlKeysBeads.VIEWBEADS_TIMEPOINT_ATTRIBUTE_NAME;
import static mpicbg.spim.data.registration.XmlKeys.VIEWREGISTRATION_SETUP_ATTRIBUTE_NAME;
import static mpicbg.spim.data.registration.XmlKeys.VIEWREGISTRATION_TAG;
import static mpicbg.spim.data.registration.XmlKeys.VIEWREGISTRATION_TIMEPOINT_ATTRIBUTE_NAME;
import static mpicbg.spim.data.registration.XmlKeys.VIEWTRANSFORM_NAME_TAG;

import java.io.File;
import java.util.ArrayList;

import mpicbg.spim.data.XmlHelpers;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlIoViewBeads
{
	public String getTagName()
	{
		return VIEWBEADS_TAG;
	}
	
	public ArrayList< ViewBeads > fromXml( final Element allViewBeads ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final ArrayList< ViewBeads > beadsList = new ArrayList< ViewBeads >();
		final NodeList nodes = allViewBeads.getElementsByTagName( VIEWBEADFILE_TAG );

		for ( int i = 0; i < nodes.getLength(); ++i )
		{
			final Element viewBeads = ( Element ) nodes.item( i );
					
			final int timepointId = Integer.parseInt( viewBeads.getAttribute( VIEWBEADS_TIMEPOINT_ATTRIBUTE_NAME ) );
			final int setupId = Integer.parseInt( viewBeads.getAttribute( VIEWBEADS_SETUP_ATTRIBUTE_NAME ) );

			final String beadFile = viewBeads.getTextContent();
			
			beadsList.add( new ViewBeads( timepointId, setupId, new File( beadFile ) ) );
		}

		return beadsList;
	}

	public Element toXml( final Document doc, final ArrayList< ViewBeads > beadsList ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final Element elem = doc.createElement( VIEWBEADS_TAG );
		
		for ( final ViewBeads vb : beadsList )
			elem.appendChild( viewBeadsToXml( doc, vb ) );
		
		return elem;
	}

	protected Node viewBeadsToXml( final Document doc, final ViewBeads viewBeads )
	{
		final Element elem = doc.createElement( VIEWBEADFILE_TAG );
		elem.setAttribute( VIEWBEADS_TIMEPOINT_ATTRIBUTE_NAME, Integer.toString( viewBeads.getTimePointId() ) );
		elem.setAttribute( VIEWBEADS_SETUP_ATTRIBUTE_NAME, Integer.toString( viewBeads.getViewSetupId() ) );
		elem.setTextContent( viewBeads.getBeadFile().toString() );
		
		return elem;
	}
}
