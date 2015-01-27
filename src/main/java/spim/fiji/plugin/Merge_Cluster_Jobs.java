package spim.fiji.plugin;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.Color;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.io.File;
import java.util.ArrayList;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.cluster.MergeClusterJobs;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;

public class Merge_Cluster_Jobs implements PlugIn
{
	public static String defaultContains1 = "job_";
	public static String defaultContains2 = ".xml";
	public static String defaultNewXML = null;
	public static String defaultMergeXMLDir = null;

	Color color = GUIHelper.neutral;
	String message = "---";
	ArrayList< File > xmls = new ArrayList< File >();

	@Override
	public void run( String arg0 )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Select XML's to Merge" );

		if ( defaultMergeXMLDir == null )
			defaultMergeXMLDir = new File( GenericLoadParseQueryXML.defaultXMLfilename ).getParent();

		gd.addDirectoryField( "Directory", defaultMergeXMLDir, 50 );
		gd.addStringField( "Filename_contains", defaultContains1 );
		gd.addStringField( "Filename_also_contains", defaultContains2 );

		final TextField directory = (TextField)gd.getStringFields().firstElement();
		final TextField contains1 = (TextField)gd.getStringFields().get( 1 );
		final TextField contains2 = (TextField)gd.getStringFields().get( 2 );

		if ( defaultNewXML == null )
			defaultNewXML = "";

		gd.addFileField( "Merged_XML", defaultNewXML, 50 );

		// a first run
		findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText() );

		gd.addMessage( "" );
		gd.addMessage( this.message, GUIHelper.largestatusfont, this.color );

		final Label target = (Label)gd.getMessage();

		addListeners( gd, directory, contains1, contains2, target );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText() );

		IOFunctions.println( "Attempting to merge the following XML's:" );

		for ( final File f : this.xmls )
			IOFunctions.println( "  " + f.getAbsolutePath() );

		defaultMergeXMLDir = gd.getNextString();
		defaultContains1 = gd.getNextString();
		defaultContains2 = gd.getNextString();
		final File newXML = new File( defaultNewXML = gd.getNextString() );

		try
		{
			MergeClusterJobs.merge( xmls, newXML );

			IOFunctions.println( "Successfully merged all XML's into one new XML: + " + newXML.getAbsolutePath() );
		}
		catch ( final SpimDataException e )
		{
			IOFunctions.println( "Failed to merge XML's: " + e );
			e.printStackTrace();
		}
	}

	protected void addListeners(
			final GenericDialog gd,
			final TextField directory,
			final TextField contains1,
			final TextField contains2,
			final Label label )
	{
		directory.addTextListener( new TextListener()
		{
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText() );
					update( label );
				}
			}
		});

		contains1.addTextListener( new TextListener()
		{
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText() );
					update( label );
				}
			}
		});

		contains2.addTextListener( new TextListener()
		{
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText() );
					update( label );
				}
			}
		});
	}

	protected void update( final Label label )
	{
		label.setText( this.message );
		label.setForeground( this.color );
	}

	protected void findFiles( final File dir, final String contains1, final String contains2 )
	{
		this.xmls.clear();

		if ( !dir.isDirectory() )
		{
			this.message = "Path provided is not a directory.";
			this.color = GUIHelper.error;
		}
		else
		{
			for ( final String file : dir.list() )
			{
				if ( file.contains( contains1 ) && file.contains( contains2 ) )
					this.xmls.add( new File( dir, file ) );
			}

			if ( this.xmls.size() == 0 )
			{
				this.message = "No files found that match the name pattern.";
				this.color = GUIHelper.warning;
			}
			else
			{
				this.message = "Found " + this.xmls.size() + " files that match the name pattern.";
				this.color = GUIHelper.good;
			}
		}
	}

	public static void main( String[] args )
	{
		new Merge_Cluster_Jobs().run( null );
	}
}
