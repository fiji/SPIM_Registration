package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.fusion.boundingbox.AutomaticBoundingBox;
import spim.process.fusion.boundingbox.AutomaticReorientation;
import spim.process.fusion.boundingbox.BigDataViewerBoundingBox;
import spim.process.fusion.boundingbox.BoundingBoxGUI;

public class Define_Bounding_Box implements PlugIn
{
	public static ArrayList< BoundingBoxGUI > staticBoundingBoxAlgorithms = new ArrayList< BoundingBoxGUI >();
	public static int defaultBoundingBoxAlgorithm = 1;
	public static String defaultName = "My Bounding Box";

	static
	{
		IOFunctions.printIJLog = true;

		staticBoundingBoxAlgorithms.add( new BoundingBoxGUI( null, null ) );
		staticBoundingBoxAlgorithms.add( new BigDataViewerBoundingBox( null, null ) );
		staticBoundingBoxAlgorithms.add( new AutomaticReorientation( null, null ) );
		staticBoundingBoxAlgorithms.add( new AutomaticBoundingBox( null, null ) );
	}


	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "bounding box definition", true, true, true, true ) )
			return;

		defineBoundingBox(
			result.getData(),
			SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
			result.getClusterExtension(),
			result.getXMLFileName(),
			true );
		
	}

	public BoundingBox defineBoundingBox(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		return defineBoundingBox( data, viewIds, "", null, false );
	}

	public BoundingBox defineBoundingBox(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
		final String[] boundingBoxDescriptions = new String[ staticBoundingBoxAlgorithms.size() ];

		for ( int i = 0; i < staticBoundingBoxAlgorithms.size(); ++i )
			boundingBoxDescriptions[ i ] = staticBoundingBoxAlgorithms.get( i ).getDescription();

		if ( defaultBoundingBoxAlgorithm >= boundingBoxDescriptions.length )
			defaultBoundingBoxAlgorithm = 0;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );

		gd.addChoice( "Bounding_Box", boundingBoxDescriptions, boundingBoxDescriptions[ defaultBoundingBoxAlgorithm ] );
		gd.addStringField( "Bounding_Box_Name", defaultName, 30 );

		// assemble the last registration names of all viewsetups involved
		final HashMap< String, Integer > names = GUIHelper.assembleRegistrationNames( data, viewIds );
		gd.addMessage( "" );
		GUIHelper.displayRegistrationNames( gd, names );
		gd.addMessage( "" );

		GUIHelper.addWebsite( gd );

		if ( names.keySet().size() > 5 )
			GUIHelper.addScrollBars( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final int boundingBoxAlgorithm = defaultBoundingBoxAlgorithm = gd.getNextChoiceIndex();
		final String boundingBoxName = gd.getNextString();

		for ( final BoundingBox bb : data.getBoundingBoxes().getBoundingBoxes() )
		{
			if ( bb.getTitle().equals( boundingBoxName ) )
			{
				IOFunctions.println( "A bounding box with the name '" + boundingBoxName + "' already exists." );
				defaultName = boundingBoxName + "1";
				return null;
			}
		}

		final BoundingBoxGUI boundingBox = staticBoundingBoxAlgorithms.get( boundingBoxAlgorithm ).newInstance( data, viewIds );

		if ( !boundingBox.queryParameters( null, null ) )
			return null;

		boundingBox.setTitle( boundingBoxName );
		defaultName = boundingBoxName + "1";

		data.getBoundingBoxes().addBoundingBox( boundingBox );

		if ( saveXML )
			SpimData2.saveXML( data, xmlFileName, clusterExtension );

		return boundingBox;
	}

	public static void main( final String[] args )
	{
		LoadParseQueryXML.defaultXMLfilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";
		new ImageJ();
		new Define_Bounding_Box().run( null );
	}
}
