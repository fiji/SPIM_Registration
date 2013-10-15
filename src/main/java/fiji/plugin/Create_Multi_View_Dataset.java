package fiji.plugin;

import fiji.datasetmanager.LightSheetZ1;
import fiji.datasetmanager.MultiViewDatasetDefinition;
import fiji.datasetmanager.StackListImageJ;
import fiji.datasetmanager.StackListLOCI;
import fiji.spimdata.SpimDataBeads;
import fiji.spimdata.XmlIo;
import fiji.spimdata.XmlIoSpimDataBeads;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Font;
import java.awt.Label;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;

import net.imglib2.img.display.imagej.ImageJFunctions;

public class Create_Multi_View_Dataset implements PlugIn
{
	final public static ArrayList< MultiViewDatasetDefinition > datasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
	public static int defaultDatasetDef = 0;

	final int numLinesDocumentation = 15;
	final int numCharacters = 80;
	
	static
	{
		datasetDefinitions.add( new StackListLOCI() );
		datasetDefinitions.add( new StackListImageJ() );
		datasetDefinitions.add( new LightSheetZ1() );
	}
	
	@Override
	public void run( String arg0 ) 
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
		final GenericDialogPlus gd1 = new GenericDialogPlus( "Select_type_of_multi-view dataset" );

		if ( defaultDatasetDef >= numDatasetDefinitions )
			defaultDatasetDef = 0;
		
		gd1.addChoice( "Type_of_dataset: ", titles, titles[ defaultDatasetDef ] );
		Choice choice = (Choice)gd1.getChoices().lastElement();
		gd1.addMessage( "" );
				
		// first add an empty label so that it is not a MultiLineLabel,
		// then add the correct text
		gd1.addMessage( "", new Font( Font.MONOSPACED, Font.ITALIC, 11 ), Color.BLACK );
		Label label = (Label)gd1.getMessage();
		label.setText( formatEntry( datasetDefinitions.get( defaultDatasetDef ).getExtendedDescription(), numCharacters, numLinesDocumentation ) );
		
		addListeners( gd1, choice, label, datasetDefinitions );
		
		gd1.showDialog();
		if ( gd1.wasCanceled() )
			return;
		
		defaultDatasetDef = gd1.getNextChoiceIndex();
		
		// run the definition
		final MultiViewDatasetDefinition def = datasetDefinitions.get( defaultDatasetDef );
		
		System.out.println( defaultDatasetDef );
		
		final SpimDataBeads spimData = def.createDataset();
		
		if ( spimData == null )
		{
			IJ.log( "Defining multi-view dataset failed." );
			return;
		}
		else
		{
			//final XmlIoSpimData< TimePoint, ViewSetupBeads > io = XmlIoSpimData.createDefault();
			final XmlIoSpimDataBeads io = XmlIo.createDefaultIo();
			
			final String xml = new File( spimData.getBasePath(), "example_fromdialog.xml" ).getAbsolutePath();
			try 
			{
				io.save( spimData, xml );
				System.out.println( "Saved xml '" + xml + "'." );
			}
			catch ( Exception e )
			{
				System.out.println( "Could not save xml '" + xml + "': " + e );
				e.printStackTrace();
			}

			// show the first image
			new ImageJ();
			ImageJFunctions.show( spimData.getSequenceDescription().getImgLoader().getImage( spimData.getSequenceDescription().getViewDescription( 0, 0 ), true ) );
			ImageJFunctions.show( spimData.getSequenceDescription().getImgLoader().getUnsignedShortImage( spimData.getSequenceDescription().getViewDescription( 0, 0 ) ) );
		}
	}
	
	public static String formatEntry( String line, final int numCharacters, final int numLines )
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
		
		line = "";
		
		for ( int j = 0; j < numLines - 1; ++j )
			line += split[ j ] + "\n";

		line += split[ numLines - 1 ];

		return line;
	}

	protected void addListeners( final GenericDialog gd, final Choice choice, final Label label, final ArrayList< MultiViewDatasetDefinition > datasetDefinitions )
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
		new Create_Multi_View_Dataset().run( null );
		
		//System.exit( 0 );
	}
}
