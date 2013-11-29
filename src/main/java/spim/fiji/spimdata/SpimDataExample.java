package spim.fiji.spimdata;

//import static mpicbg.spim.data.newstuff.SpimDataXmlKeys.*;

import java.io.File;
import java.io.StringWriter;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import mpicbg.spim.data.XmlHelpers;

import org.w3c.dom.Document;

public class SpimDataExample
{
	public static void main( final String[] args ) throws Exception
	{
		// load SpimData from xml file
		final String xmlFilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/example_fromdialog.xml";
		
		//final XmlIoSpimData< TimePoint, ViewSetupBeads > io = XmlIoSpimData.createDefault();
		//final XmlIoSpimData< TimePoint, ViewSetupBeads > io = XmlIo.createDefaultIo();

		//final SpimData< TimePoint, ViewSetupBeads > spimData = io.load( xmlFilename );
		
		final XmlIoSpimDataInterestPoints io = XmlIo.createDefaultIo();
		
		final SpimDataInterestPoints spimData = io.load( xmlFilename );
		
		// save SpimData to xml file
		io.save( spimData, "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/example_fromdialog2.xml" );

		// write SpimData into a xml Document
		final Document doc = XmlHelpers.newXmlDocument();
		doc.appendChild( io.toXml( doc, spimData, new File(".") ) );

		// output Document to System.out
		final Transformer transformer = TransformerFactory.newInstance().newTransformer();
		transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
		transformer.setOutputProperty( OutputKeys.ENCODING, "UTF-8" );
		transformer.setOutputProperty( "{http://xml.apache.org/xslt}indent-amount", "4" );
		final StringWriter w = new StringWriter();
		transformer.transform( new DOMSource( doc ), new StreamResult( w ) );
		System.out.println( w );
	}
}
