package task;

import spim.fiji.plugin.resave.Resave_HDF5;

/**
 * Created by moon on 4/30/15.
 */
public class ResaveHDF5Task extends BaseTask
{
	public ResaveHDF5Task(String xmlFileName)
	{
		super(xmlFileName);
	}

	public void resaveHDF5()
	{
		// Currently single computer only
		new Resave_HDF5().defaultProcess( xmlFileName, false );
	}

	public static void main(String[] argv)
	{
		ResaveHDF5Task task = new ResaveHDF5Task( argv[0] );
		task.resaveHDF5();
	}
}
