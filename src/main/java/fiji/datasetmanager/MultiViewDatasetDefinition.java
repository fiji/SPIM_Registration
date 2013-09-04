package fiji.datasetmanager;

import mpicbg.spim.data.SpimData;
import ij.plugin.PlugIn;

public interface MultiViewDatasetDefinition extends PlugIn
{
	/**
	 * Defines the title under which it will be displayed in the list
	 * of available multi-view dataset definitions
	 * 
	 * @return
	 */
	public String getTitle();
	
	/**
	 * This method is supposed to (interactively, ideally ImageJ-macroscriptable)
	 * query all necessary data from the user to build up a SpimData object and
	 * save it as an XML file.
	 * 
	 * @return - the saved {@link SpimData} object
	 */
	public SpimData<?, ?> createDataset();
}
