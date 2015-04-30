package task;

import spim.fiji.plugin.Interest_Point_Registration;

/**
 * Created by moon on 4/30/15.
 */
public class RegistrationTask extends BaseTask
{
	protected RegistrationTask( String filename )
	{
		super( filename );
	}

	public void register()
	{
		Interest_Point_Registration ipr = new Interest_Point_Registration();
		ipr.defaultProcess( xmlFileName );
	}

	public static void main(String[] argv)
	{
		RegistrationTask task = new RegistrationTask( argv[0] );
		task.register();
	}
}
