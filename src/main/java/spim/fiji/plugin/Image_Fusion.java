package spim.fiji.plugin;

import java.util.Date;
import java.util.List;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.FusionGUI;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.process.export.ImgExport;
import spim.process.fusion.FusionTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Fusion implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		fuse( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean fuse(
			final SpimData2 spimData,
			final List< ViewId > views )
	{
		final FusionGUI fusion = new FusionGUI( spimData, views );

		if ( !fusion.queryDetails() )
			return false;

		final List< Group< ViewDescription > > groups = fusion.getFusionGroups();
		int i = 0;

		for ( final Group< ViewDescription > group : groups )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing group " + (++i) + "/" + groups.size() + " (group=" + group + ")" );

			double downsampling = fusion.getDownsampling();

			if ( downsampling == 1.0 )
				downsampling = Double.NaN;

			final RandomAccessibleInterval< FloatType > virtual =
				FusionTools.fuseVirtual(
					spimData,
					views,
					fusion.useBlending(),
					fusion.useContentBased(),
					fusion.getInterpolation(),
					fusion.getBoundingBox(),
					downsampling );

			if ( fusion.getPixelType() == 1 ) // 16 bit
			{
				if ( !cacheAndExport(
						new ConvertedRandomAccessibleInterval< FloatType, UnsignedShortType >(
								virtual, new RealUnsignedShortConverter<>(), new UnsignedShortType() ),
						new UnsignedShortType(), fusion, group ) )
					return false;
			}
			else
			{
				if ( !cacheAndExport( virtual, new FloatType(), fusion, group ) )
					return false;
			}
		}

		return true;
	}

	protected static < T extends RealType< T > & NativeType< T > > boolean cacheAndExport(
			final RandomAccessibleInterval< T > output,
			final T type,
			final FusionGUI fusion,
			final Group< ViewDescription > group )
	{
		final ImgExport exporter = fusion.getExporter();

		exporter.queryParameters( fusion );

		final RandomAccessibleInterval< T > processedOutput;

		if ( fusion.getCacheType() == 0 ) // Virtual
			processedOutput = output;
		else if ( fusion.getCacheType() == 1 ) // Cached
			processedOutput = FusionTools.cacheRandomAccessibleInterval( output, FusionGUI.maxCacheSize, type, FusionGUI.cellDim );
		else // Precomputed
			processedOutput = FusionTools.copyImg( output, new ImagePlusImgFactory< T >(), type, true );

		return exporter.exportImage( processedOutput, fusion.getBoundingBox(), fusion.getDownsampling(), group.toString(), group );
	}

	public static void fuseGroup(
			final SpimData2 spimData,
			final Group< ViewDescription > group
			)
	{
		
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Fusion().run( null );
	}
}
