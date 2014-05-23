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

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

import org.jdom2.Element;

public class XmlIoViewInterestPoints
{
	public String getTagName()
	{
		return VIEWINTERESTPOINTS_TAG;
	}

	public ViewInterestPoints fromXml( final Element allInterestPointLists, final File basePath, final HashMap< ViewId, ViewDescription< TimePoint, ViewSetup > > viewDescriptionList ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final ViewInterestPoints viewsInterestPoints = ViewInterestPoints.createViewInterestPoints( viewDescriptionList );

		for ( final Element viewInterestPointsElement : allInterestPointLists.getChildren( VIEWINTERESTPOINTSFILE_TAG ) )
		{
			final int timepointId = Integer.parseInt( viewInterestPointsElement.getAttributeValue( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME ) );
			final int setupId = Integer.parseInt( viewInterestPointsElement.getAttributeValue( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME ) );
			final String label = viewInterestPointsElement.getAttributeValue( VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME );
			final String parameters = viewInterestPointsElement.getAttributeValue( VIEWINTERESTPOINTS_PARAMETERS_ATTRIBUTE_NAME );

			final String interestPointFileName = viewInterestPointsElement.getTextTrim();

			final ViewId viewId = new ViewId( timepointId, setupId );
			final ViewInterestPointLists collection = viewsInterestPoints.getViewInterestPointLists( viewId );

			// we do not load the interestpoints nor the correspondinginterestpoints, we just do that once it is requested
			final InterestPointList list = new InterestPointList( basePath, new File( interestPointFileName ) );
			list.setParameters( parameters );
			collection.addInterestPointList( label, list );
		}

		return viewsInterestPoints;
	}

	public Element toXml( final ViewInterestPoints viewsInterestPoints )
	{
		final Element elem = new Element( VIEWINTERESTPOINTS_TAG );

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
				final InterestPointList list = v.getInterestPointList( label );
				elem.addContent( viewInterestPointsToXml( list, v.getTimePointId(), v.getViewSetupId(), label ) );
			}
		}
		return elem;
	}

	protected Element viewInterestPointsToXml( final InterestPointList interestPointList, final int tpId, final int viewId, final String label )
	{
		final Element elem = new Element( VIEWINTERESTPOINTSFILE_TAG );
		elem.setAttribute( VIEWINTERESTPOINTS_TIMEPOINT_ATTRIBUTE_NAME, Integer.toString( tpId ) );
		elem.setAttribute( VIEWINTERESTPOINTS_SETUP_ATTRIBUTE_NAME, Integer.toString( viewId ) );
		elem.setAttribute( VIEWINTERESTPOINTS_LABEL_ATTRIBUTE_NAME, label );
		elem.setAttribute( VIEWINTERESTPOINTS_PARAMETERS_ATTRIBUTE_NAME, interestPointList.getParameters() );
		// a hack so that windows does not put its backslashes in
		elem.setText( interestPointList.getFile().toString().replace( "\\", "/" ) );

		return elem;
	}
}
