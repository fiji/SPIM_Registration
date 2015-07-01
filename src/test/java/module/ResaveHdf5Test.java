package module;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import com.google.common.io.Files;
import mpicbg.spim.data.SpimDataException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import task.ResaveHdf5Task;

import java.io.File;
import java.io.IOException;

/**
 * Test class for resave HDF5 file
 */
public class ResaveHdf5Test
{
	String xmlFile = "/Users/moon/temp/moon/test.xml";

	@Before
	public void backupXml()
	{
		try
		{
			Files.copy( new File( xmlFile ), new File( xmlFile + ".org" ) );
			Files.copy( new File(xmlFile + ".define"), new File(xmlFile));
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	@After
	public void restoreXml()
	{
		try
		{
			Files.move( new File( xmlFile + ".org" ), new File( xmlFile ) );
		}
		catch ( IOException e )
		{
			e.printStackTrace();
		}
	}

	private SpimDataMinimal openSpimData()
	{
		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();

		SpimDataMinimal spimData;
		try
		{
			spimData = io.load( new File( xmlFile ).getAbsolutePath() );
		}
		catch ( final SpimDataException e )
		{
			throw new RuntimeException( e );
		}

		return spimData;
	}

	@Test
	public void ResaveHdf5TaskTestNoCluster()
	{
		ResaveHdf5Task resave = new ResaveHdf5Task();
		ResaveHdf5Task.Parameters params = new ResaveHdf5Task.Parameters();

		params.setXmlFilename( xmlFile );
		params.setUseCluster( false );

		resave.process( params );

		// Load SPIM data again
		SpimDataMinimal spimData = openSpimData();
		String className = spimData.getSequenceDescription().getImgLoader().getClass().getSimpleName();

		// To check if the class is Hdf5ImageLoader
		Assert.assertEquals( className, "Hdf5ImageLoader" );
	}

	@Test
	public void ResaveHdf5TaskTestUseCluster()
	{
		ResaveHdf5Task resave = new ResaveHdf5Task();
		ResaveHdf5Task.Parameters params = new ResaveHdf5Task.Parameters();

		params.setXmlFilename( xmlFile );
		params.setUseCluster( true );

		resave.process( params );

		// Load SPIM data again
		SpimDataMinimal spimData = openSpimData();
		String className = spimData.getSequenceDescription().getImgLoader().getClass().getSimpleName();

		// To check if the class is Hdf5ImageLoader
		Assert.assertEquals( className, "Hdf5ImageLoader" );
	}

	@Test
	public void ResaveHdf5CliTest()
	{
		final String[] params = new String[] {
				"-Dxml_filename=/Users/moon/temp/moon/test.xml",
				"-Dsubsampling_factors={1,1,1}, {2,2,1}, {4,4,2}",
				"-Dhdf5_chunk_sizes={16,16,16}, {16,16,16}, {16,16,16}"
		};

		ResaveHdf5Task resave = new ResaveHdf5Task();
		resave.process( params );

		// Load SPIM data again
		SpimDataMinimal spimData = openSpimData();
		String className = spimData.getSequenceDescription().getImgLoader().getClass().getSimpleName();

		// To check if the class is Hdf5ImageLoader
		Assert.assertEquals( className, "Hdf5ImageLoader" );
	}
}
