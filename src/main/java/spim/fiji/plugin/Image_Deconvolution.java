package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.fusion.DeconvolutionGUI;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.process.deconvolution.DeconView;
import spim.process.deconvolution.DeconViewPSF.PSFTYPE;
import spim.process.deconvolution.DeconViews;
import spim.process.deconvolution.MultiViewDeconvolution;
import spim.process.deconvolution.iteration.ComputeBlockThreadFactory;
import spim.process.deconvolution.iteration.PsiInitialization;
import spim.process.deconvolution.iteration.PsiInitialization.PsiInit;
import spim.process.deconvolution.iteration.PsiInitializationAvgApprox;
import spim.process.deconvolution.iteration.PsiInitializationAvgPrecise;
import spim.process.deconvolution.iteration.PsiInitializationBlurredFused;
import spim.process.deconvolution.util.PSFPreparation;
import spim.process.deconvolution.util.ProcessInputImages;
import spim.process.export.ImgExport;
import spim.process.fusion.FusionTools;
import spim.process.fusion.FusionTools.ImgDataType;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Deconvolution implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset (Multiview) Deconvolution", true, true, true, true, true ) )
			return;

		deconvolve( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean deconvolve(
			final SpimData2 spimData,
			final List< ViewId > views )
	{
		return deconvolve( spimData, views, DeconViews.createExecutorService() );
	}

	public static boolean deconvolve(
			final SpimData2 spimData,
			final List< ViewId > viewList,
			final ExecutorService service )
	{
		final DeconvolutionGUI decon = new DeconvolutionGUI( spimData, viewList, service );

		if ( !decon.queryDetails() )
			return false;

		final List< Group< ViewDescription > > deconGroups = decon.getFusionGroups();
		int i = 0;

		for ( final Group< ViewDescription > deconGroup : deconGroups )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Deconvolving group " + (++i) + "/" + deconGroups.size() + " (group=" + deconGroup + ")" );

			final List< Group< ViewDescription > > deconSubGroups = decon.getDeconvolutionGrouping( deconGroup );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): This group contains the following 'virtual views':" );

			for ( final Group< ViewDescription > subGroup : deconSubGroups )
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): " + subGroup );

			final Interval bb = decon.getBoundingBox();
			final double downsampling = decon.getDownsampling();

			final ProcessInputImages< ViewDescription > fusion = new ProcessInputImages<>(
					spimData,
					deconSubGroups,
					bb,
					downsampling,
					true,
					FusionTools.defaultBlendingRange,
					FusionTools.defaultBlendingBorder,
					true,
					decon.getBlendingRange(),
					decon.getBlendingBorder() / ( Double.isNaN( downsampling ) ? 1.0f : (float)downsampling ) );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusion of 'virtual views' " );
			fusion.fuseGroups();

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Normalizing weights ... " );
			fusion.normalizeWeights( decon.getOSEMSpeedUp(), true, 0.1f, 0.05f );

			if ( decon.getInputImgCacheType() == ImgDataType.CACHED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Caching fused input images ... " );
				fusion.cacheImages();
			}
			else if ( decon.getInputImgCacheType() == ImgDataType.PRECOMPUTED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Precomputing fused input images ... " );
				fusion.copyImages( decon.getCopyFactory() );
			}

			if ( decon.getWeightCacheType() == ImgDataType.CACHED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Caching weight images ... " );
				fusion.cacheNormalizedWeights();
				fusion.cacheUnnormalizedWeights();
			}
			if ( decon.getWeightCacheType() == ImgDataType.PRECOMPUTED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Precomputing weight images ... " );
				// we cache the unnormalized ones so the copying is efficient
				fusion.cacheUnnormalizedWeights();
				fusion.copyNormalizedWeights( decon.getCopyFactory() );
			}

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Grouping, and transforming PSF's " );

			final HashMap< Group< ViewDescription >, ArrayImg< FloatType, ? > > psfs =
					PSFPreparation.loadGroupTransformPSFs( spimData.getPointSpreadFunctions(), fusion );

			final ImgFactory< FloatType > psiFactory = decon.getPsiFactory();
			final int[] blockSize = decon.getComputeBlockSize();
			final int numIterations = decon.getNumIterations();
			final PSFTYPE psfType = decon.getPSFType();
			final PsiInit psiInitType = decon.getPsiInitType();
			final boolean filterBlocksForContent = decon.testEmptyBlocks();
			final boolean debug = decon.getDebugMode();
			final int debugInterval = decon.getDebugInterval();
			final ComputeBlockThreadFactory cptf = decon.getComputeBlockThreadFactory();

			try
			{
				final PsiInitialization psiInit;

				if ( psiInitType == PsiInit.FUSED_BLURRED )
					psiInit = new PsiInitializationBlurredFused();
				else if ( psiInitType == PsiInit.AVG )
					psiInit = new PsiInitializationAvgPrecise();
				else
					psiInit = new PsiInitializationAvgApprox();

				if ( filterBlocksForContent )
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution and testing for empty ones that can be dropped." );
				else
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution." );

				final ArrayList< DeconView > deconViews = new ArrayList<>();

				for ( final Group< ViewDescription > virtualView : fusion.getGroups() )
				{
					deconViews.add( new DeconView(
							service,
							fusion.getImages().get( virtualView ),
							fusion.getNormalizedWeights().get( virtualView ),
							psfs.get( virtualView ),
							psfType,
							blockSize,
							cptf.numParallelBlocks(),
							filterBlocksForContent ) );

					if ( deconViews.get( deconViews.size() - 1 ).getNumBlocks() <= 0 )
						return false;
				}

				final DeconViews views = new DeconViews( deconViews, service );

				final MultiViewDeconvolution mvDecon = new MultiViewDeconvolution( views, numIterations, psiInit, cptf, psiFactory );
				mvDecon.setDebug( debug );
				mvDecon.setDebugInterval( debugInterval );
				mvDecon.runIterations();

				if ( !export( mvDecon.getPSI(), decon, deconGroup ) )
				{
					IOFunctions.println( "ERROR exporting the image using '" + decon.getExporter().getClass().getSimpleName() + "'" );
					return false;
				}
			}
			catch ( OutOfMemoryError oome )
			{
				IOFunctions.println( "Out of memory.  Use smaller blocks, virtual/cached inputs, and check \"Edit > Options > Memory & Threads\"" );
				IOFunctions.println( "Your java instance has access to a total amount of RAM of: " + Runtime.getRuntime().maxMemory() / (1024*1024) );

				service.shutdown();

				return false;
			}
		}

		service.shutdown();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): DONE." );

		return true;
	}

	protected static boolean export(
			final RandomAccessibleInterval< FloatType > output,
			final DeconvolutionGUI fusion,
			final Group< ViewDescription > group )
	{
		final ImgExport exporter = fusion.getExporter();

		exporter.queryParameters( fusion );

		final String title = Image_Fusion.getTitle( fusion.getSplittingType(), group );

		return exporter.exportImage( output, fusion.getBoundingBox(), fusion.getDownsampling(), title, group );
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Deconvolution().run( null );
	}
}
