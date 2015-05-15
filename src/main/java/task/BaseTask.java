package task;

import mpicbg.spim.io.IOFunctions;

/**
 * Created by moon on 4/30/15.
 */
public abstract class BaseTask
{
	final protected String xmlFileName;

	protected BaseTask(String filename)
	{
		xmlFileName = filename;
		IOFunctions.printIJLog = false;
	}
}
