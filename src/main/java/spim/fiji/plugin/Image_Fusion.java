package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;
import spim.process.fusion.boundingbox.AutomaticBoundingBox;
import spim.process.fusion.boundingbox.AutomaticReorientation;
import spim.process.fusion.boundingbox.BigDataViewerBoundingBox;
import spim.process.fusion.boundingbox.BoundingBoxGUI;
import spim.process.fusion.boundingbox.PreDefinedBoundingBox;
import spim.process.fusion.deconvolution.EfficientBayesianBased;
import spim.process.fusion.export.AppendSpimData2;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.export.ExportSpimData2HDF5;
import spim.process.fusion.export.ExportSpimData2TIFF;
import spim.process.fusion.export.ImgExport;
import spim.process.fusion.export.Save3dTIFF;
import spim.process.fusion.weightedavg.WeightedAverageFusion;
import spim.process.fusion.weightedavg.WeightedAverageFusion.WeightedAvgFusionType;
import bdv.img.hdf5.Hdf5ImageLoader;

public class Image_Fusion implements PlugIn
{
	public final static ArrayList< Fusion > staticFusionAlgorithms = new ArrayList< Fusion >();
	public static int defaultFusionAlgorithm = 1;

	public final static ArrayList< BoundingBoxGUI > staticBoundingBoxAlgorithms = new ArrayList< BoundingBoxGUI >();
	public static int defaultBoundingBoxAlgorithm = -1;

	public final static ArrayList< ImgExport > staticImgExportAlgorithms = new ArrayList< ImgExport >();
	public static int defaultImgExportAlgorithm = 0;

	static
	{
		IOFunctions.printIJLog = true;
		staticFusionAlgorithms.add( new EfficientBayesianBased( null, null ) );
		staticFusionAlgorithms.add( new WeightedAverageFusion( null, null, WeightedAvgFusionType.FUSEDATA ) );
		staticFusionAlgorithms.add( new WeightedAverageFusion( null, null, WeightedAvgFusionType.INDEPENDENT ) );

		staticBoundingBoxAlgorithms.add( new BoundingBoxGUI( null, null ) );
		staticBoundingBoxAlgorithms.add( new BigDataViewerBoundingBox( null, null ) );
		staticBoundingBoxAlgorithms.add( new AutomaticReorientation( null, null ) );
		staticBoundingBoxAlgorithms.add( new AutomaticBoundingBox( null, null ) );
		staticBoundingBoxAlgorithms.add( new PreDefinedBoundingBox( null, null ) );

		staticImgExportAlgorithms.add( new DisplayImage() );
		staticImgExportAlgorithms.add( new Save3dTIFF( null ) );
		staticImgExportAlgorithms.add( new ExportSpimData2TIFF() );
		staticImgExportAlgorithms.add( new ExportSpimData2HDF5() );
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
			final List< ViewId > viewIds )
	{
		return fuse( data, viewIds, "", null, false );
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
		if ( defaultBoundingBoxAlgorithm < 0 )
		{
			if ( data.getBoundingBoxes().getBoundingBoxes().size() > 0 )
				defaultBoundingBoxAlgorithm = 4;
			else
				defaultBoundingBoxAlgorithm = 1;
		}
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
		final BoundingBoxGUI boundingBox = staticBoundingBoxAlgorithms.get( boundingBoxAlgorithm ).newInstance( data, viewIds );
		final ImgExport imgExport = staticImgExportAlgorithms.get( imgExportAlgorithm ).newInstance();

		if ( data.getSequenceDescription().getImgLoader() instanceof Hdf5ImageLoader )
			BoundingBoxGUI.defaultPixelType = 1; // set to 16 bit by default for hdf5

		if ( !boundingBox.queryParameters( fusion, imgExport ) )
			return false;

		if ( !fusion.queryParameters() )
			return false;

		// set all the properties required for exporting as a new XML or as addition to an existing XML
		fusion.defineNewViewSetups( boundingBox );
		imgExport.setXMLData( fusion.getTimepointsToProcess(), fusion.getNewViewSetups() );
		
		if ( !imgExport.queryParameters( data, boundingBox.getPixelType() == 1 ) )
			return false;

		// did anyone modify this SpimData object?
		boolean spimDataModified = false;

		fusion.fuseData( boundingBox, imgExport );

		spimDataModified |= boundingBox.cleanUp();

		// save the XML if metadata was updated
		if ( data.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
		{
			try
			{
				for ( final ViewSetup setup : data.getSequenceDescription().getViewSetupsOrdered() )
					spimDataModified |= ( (AbstractImgLoader)data.getSequenceDescription().getImgLoader() ).updateXMLMetaData( setup, false );
			}
			catch( Exception e )
			{
				IOFunctions.println( "Failed to update metadata, this should not happen: " + e );
			}
		}

		spimDataModified |= imgExport.finish();

		if ( spimDataModified && saveXML )
			SpimData2.saveXML( data, xmlFileName, clusterExtension );

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fusion finished." );

		return true;
	}

	public static void main( final String[] args )
	{
		LoadParseQueryXML.defaultXMLfilename = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";
		new ImageJ();
		new Image_Fusion().run( null );
	}
}
