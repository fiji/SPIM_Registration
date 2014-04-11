package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.process.boundingbox.CompleteBoundingBox;
import spim.process.boundingbox.ManualBoundingBox;
import spim.process.fusion.WeightedAverageFusion;

public class Image_Fusion implements PlugIn
{
	public static ArrayList< Fusion > staticFusionAlgorithms = new ArrayList< Fusion >();
	public static int defaultFusionAlgorithm = 0;

	public static ArrayList< BoundingBox > staticBoundingBoxAlgorithms = new ArrayList< BoundingBox >();
	public static int defaultBoundingBoxAlgorithm = 0;

	static
	{
		IOFunctions.printIJLog = true;
		staticFusionAlgorithms.add( new WeightedAverageFusion( null, null, null, null, null ) );
		
		staticBoundingBoxAlgorithms.add( new ManualBoundingBox( null, null, null, null, null ) );
		staticBoundingBoxAlgorithms.add( new CompleteBoundingBox( null, null, null, null, null ) );
	}

	@Override
	public void run( final String arg )
	{
		// ask for everything
		final XMLParseResult result = new LoadParseQueryXML().queryXML( true, true, true, true );
		
		if ( result == null )
			return;
		
		// the GenericDialog needs a list[] of String
		final String[] fusionDescriptions = new String[ staticFusionAlgorithms.size() ];
		final String[] boundingBoxDescriptions = new String[ staticBoundingBoxAlgorithms.size() ];
		
		for ( int i = 0; i < staticFusionAlgorithms.size(); ++i )
			fusionDescriptions[ i ] = staticFusionAlgorithms.get( i ).getDescription();
		for ( int i = 0; i < staticBoundingBoxAlgorithms.size(); ++i )
			boundingBoxDescriptions[ i ] = staticBoundingBoxAlgorithms.get( i ).getDescription();
		
		if ( defaultFusionAlgorithm >= fusionDescriptions.length )
			defaultFusionAlgorithm = 0;
		if ( defaultBoundingBoxAlgorithm >= boundingBoxDescriptions.length )
			defaultBoundingBoxAlgorithm = 0;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );
		
		gd.addChoice( "Type_of_image_fusion", fusionDescriptions, fusionDescriptions[ defaultFusionAlgorithm ] );
		gd.addChoice( "Bounding_Box defined by", boundingBoxDescriptions, boundingBoxDescriptions[ defaultBoundingBoxAlgorithm ] );
		
		gd.addMessage( "" );
		GUIHelper.addWebsite( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final int fusionAlgorithm = defaultFusionAlgorithm = gd.getNextChoiceIndex();
		final int boundingBoxAlgorithm = defaultBoundingBoxAlgorithm = gd.getNextChoiceIndex();

		final BoundingBox boundingBox = staticBoundingBoxAlgorithms.get( boundingBoxAlgorithm ).newInstance(
				result.getData(), 
				result.getAnglesToProcess(),
				result.getChannelsToProcess(),
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess() );
		
		if ( !boundingBox.queryParameters() )
			return;
		
		final Fusion fusion = staticFusionAlgorithms.get( fusionAlgorithm ).newInstance(
				result.getData(), 
				result.getAnglesToProcess(),
				result.getChannelsToProcess(),
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess() );

	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Image_Fusion().run( null );
	}
}
