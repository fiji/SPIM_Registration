package spim.fiji.spimdata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;
import mpicbg.spim.io.IOFunctions;

import org.jdom2.Element;

import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.fiji.spimdata.interestpoints.XmlIoViewInterestPoints;

public class XmlIoSpimData2 extends XmlIoAbstractSpimData< SequenceDescription, SpimData2 >
{
	final XmlIoViewInterestPoints xmlViewsInterestPoints;
	public static int numBackups = 5;
	
	public XmlIoSpimData2()
	{
		super( SpimData2.class, new XmlIoSequenceDescription(), new XmlIoViewRegistrations() );
		xmlViewsInterestPoints = new XmlIoViewInterestPoints();
		handledTags.add( xmlViewsInterestPoints.getTag() );
	}

	@Override
	public void save( final SpimData2 spimData, final String xmlFilename ) throws SpimDataException
	{
		// fist make a copy of the XML and save it to not loose it
		if ( new File( xmlFilename ).exists() )
		{
			int maxExistingBackup = 0;
			for ( int i = 1; i < numBackups; ++i )
				if ( new File( xmlFilename + "~" + i ).exists() )
					maxExistingBackup = i;
				else
					break;

			// copy the backups
			try
			{
				for ( int i = maxExistingBackup; i >= 1; --i )
				{
					final Path input = Paths.get( xmlFilename + "~" + i );
					final Path output = Paths.get( xmlFilename + "~" + (i + 1) );
					Files.copy( input, output, StandardCopyOption.REPLACE_EXISTING );
				}

				final Path input = Paths.get( xmlFilename );
				final Path output = Paths.get( xmlFilename + "~1" );
				Files.copy( input, output, StandardCopyOption.REPLACE_EXISTING );
			}
			catch ( final IOException e )
			{
				IOFunctions.println( "Could not save backup of XML file: " + e );
				e.printStackTrace();
			}
		}

		super.save( spimData, xmlFilename );
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
