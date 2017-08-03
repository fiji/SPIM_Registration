package spim.fiji.plugin;

import java.util.Date;
import java.util.List;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.converter.read.ConvertedRandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
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

		final List< Group< ViewDescription > > groups = FusionGUI.getFusionGroups( fusion.getSpimData(), fusion.getViews(), fusion.getSplittingType() );
		int i = 0;

		for ( final Group< ViewDescription > group : groups )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusing group " + (++i) + "/" + groups.size() + " (group=" + group + ")" );

			final double downsampling = fusion.getDownsampling();

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
				final double[] minmax = determineInputBitDepth( group, spimData, virtual );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Range for conversion to 16-bit, min=" + minmax[ 0 ] + ", max=" + minmax[ 1 ] );

				if ( !cacheAndExport(
						new ConvertedRandomAccessibleInterval< FloatType, UnsignedShortType >(
								virtual, new RealUnsignedShortConverter<>( minmax[ 0 ], minmax[ 1 ] ), new UnsignedShortType() ),
						new UnsignedShortType(), fusion, group, minmax ) )
					return false;
			}
			else
			{
				if ( !cacheAndExport( virtual, new FloatType(), fusion, group, null ) )
					return false;
			}
		}

		return true;
	}

	public static double[] determineInputBitDepth( final Group< ViewDescription > group, final SpimData2 spimData, final RandomAccessibleInterval< FloatType > virtual )
	{
		SetupImgLoader< ? > loader = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( group.iterator().next().getViewSetupId() );
		Object type = loader.getImageType();

		if ( UnsignedByteType.class.isInstance( type ) )
			return new double[] { 0, 255 };
		else if ( UnsignedShortType.class.isInstance( type ) )
			return new double[] { 0, 65535 };
		else
		{
			IOFunctions.println( "WARNING: You are saving a non-8/16 bit input as 16bit, have to manually determine min/max of the fused image." );

			final float[] minmax = FusionTools.minMax( virtual );
			return new double[]{ minmax[ 0 ], minmax[ 1 ] };
		}
	}

	protected static < T extends RealType< T > & NativeType< T > > boolean cacheAndExport(
			final RandomAccessibleInterval< T > output,
			final T type,
			final FusionGUI fusion,
			final Group< ViewDescription > group,
			final double[] minmax )
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

		final String title = getTitle( fusion, group );

		if ( minmax == null )
			return exporter.exportImage( processedOutput, fusion.getBoundingBox(), fusion.getDownsampling(), title, group );
		else
			return exporter.exportImage( processedOutput, fusion.getBoundingBox(), fusion.getDownsampling(), title, group, minmax[ 0 ], minmax[ 1 ] );
	}

	public static String getTitle( final FusionGUI fusion, final Group< ViewDescription > group )
	{
		String title;
		final ViewDescription vd0 = group.iterator().next();

		if ( fusion.getSplittingType() == 0 ) // "Each timepoint & channel"
			title = "fused_tp_" + vd0.getTimePointId() + "_ch_" + vd0.getViewSetup().getChannel().getId();
		else if ( fusion.getSplittingType() == 1 ) // "Each timepoint, channel & illumination"
			title = "fused_tp_" + vd0.getTimePointId() + "_ch_" + vd0.getViewSetup().getChannel().getId() + "_illum_" + vd0.getViewSetup().getIllumination().getId();
		else if ( fusion.getSplittingType() == 2 ) // "All views together"
			title = "fused";
		else // "All views"
			title = "fused_tp_" + vd0.getTimePointId() + "_vs_" + vd0.getViewSetupId();

		return title;
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
