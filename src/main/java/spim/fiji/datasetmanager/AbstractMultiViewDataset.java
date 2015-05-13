package spim.fiji.datasetmanager;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import spim.fiji.plugin.util.GUIHelper;

/**
 * Created by moon on 5/13/15.
 */
public abstract class AbstractMultiViewDataset implements MultiViewDatasetDefinition
{
	public String defaultXMLName = "dataset.xml";

	public static class Parameters
	{
		private String xmlFilename;

		/***
		 * XML file name for the dataset definition
		 */
		public String getXmlFilename()
		{
			return xmlFilename;
		}

		public void setXmlFilename( String xmlFilename )
		{
			this.xmlFilename = xmlFilename;
		}
	}

	public boolean queryDialog()
	{
		// query the dataset definition to use
		final GenericDialogPlus gd1 = new GenericDialogPlus( "Select type of multi-view dataset" );

		gd1.addStringField( "XML_filename", defaultXMLName, 30 );

		GUIHelper.addWebsite( gd1 );

		gd1.showDialog();
		if ( gd1.wasCanceled() )
			return false;

		defaultXMLName = gd1.getNextString();

		return true;
	}

	protected void process(Parameters params)
	{
		if( IJ.debugMode )
		{
			System.out.println("Output XML File: " + params.getXmlFilename());
		}
	}
}
