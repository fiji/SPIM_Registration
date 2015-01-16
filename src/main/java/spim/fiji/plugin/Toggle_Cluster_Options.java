package spim.fiji.plugin;

import mpicbg.spim.io.IOFunctions;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class Toggle_Cluster_Options implements PlugIn
{
	/**
	 * Set this to true so that the option to process as individual cluster jobs shows up in the dialogs
	 */
	public static boolean displayClusterProcessing = false;

	@Override
	public void run( String arg0 )
	{
		final GenericDialog gd = new GenericDialog( "Toggle Cluster Processing Options" );
		gd.addCheckbox( "Display_Cluster Processing Options", displayClusterProcessing );
		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		displayClusterProcessing = gd.getNextBoolean();

		IOFunctions.println( "Cluster processing option: " + ( displayClusterProcessing ? "ON" : "OFF" ) );
	}
}
