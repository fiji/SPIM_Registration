package module;

import com.google.common.io.Files;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import task.DetectInterestPointTask;

import java.io.File;
import java.io.IOException;

/**
 * Test class for Detect Interest Points task
 */
public class DetectInterestPointTest
{
	String xmlFile = "/Users/moon/temp/moon/test.xml";

	@Before
	public void backupXml()
	{
		try
		{
			Files.copy( new File( xmlFile ), new File( xmlFile + ".org" ) );
			Files.copy( new File( xmlFile + ".hdf5" ), new File( xmlFile ) );
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
	public void TestDifferenceOfMean()
	{
		DetectInterestPointTask task = new DetectInterestPointTask();
		DetectInterestPointTask.Parameters params = new DetectInterestPointTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( DetectInterestPointTask.Method.DifferenceOfMean );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestDifferenceOfGaussianCPU()
	{
		DetectInterestPointTask task = new DetectInterestPointTask();
		DetectInterestPointTask.Parameters params = new DetectInterestPointTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( DetectInterestPointTask.Method.DifferenceOfGaussian );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestDifferenceOfGaussianGPU()
	{
		DetectInterestPointTask task = new DetectInterestPointTask();
		DetectInterestPointTask.Parameters params = new DetectInterestPointTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( DetectInterestPointTask.Method.DifferenceOfGaussian );

		// 0:"CPU (Java)",
		// 1:"GPU approximate (Nvidia CUDA via JNA)",
		// 2:"GPU accurate (Nvidia CUDA via JNA)"
		params.setComputeOn( 1 );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestDifferenceOfGaussianGPUAccurate()
	{
		DetectInterestPointTask task = new DetectInterestPointTask();
		DetectInterestPointTask.Parameters params = new DetectInterestPointTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( DetectInterestPointTask.Method.DifferenceOfGaussian );

		// 0:"CPU (Java)",
		// 1:"GPU approximate (Nvidia CUDA via JNA)",
		// 2:"GPU accurate (Nvidia CUDA via JNA)"
		params.setComputeOn( 2 );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestDifferenceOfGaussianGPUCli()
	{
		final String[] params = new String[] {
				"-Dxml_filename=/Users/moon/temp/moon/test.xml",
				"-Dmethod=DifferenceOfGaussian",
				"-Dcompute_on=1",
				"-Ddownsample_xy=1",
				"-Ddownsample_z=1",
				"-Dminimal_intensity=0.0",
				"-Dmaximal_intensity=65535.0",
				"-Dimage_sigma_x=0.5",
				"-Dimage_sigma_y=0.5",
				"-Dimage_sigma_z=0.5",
				"-Dsubpixel_localization=1"
		};

		final DetectInterestPointTask task = new DetectInterestPointTask();
		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}
}
