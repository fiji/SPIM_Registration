package module;

import com.google.common.io.Files;
import mpicbg.spim.postprocessing.deconvolution2.LRFFT;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import task.FusionTask;
import task.RegisterationTask;

import java.io.File;
import java.io.IOException;

/**
 * Test class for Fusion task
 */
public class FusionTest
{
	String xmlFile = "/Users/moon/temp/moon/test.xml";

	@Before
	public void backupXml()
	{
		try
		{
			Files.copy( new File( xmlFile ), new File( xmlFile + ".org" ) );
			Files.copy( new File( xmlFile + ".registered" ), new File( xmlFile ) );
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

	@Test
	public void TestEfficientBayesianGPU()
	{
		FusionTask task = new FusionTask();
		FusionTask.Parameters params = new FusionTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( FusionTask.Method.EfficientBayesianBased );
		params.setComputeOn( 1 );
		params.setBlockSize( new int[] { 256, 256, 256 } );
		params.setIterationType( LRFFT.PSFTYPE.OPTIMIZATION_I );

		task.process( params );
	}

	@Test
	public void TestEfficientBayesianCPUCli()
	{
		final String[] params = new String[] {
				"-Dxml_filename=/Users/moon/temp/moon/test.xml",
				"-Dmethod=EfficientBayesianBased",
				"-Dcompute_on=0",
				"-Dblock_size='{256, 256, 256}'",
				"-Diteration_type=OPTIMIZATION_I",
				"-Dmin='{183, 45, -690}'",
				"-Dmax='{910, 1926, 714}'",
				"-Dexport=Save3dTIFF"
		};

		final FusionTask task = new FusionTask();
		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}
}
