package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.fusion.BoundingBox;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.process.fusion.boundingbox.AutomaticBoundingBox;
import spim.process.fusion.boundingbox.AutomaticReorientation;
import spim.process.fusion.boundingbox.ManualBoundingBox;
import spim.process.fusion.deconvolution.EfficientBayesianBased;
import spim.process.fusion.export.AppendSpimData2;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.export.ExportSpimData2TIFF;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.Save3dTIFF;
import spim.process.fusion.weightedavg.WeightedAverageFusion;
import spim.process.fusion.weightedavg.WeightedAverageFusion.WeightedAvgFusionType;

public class Image_Fusion implements PlugIn
{
	public static ArrayList< Fusion > staticFusionAlgorithms = new ArrayList< Fusion >();
	public static int defaultFusionAlgorithm = 1;

	public static ArrayList< BoundingBox > staticBoundingBoxAlgorithms = new ArrayList< BoundingBox >();
	public static int defaultBoundingBoxAlgorithm = 0;

	public static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public static int defaultImgExportAlgorithm = 0;

	static
	{
		IOFunctions.printIJLog = true;
		staticFusionAlgorithms.add( new EfficientBayesianBased( null, null, null, null, null ) );
		staticFusionAlgorithms.add( new WeightedAverageFusion( null, null, null, null, null, WeightedAvgFusionType.FUSEDATA ) );
		staticFusionAlgorithms.add( new WeightedAverageFusion( null, null, null, null, null, WeightedAvgFusionType.INDEPENDENT ) );
		
		staticBoundingBoxAlgorithms.add( new ManualBoundingBox( null, null ) );
		staticBoundingBoxAlgorithms.add( new AutomaticReorientation( null, null ) );
		staticBoundingBoxAlgorithms.add( new AutomaticBoundingBox( null, null ) );
		
		staticImgExportAlgorithms.add( new DisplayImage() );
		staticImgExportAlgorithms.add( new Save3dTIFF( null ) );
		staticImgExportAlgorithms.add( new ExportSpimData2TIFF() );
		staticImgExportAlgorithms.add( new AppendSpimData2() );
	}

	@Override
	public void run( final String arg )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "image fusion", true, true, true, true ) )
			return;

		fuse(
			result.getData(),
			SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
			result.getClusterExtension(),
			result.getXMLFileName(),
			true );
	}

	public boolean fuse(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
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
		gd.addChoice( "Bounding_Box", boundingBoxDescriptions, boundingBoxDescriptions[ defaultBoundingBoxAlgorithm ] );
		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );

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
			return false;

		final int fusionAlgorithm = defaultFusionAlgorithm = gd.getNextChoiceIndex();
		final int boundingBoxAlgorithm = defaultBoundingBoxAlgorithm = gd.getNextChoiceIndex();
		final int imgExportAlgorithm = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		final Fusion fusion = staticFusionAlgorithms.get( fusionAlgorithm ).newInstance( data, viewIds );

		final BoundingBox boundingBox = staticBoundingBoxAlgorithms.get( boundingBoxAlgorithm ).newInstance( data, viewIds );

		final ImgExport imgExport = staticImgExportAlgorithms.get( imgExportAlgorithm ).newInstance();

		if ( !boundingBox.queryParameters( fusion, imgExport ) )
			return false;

		if ( !fusion.queryParameters() )
			return false;

		// set all the properties required for exporting as a new XML or as addition to an existing XML
		fusion.defineNewViewSetups( boundingBox );
		imgExport.setXMLData( fusion.getTimepointsToProcess(), fusion.getNewViewSetups() );
		
		if ( !imgExport.queryParameters( data ) )
			return false;

		imgExport.setClusterExt( clusterExtension );
		fusion.fuseData( boundingBox, imgExport );

		boundingBox.cleanUp( result );

		// save the XML if metadata was updated
		if ( data.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
		{
			boolean updated = false;
			
			try
			{
				for ( final ViewSetup setup : data.getSequenceDescription().getViewSetupsOrdered() )
					updated |= ( (AbstractImgLoader)data.getSequenceDescription().getImgLoader() ).updateXMLMetaData( setup, false );
			}
			catch( Exception e )
			{
				IOFunctions.println( "Failed to update metadata, this should not happen: " + e );
			}
			
			if ( updated && saveXML )
			{
				// save the xml
				final XmlIoSpimData2 io = new XmlIoSpimData2( clusterExtension );
				
				final String xml = new File( data.getBasePath(), new File( xmlFileName ).getName() ).getAbsolutePath();
				try 
				{
					io.save( data, xml );
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + io.lastFileName() + "' (image metadata was updated)." );
				}
				catch ( Exception e )
				{
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + io.lastFileName() + "': " + e );
					e.printStackTrace();
				}
			}
		}

		imgExport.finish();

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fusion finished." );
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Image_Fusion().run( null );
	}
}
