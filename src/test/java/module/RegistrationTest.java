package module;

import com.google.common.io.Files;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import task.RegisterationTask;

import java.io.File;
import java.io.IOException;

/**
 * Test class for Registration task
 */
public class RegistrationTest
{
	String xmlFile = "/Users/moon/temp/moon/test.xml";

	@Before
	public void backupXml()
	{
		try
		{
			Files.copy( new File( xmlFile ), new File( xmlFile + ".org" ) );
			Files.copy( new File( xmlFile + ".detected" ), new File( xmlFile ) );
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
	public void TestRegistrationTimepointsIndividuallyRGLDM()
	{
		RegisterationTask task = new RegisterationTask();
		RegisterationTask.Parameters params = new RegisterationTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( RegisterationTask.Method.RGLDM );
		params.setType( RegisterationTask.RegistrationType.TIMEPOINTS_INDIVIDUALLY );
		params.setTransformationModel( RegisterationTask.TransformationModel.Affine );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestRegistrationTimepointsIndividuallyGeometricHashing()
	{
		RegisterationTask task = new RegisterationTask();
		RegisterationTask.Parameters params = new RegisterationTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( RegisterationTask.Method.GeometricHashing );
		params.setType( RegisterationTask.RegistrationType.TIMEPOINTS_INDIVIDUALLY );
		params.setTransformationModel( RegisterationTask.TransformationModel.Affine );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestRegistrationTimepointsIndividuallyInteractiveClosestPoint()
	{
		RegisterationTask task = new RegisterationTask();
		RegisterationTask.Parameters params = new RegisterationTask.Parameters();

		params.setXmlFilename( xmlFile );
		params.setMethod( RegisterationTask.Method.IterativeClosestPoint );
		params.setType( RegisterationTask.RegistrationType.TIMEPOINTS_INDIVIDUALLY );
		params.setTransformationModel( RegisterationTask.TransformationModel.Affine );

		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}

	@Test
	public void TestRegistrationGeometricHashingCli()
	{
		final String[] params = new String[] {
				"-Dxml_filename=/Users/moon/temp/moon/test.xml",
				"-Dmethod=GeometricHashing",
				"-Dtype_of_registration=TIMEPOINTS_INDIVIDUALLY"
		};

		final RegisterationTask task = new RegisterationTask();
		task.process( params );

		Assert.assertNotEquals( task.getSpimData(), null );
	}
}
