package fiji.plugin;

import java.util.ArrayList;

import mpicbg.spim.data.SpimData;

import fiji.datasetmanager.MultiViewDatasetDefinition;
import fiji.datasetmanager.StackListLOCI;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

public class Create_Multi_View_Dataset implements PlugIn
{
	final public static ArrayList< MultiViewDatasetDefinition > datasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
	public static int defaultDatasetDef = 0;
	
	static
	{
		datasetDefinitions.add( new StackListLOCI() );
	}

	@Override
	public void run(String arg0) 
	{
		// verify that there are definitions
		final int numDatasetDefinitions = datasetDefinitions.size();
		
		if ( numDatasetDefinitions == 0 )
		{
			IJ.log( "No Multi-View Dataset Definitions available." );
			return;
		}
		
		// get their names
		final String[] titles = new String[ numDatasetDefinitions ];
		
		for ( int i = 0; i < datasetDefinitions.size(); ++i )
			titles[ i ] = datasetDefinitions.get( i ).getTitle();
		
		// query the dataset definition to use
		final GenericDialog gd1 = new GenericDialog( "Select_type_of_multi-view dataset" );

		if ( defaultDatasetDef >= numDatasetDefinitions )
			defaultDatasetDef = 0;
		
		gd1.addChoice( "Type_of_dataset: ", titles, titles[ defaultDatasetDef ] );
		Object o1 = gd1.getStringFields().lastElement();
		gd1.addMessage( "" );
		gd1.addMessage( "hallo" );
		Object o2 = gd1.getStringFields().lastElement();
		
		gd1.showDialog();
		if ( gd1.wasCanceled() )
			return;
		
		defaultDatasetDef = gd1.getNextChoiceIndex();
		
		// run the definition
		final MultiViewDatasetDefinition def = datasetDefinitions.get( defaultDatasetDef );
		
		final SpimData< ?, ? > spimData = def.createDataset();
		
		if ( spimData == null )
		{
			IJ.log( "Defining multi-view dataset failed." );
			return;
		}
	}

}
