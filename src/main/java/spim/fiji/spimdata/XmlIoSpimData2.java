package spim.fiji.spimdata;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.fiji.spimdata.interestpoints.XmlIoViewInterestPoints;

public class XmlIoSpimData2 extends XmlIoSpimData< TimePoint, ViewSetup >
{
	final XmlIoViewInterestPoints xmlViewsInterestPoints;

	public XmlIoSpimData2( final XmlIoSequenceDescription< TimePoint, ViewSetup > xmlSequenceDescription,
			final XmlIoViewRegistrations xmlViewRegistrations, final XmlIoViewInterestPoints xmlViewsInterestPoints )
	{
		super( xmlSequenceDescription, xmlViewRegistrations );

		this.xmlViewsInterestPoints = xmlViewsInterestPoints;
	}

	@Override
	public SpimData2 load( final String xmlFilename ) throws JDOMException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		return ( SpimData2 ) super.load( xmlFilename );
	}

	@Override
	public SpimData2 fromXml( final Element root, final File basePath ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
//		String version = getVersion( root );

		Element elem = root.getChild( xmlSequenceDescription.getTagName() );
		if ( elem == null )
			throw new IllegalArgumentException( "no <" + xmlSequenceDescription.getTagName() + "> element found." );
		final SequenceDescription< TimePoint, ViewSetup > seq = xmlSequenceDescription.fromXml( elem, basePath );

		elem = root.getChild( xmlViewRegistrations.getTagName() );
		if ( elem == null )
			throw new IllegalArgumentException( "no <" + xmlViewRegistrations.getTagName() + "> element found." );
		final ViewRegistrations reg = xmlViewRegistrations.fromXml( elem );

		final ViewInterestPoints viewsInterestPoints;
		elem = root.getChild( xmlViewsInterestPoints.getTagName() );
		if ( elem == null )
			viewsInterestPoints = ViewInterestPoints.createViewInterestPoints( seq.getViewDescriptions() );
		else
			viewsInterestPoints = xmlViewsInterestPoints.fromXml( elem, basePath, seq.getViewDescriptions() );

		return new SpimData2( basePath, seq, reg, viewsInterestPoints );
	}

	public void save( final SpimData2 spimData, final String xmlFilename ) throws IOException
	{
		final File xmlFileDirectory = new File( xmlFilename ).getParentFile();
		final Document doc = new Document( toXml( spimData, xmlFileDirectory ) );
		final XMLOutputter xout = new XMLOutputter( Format.getPrettyFormat() );
		xout.output( doc, new FileWriter( xmlFilename ) );
	}

	public Element toXml( final SpimData2 spimData, final File xmlFileDirectory )
	{
		final Element root = super.toXml( spimData, xmlFileDirectory );
		root.addContent( xmlViewsInterestPoints.toXml( spimData.getViewInterestPoints() ) );
		return root;
	}
}
