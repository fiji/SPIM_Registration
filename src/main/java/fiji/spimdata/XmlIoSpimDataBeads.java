package fiji.spimdata;

import static mpicbg.spim.data.SpimDataXmlKeys.SPIMDATA_TAG;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fiji.spimdata.beads.ViewBeads;
import fiji.spimdata.beads.XmlIoViewBeads;

public class XmlIoSpimDataBeads extends XmlIoSpimData< TimePoint, ViewSetup >
{
	final XmlIoViewBeads xmlViewBeads;
	
	public XmlIoSpimDataBeads( final XmlIoSequenceDescription< TimePoint, ViewSetup > xmlSequenceDescription,
			final XmlIoViewRegistrations xmlViewRegistrations, final XmlIoViewBeads xmlViewBeads )
	{
		super( xmlSequenceDescription, xmlViewRegistrations );
	
		this.xmlViewBeads = xmlViewBeads;
	}
	
	@Override
	public SpimDataBeads load( final String xmlFilename ) throws ParserConfigurationException, SAXException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final File xmlFile = new File( xmlFilename );

		final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		final DocumentBuilder db = dbf.newDocumentBuilder();
		final Document dom = db.parse( xmlFilename );
		final Element root = dom.getDocumentElement();

		if ( root.getNodeName() != SPIMDATA_TAG )
			throw new RuntimeException( "expected <" + SPIMDATA_TAG + "> root element. wrong file?" );

		final File basePath = loadBasePath( root, xmlFile );
		
		return fromXml( root, basePath );
	}

	@Override
	public SpimDataBeads fromXml( final Element root, final File basePath ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
//		String version = getVersion( root );

		NodeList nodes = root.getElementsByTagName( xmlSequenceDescription.getTagName() );
		if ( nodes.getLength() == 0 )
			throw new IllegalArgumentException( "no <" + xmlSequenceDescription.getTagName() + "> element found." );
		final SequenceDescription< TimePoint, ViewSetup > seq = xmlSequenceDescription.fromXml( ( Element ) nodes.item( 0 ), basePath );

		nodes = root.getElementsByTagName( xmlViewRegistrations.getTagName() );
		if ( nodes.getLength() == 0 )
			throw new IllegalArgumentException( "no <" + xmlViewRegistrations.getTagName() + "> element found." );
		final ViewRegistrations reg = xmlViewRegistrations.fromXml( ( Element ) nodes.item( 0 ) );
		
		final ArrayList< ViewBeads > beads;
		nodes = root.getElementsByTagName( xmlViewBeads.getTagName() );
		
		if ( nodes.getLength() == 0 )
			beads = new ArrayList< ViewBeads >();
		else
			beads = xmlViewBeads.fromXml( ( Element ) nodes.item( 0 ) );
		
		return new SpimDataBeads( basePath, seq, reg, beads );
	}

	public void save( final SpimDataBeads spimData, final String xmlFilename ) throws ParserConfigurationException, InstantiationException, IllegalAccessException, ClassNotFoundException, FileNotFoundException, TransformerFactoryConfigurationError, TransformerException
	{
		final Document doc = XmlHelpers.newXmlDocument();
		final File xmlFileDirectory = new File( xmlFilename ).getParentFile();
		doc.appendChild( toXml( doc, spimData, xmlFileDirectory ) );
		XmlHelpers.writeXmlDocument( doc, xmlFilename );
	}

	public Element toXml( final Document doc, final SpimDataBeads spimData, final File xmlFileDirectory ) throws InstantiationException, IllegalAccessException, ClassNotFoundException
	{
		final Element root = super.toXml( doc, spimData, xmlFileDirectory );
		
		root.appendChild( xmlViewBeads.toXml( doc, spimData.getViewBeads() ) );
		
		return root;
	}
}
