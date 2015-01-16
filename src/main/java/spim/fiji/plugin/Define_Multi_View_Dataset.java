package spim.fiji.plugin;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.datasetmanager.LightSheetZ1;
import spim.fiji.datasetmanager.MultiViewDatasetDefinition;
import spim.fiji.datasetmanager.StackListImageJ;
import spim.fiji.datasetmanager.StackListLOCI;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.plugin.util.MyMultiLineLabel;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;

public class Define_Multi_View_Dataset implements PlugIn
{
	final public static ArrayList< MultiViewDatasetDefinition > staticDatasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
	public static int defaultDatasetDef = 2;
	public static String defaultXMLName = "dataset.xml";

	final int numLinesDocumentation = 15;
	final int numCharacters = 80;
	
	static
	{
		IOFunctions.printIJLog = true;
		staticDatasetDefinitions.add( new StackListLOCI() );
		staticDatasetDefinitions.add( new StackListImageJ() );
		staticDatasetDefinitions.add( new LightSheetZ1() );
	}
	
	@Override
	public void run( String arg0 ) 
	{
		final ArrayList< MultiViewDatasetDefinition > datasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
		
		for ( final MultiViewDatasetDefinition mvd : staticDatasetDefinitions )
			datasetDefinitions.add( mvd.newInstance() );
		
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
		final GenericDialogPlus gd1 = new GenericDialogPlus( "Select type of multi-view dataset" );

		if ( defaultDatasetDef >= numDatasetDefinitions )
			defaultDatasetDef = 0;
		
		gd1.addChoice( "Type_of_dataset: ", titles, titles[ defaultDatasetDef ] );
		//Choice choice = (Choice)gd1.getChoices().lastElement();
		gd1.addStringField( "XML_filename", defaultXMLName, 30 );
		/*
		final MyMultiLineLabel label = MyMultiLineLabel.addMessage( gd1,
				formatEntry( datasetDefinitions.get( defaultDatasetDef ).getExtendedDescription(), numCharacters, numLinesDocumentation ),
				new Font( Font.MONOSPACED, Font.PLAIN, 11 ),
				Color.BLACK );
						
		addListeners( gd1, choice, label, datasetDefinitions );*/
		
		GUIHelper.addWebsite( gd1 );
		
		gd1.showDialog();
		if ( gd1.wasCanceled() )
			return;
		
		defaultDatasetDef = gd1.getNextChoiceIndex();
		final String xmlFileName = defaultXMLName = gd1.getNextString();
		
		// run the definition
		final MultiViewDatasetDefinition def = datasetDefinitions.get( defaultDatasetDef );
		
		IOFunctions.println( defaultDatasetDef );
		
		final SpimData2 spimData = def.createDataset();
		
		if ( spimData == null )
		{
			IOFunctions.println( "Defining multi-view dataset failed." );
			return;
		}
		else
		{
			//final XmlIoSpimData< TimePoint, ViewSetupBeads > io = XmlIoSpimData.createDefault();
			final XmlIoSpimData2 io = new XmlIoSpimData2( "" );
			
			final String xml = new File( spimData.getBasePath(), xmlFileName ).getAbsolutePath();
			try 
			{
				io.save( spimData, xml );
				IOFunctions.println( "Saved xml '" + xml + "'." );
				GenericLoadParseQueryXML.defaultXMLfilename = xml;
			}
			catch ( Exception e )
			{
				IOFunctions.println( "Could not save xml '" + xml + "': " + e );
				e.printStackTrace();
			}

			// show the first image
			//new ImageJ();
			//ImageJFunctions.show( spimData.getSequenceDescription().getImgLoader().getImage( spimData.getSequenceDescription().getViewDescription( 0, 0 ), true ) );
			//ImageJFunctions.show( spimData.getSequenceDescription().getImgLoader().getUnsignedShortImage( spimData.getSequenceDescription().getViewDescription( 0, 0 ) ) );
		}
	}
	
	public static String[] formatEntry( String line, final int numCharacters, final int numLines )
	{
		if ( line == null )
			line = "";
		
		String[] split = line.split( "\n" );
		
		if ( split.length != numLines )
		{
			String[] split2 = new String[ numLines ];

			for ( int j = 0; j < Math.min( split.length, numLines ); ++j )
				split2[ j ] = split[ j ];
			
			for ( int j = Math.min( split.length, numLines ); j < numLines; ++j )
				split2[ j ] = "";

			split = split2;
		}
		
		for ( int j = 0; j < split.length; ++j )
		{
			String s = split[ j ];
			
			if ( s.length() > 80 )
				s = s.substring( 0, 80 );
			
			// fill up to numCharacters + 3
			for ( int i = s.length(); i < numCharacters + 3; ++i )
				s = s + " ";
			
			split[ j ] = s;
		}
		
		return split;
	}

	protected void addListeners( final GenericDialog gd, final Choice choice, final MyMultiLineLabel label, final ArrayList< MultiViewDatasetDefinition > datasetDefinitions )
	{
		gd.addDialogListener( new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == choice )
				{
					label.setText( formatEntry( datasetDefinitions.get( choice.getSelectedIndex() ).getExtendedDescription(), numCharacters, numLinesDocumentation ) );
				}
				return true;
			}
		} );
		
	}
	
	public static void main( String args[] )
	{
		//new ImageJ();
		new Define_Multi_View_Dataset().run( null );
		
		//System.exit( 0 );
	}
}
