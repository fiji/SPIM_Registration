package spim.fiji.spimdata;

import java.io.File;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;

import org.jdom2.Element;

import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.fiji.spimdata.interestpoints.XmlIoViewInterestPoints;

public class XmlIoSpimData2 extends XmlIoAbstractSpimData< SequenceDescription, SpimData2 >
{
	final XmlIoViewInterestPoints xmlViewsInterestPoints;

	public XmlIoSpimData2()
	{
		super( SpimData2.class, new XmlIoSequenceDescription(), new XmlIoViewRegistrations() );
		xmlViewsInterestPoints = new XmlIoViewInterestPoints();
		handledTags.add( xmlViewsInterestPoints.getTag() );
	}

	@Override
	public SpimData2 fromXml( final Element root, final File xmlFile ) throws SpimDataException
	{
		final SpimData2 spimData = super.fromXml( root, xmlFile );
		final SequenceDescription seq = spimData.getSequenceDescription();

		final ViewInterestPoints viewsInterestPoints;
		final Element elem = root.getChild( xmlViewsInterestPoints.getTag() );
		if ( elem == null )
		{
			viewsInterestPoints = new ViewInterestPoints();
			viewsInterestPoints.createViewInterestPoints( seq.getViewDescriptions() );
		}
		else
			viewsInterestPoints = xmlViewsInterestPoints.fromXml( elem, spimData.getBasePath(), seq.getViewDescriptions() );

		spimData.setViewsInterestPoints( viewsInterestPoints );
		return spimData;
	}

	@Override
	public Element toXml( final SpimData2 spimData, final File xmlFileDirectory ) throws SpimDataException
	{
		final Element root = super.toXml( spimData, xmlFileDirectory );
		root.addContent( xmlViewsInterestPoints.toXml( spimData.getViewInterestPoints() ) );
		return root;
	}
}
