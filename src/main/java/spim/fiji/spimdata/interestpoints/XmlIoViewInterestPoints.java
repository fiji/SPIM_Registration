package spim.fiji.spimdata.interestpoints;

import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTSFILE_TAG;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_PARAMETERS_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_TAG;
import static spim.fiji.spimdata.XmlKeysInterestPoints.VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	
	public ViewInterestPoints fromXml( final Element allInterestPointLists, final File basePath, final HashMap< ViewId, ViewDescription< TimePoint, ViewSetup > > viewDescriptionList ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final ViewInterestPoints viewsInterestPoints = ViewInterestPoints.createViewInterestPoints( viewDescriptionList );

		final NodeList nodes = allInterestPointLists.getElementsByTagName( VIEWINTERESTPOINTSFILE_TAG );

		for ( int i = 0; i < nodes.getLength(); ++i )
		{
			final Element viewInterestPointsElement = ( Element ) nodes.item( i );
					
			final int timepointId = Integer.parseInt( viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME ) );
			final int setupId = Integer.parseInt( viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME ) );
			final String label = viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME );
			final String parameters = viewInterestPointsElement.getAttribute( VIEWINTERESTPOINTS_PARAMETERS_ATTRIBUTE_NAME );
			
			final String interestPointFileName = viewInterestPointsElement.getTextContent();
			
			final ViewId viewId = new ViewId( timepointId, setupId );
			final ViewInterestPointLists collection = viewsInterestPoints.getViewInterestPointLists( viewId );
			
			// we do not add an entry for the List< Point >, we just load them once it is requested
			collection.addInterestPoints( label, new InterestPointList( basePath, new File( interestPointFileName ), parameters ) );
		}

		return viewsInterestPoints;
	}

	public Element toXml( final Document doc, final ViewInterestPoints viewsInterestPoints ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final Element elem = doc.createElement( VIEWINTERESTPOINTS_TAG );
		
		// sort all entries by timepoint and viewsetupid so that it is possible to edit XML by hand
		final ArrayList< ViewInterestPointLists > viewIPlist = new ArrayList< ViewInterestPointLists >();
		viewIPlist.addAll( viewsInterestPoints.getViewInterestPoints().values() );
		Collections.sort( viewIPlist );

		for ( final ViewInterestPointLists v : viewIPlist )
		{
			// sort all entries by label so that it is possible to edit XML by hand
			final ArrayList< String > labelList = new ArrayList< String >();
			labelList.addAll( v.getHashMap().keySet() );
			Collections.sort( labelList );

			for ( final String label : labelList )
			{
				final InterestPointList list = v.getInterestPoints( label );
				elem.appendChild( viewInterestPointsToXml( doc, list, v.getTimePointId(), v.getViewSetupId(), label ) );
			}
		}
		return elem;
	}

	protected Node viewInterestPointsToXml( final Document doc, final InterestPointList interestPointList, final int tpId, final int viewId, final String label )
	{
		final Element elem = doc.createElement( VIEWINTERESTPOINTSFILE_TAG );
		elem.setAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME, Integer.toString( tpId ) );
		elem.setAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME, Integer.toString( viewId ) );
		elem.setAttribute( VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME, label );
		elem.setAttribute( VIEWINTERESTPOINTS_PARAMETERS_ATTRIBUTE_NAME, interestPointList.getParameters() );
		elem.setTextContent( interestPointList.getFile().toString() );
		
		return elem;
	}
}
