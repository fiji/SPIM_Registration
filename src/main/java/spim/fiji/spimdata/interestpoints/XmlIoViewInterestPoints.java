package spim.fiji.spimdata.interestpoints;

import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTSFILE_TAG;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_TAG;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME;

import java.io.File;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

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
	
	public ViewInterestPoints fromXml( final Element allViewBeads, final List< ViewDescription< TimePoint, ViewSetup > > viewDescriptionList ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final ViewInterestPoints viewsInterestPoints = ViewInterestPoints.createViewInterestPoints( viewDescriptionList );

		final NodeList nodes = allViewBeads.getElementsByTagName( VIEWINTERESTPOINTSFILE_TAG );

		for ( int i = 0; i < nodes.getLength(); ++i )
		{
			final Element viewInterestPointsElement = ( Element ) nodes.item( i );
					
			final int timepointId = Integer.parseInt( viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME ) );
			final int setupId = Integer.parseInt( viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME ) );
			final String label = viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME );
			
			final String interestPointFileName = viewInterestPointsElement.getTextContent();
			
			final ViewId viewId = new ViewId( timepointId, setupId );
			final ViewInterestPointLists collection = viewsInterestPoints.getViewInterestPointCollection( viewId );
			
			// we add a null entry for the List< Point >, we just load them once it is requested
			// do not try to save the null value
			collection.addInterestPoints( new InterestPointList( label, viewId, null, new File( interestPointFileName ), false ) );
		}

		return viewsInterestPoints;
	}

	public Element toXml( final Document doc, final ViewInterestPoints viewsInterestPoints ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		// TODO: export
		
		final Element elem = doc.createElement( VIEWINTERESTPOINTS_TAG );
		/*
		for ( final ViewInterestPointCollection vb : beadsList )
			elem.appendChild( viewInterestPointsToXml( doc, vb ) );
		*/
		return elem;
	}

	protected Node viewInterestPointsToXml( final Document doc, final ViewInterestPointLists viewInterestPointCollection )
	{
		// TODO: export
		final Element elem = doc.createElement( VIEWINTERESTPOINTSFILE_TAG );
		elem.setAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME, Integer.toString( viewInterestPointCollection.getTimePointId() ) );
		elem.setAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME, Integer.toString( viewInterestPointCollection.getViewSetupId() ) );
		//elem.setTextContent( viewInterestPointCollection.getBeadFile().toString() );
		
		return elem;
	}
}
