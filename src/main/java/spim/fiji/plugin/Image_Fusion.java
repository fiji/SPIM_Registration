package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;

import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.process.fusion.boundingbox.CompleteBoundingBox;
import spim.process.fusion.boundingbox.ManualBoundingBox;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.weightedavg.WeightedAverageFusion;

public class Image_Fusion implements PlugIn
{
	public static ArrayList< Fusion > staticFusionAlgorithms = new ArrayList< Fusion >();
	public static int defaultFusionAlgorithm = 0;

	public static ArrayList< BoundingBox > staticBoundingBoxAlgorithms = new ArrayList< BoundingBox >();
	public static int defaultBoundingBoxAlgorithm = 1;

	public static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public static int defaultImgExportAlgorithm = 0;

	static
	{
		IOFunctions.printIJLog = true;
		staticFusionAlgorithms.add( new WeightedAverageFusion( null, null, null, null, null ) );
		
		staticBoundingBoxAlgorithms.add( new ManualBoundingBox( null, null, null, null, null ) );
		staticBoundingBoxAlgorithms.add( new CompleteBoundingBox( null, null, null, null, null ) );
		
		staticImgExportAlgorithms.add( new DisplayImage() );
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
		final String[] imgExportDescriptions = new String[ staticImgExportAlgorithms.size() ];
		
		for ( int i = 0; i < staticFusionAlgorithms.size(); ++i )
			fusionDescriptions[ i ] = staticFusionAlgorithms.get( i ).getDescription();
		for ( int i = 0; i < staticBoundingBoxAlgorithms.size(); ++i )
			boundingBoxDescriptions[ i ] = staticBoundingBoxAlgorithms.get( i ).getDescription();
		for ( int i = 0; i < staticImgExportAlgorithms.size(); ++i )
			imgExportDescriptions[ i ] = staticImgExportAlgorithms.get( i ).getDescription();
		
		if ( defaultFusionAlgorithm >= fusionDescriptions.length )
			defaultFusionAlgorithm = 0;
		if ( defaultBoundingBoxAlgorithm >= boundingBoxDescriptions.length )
			defaultBoundingBoxAlgorithm = 0;
		if ( defaultImgExportAlgorithm >= imgExportDescriptions.length )
			defaultImgExportAlgorithm = 0;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );
		
		gd.addChoice( "Type_of_image_fusion", fusionDescriptions, fusionDescriptions[ defaultFusionAlgorithm ] );
		gd.addChoice( "Bounding_Box defined by", boundingBoxDescriptions, boundingBoxDescriptions[ defaultBoundingBoxAlgorithm ] );
		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );
		
		gd.addMessage( "" );
		GUIHelper.addWebsite( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final int fusionAlgorithm = defaultFusionAlgorithm = gd.getNextChoiceIndex();
		final int boundingBoxAlgorithm = defaultBoundingBoxAlgorithm = gd.getNextChoiceIndex();
		final int imgExportAlgorithm = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		final Fusion fusion = staticFusionAlgorithms.get( fusionAlgorithm ).newInstance(
				result.getData(), 
				result.getAnglesToProcess(),
				result.getChannelsToProcess(),
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess() );

		final BoundingBox boundingBox = staticBoundingBoxAlgorithms.get( boundingBoxAlgorithm ).newInstance(
				result.getData(), 
				result.getAnglesToProcess(),
				result.getChannelsToProcess(),
				result.getIlluminationsToProcess(),
				result.getTimePointsToProcess() );

		final ImgExport imgExport = staticImgExportAlgorithms.get( imgExportAlgorithm ).newInstance();
		
		if ( !boundingBox.queryParameters( fusion, imgExport ) )
			return;
		
		if ( !fusion.queryParameters() )
			return;
		
		fusion.fuseData( boundingBox, imgExport );
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Image_Fusion().run( null );
	}
}
