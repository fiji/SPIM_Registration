package spim.headless.deconvolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import ij.IJ;
import ij.ImageJ;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import spim.process.cuda.CUDADevice;
import spim.process.deconvolution.MVDeconFFT;
import spim.process.deconvolution.MVDeconFFT.PSFTYPE;
import spim.process.deconvolution.MVDeconInput;
import spim.process.deconvolution.MVDeconvolution;
import spim.process.deconvolution.ProcessInputImages;
import spim.process.export.DisplayImage;
import spim.process.fusion.FusionTools.ImgDataType;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval;
import spim.process.fusion.transformed.weightcombination.CombineWeightsRandomAccessibleInterval.CombineType;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.psf.PSFCombination;
import spim.process.psf.PSFExtraction;

public class TestDeconvolution
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;
		Collection< Group< ViewDescription > > groups = new ArrayList<>();

		// generate 4 views with 1000 corresponding beads, single timepoint
		spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );
		groups = Group.toGroups( spimData.getSequenceDescription().getViewDescriptions().values() );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );
		groups = selectViews( spimData.getSequenceDescription().getViewDescriptions().values() );

		testDeconvolution( spimData, groups, "My Bounding Box1" );
	}

	public static < V extends ViewId > void testDeconvolution(
			final SpimData2 spimData,
			final Collection< Group< V > > groups,
			final String bbTitle )
	{
		BoundingBox boundingBox = null;

		for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
			if ( bb.getTitle().equals( bbTitle ) )
				boundingBox = bb;

		if ( boundingBox == null )
		{
			System.out.println( "Bounding box '" + bbTitle + "' not found." );
			return;
		}

		IOFunctions.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		final double osemSpeedUp = 3.0;
		final double downsampling = 2.0;

		final ProcessInputImages< V > fusion = new ProcessInputImages<>(
				spimData,
				groups,
				boundingBox,
				downsampling,
				true,
				true );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Virtual Fusion of groups " );
		fusion.fuseGroups();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Normalizing weights ... " );
		fusion.normalizeWeights( osemSpeedUp, true, 0.1f, 0.05f );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): De-virtualization ... " );
		fusion.deVirtualizeImages( ImgDataType.CACHED );
		fusion.deVirtualizeUnnormalizedWeights( ImgDataType.CACHED );
		fusion.deVirtualizeNormalizedWeights( ImgDataType.CACHED );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Displaying " );
		displayDebug( fusion );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Loading, grouping, and transforming PSF's " );

		final HashMap< ViewId, PointSpreadFunction > rawPSFs = spimData.getPointSpreadFunctions().getPointSpreadFunctions();
		final HashMap< Group< V >, ArrayImg< FloatType, ? > > psfs = new HashMap<>();

		for ( final Group< V > group : fusion.getGroups() )
		{
			final ArrayList< Img< FloatType > > viewPsfs = new ArrayList<>();
	
			for ( final V view : group )
			{
				// load PSF
				final ArrayImg< FloatType, ? > psf = rawPSFs.get( view ).getPSFCopyArrayImg();

				// remember the normalized, transformed version (including downsampling!)
				viewPsfs.add( PSFExtraction.getTransformedNormalizedPSF( psf, fusion.getDownsampledModels().get( view ) ) );

				//DisplayImage.getImagePlusInstance( viewPsfs.get( viewPsfs.size() - 1 ), false, "psf " + Group.pvid( view ), 0, 1 ).show();
			}

			// compute the PSF for a group by averaging over the minimal size of all inputs
			// the sizes can be different if the transformations are not tranlations but affine.
			// they should, however, not differ significantly but only combine views that have
			// basically the same transformation (e.g. angle 0 vs 180, or before after correction of chromatic abberations)
			psfs.put( group, (ArrayImg< FloatType, ? >)PSFCombination.computeAverageImage( viewPsfs, new ArrayImgFactory< FloatType >(), false ) );

			//DisplayImage.getImagePlusInstance( psfs.get( group ), false, "psf " + group, 0, 1 ).show();
		}

		final Img< FloatType > avgPSF = PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );
		final Img< FloatType > maxAvgPSF = PSFCombination.computeMaxAverageTransformedPSF( psfs.values(), new ArrayImgFactory< FloatType >() );

		DisplayImage.getImagePlusInstance( Views.rotate( avgPSF, 0, 2 ), false, "avgPSF", 0, 1 ).show();
		DisplayImage.getImagePlusInstance( maxAvgPSF, false, "maxAvgPSF", 0, 1 ).show();

		SimpleMultiThreading.threadHaltUnClean();

		final ImgFactory< FloatType > factory = new ArrayImgFactory<>();
		final ImgFactory< FloatType > computeFactory = new ArrayImgFactory<>();
		final boolean useTikhonov = true;
		double lambda = 0.0006;

		/**
		 * 0 ... n == CUDA device i
		 */
		final ArrayList< CUDADevice > deviceList = new ArrayList<>();
		deviceList.add( new CUDADevice( -1, "CPU", Runtime.getRuntime().maxMemory(), Runtime.getRuntime().freeMemory(), 0, 0 ) );
		final boolean useCUDA = false;
		final boolean useBlocks = true;
		final int[] blockSize = new int[]{ 512, 512, 512 };
		final boolean saveMemory = false;
		final PSFTYPE iterationType = PSFTYPE.INDEPENDENT;
		final int numIterations = 10;

		try
		{
			final MVDeconInput deconvolutionData = new MVDeconInput( factory );
			
			IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Block & FFT image factory: " + computeFactory.getClass().getSimpleName() );

			for ( final Group< V > group : fusion.getGroups() )
			{
				// device list for CPU or CUDA processing
				final int[] devList = new int[ deviceList.size() ];
				for ( int i = 0; i < devList.length; ++i )
					devList[ i ] = deviceList.get( i ).getDeviceId();

				deconvolutionData.add( new MVDeconFFT(
						fusion.getImages().get( group ),
						fusion.getNormalizedWeights().get( group ),
						psfs.get( group ),
						computeFactory, devList, useBlocks, blockSize, saveMemory ) );
			}

			if ( !useTikhonov )
				lambda = 0;

			final Img< FloatType > deconvolved;

			try
			{
				deconvolved = new MVDeconvolution( deconvolutionData, iterationType, numIterations, lambda, osemSpeedUp, "deconvolved" ).getPsi();
			} 
			catch (IncompatibleTypeException e)
			{
				IOFunctions.println( "Failed to initialize deconvolution: " + e );
				e.printStackTrace();
				return;
			}

			// export the final image
			DisplayImage.getImagePlusInstance( deconvolved, true, "deconvolved", 0, 1 ).show();

		}
		catch ( OutOfMemoryError oome )
		{
			IJ.log( "Out of Memory" );
			IJ.error("Multi-View Registration", "Out of memory.  Check \"Edit > Options > Memory & Threads\"");
			return;
		}
	}

	public static < V extends ViewId > void displayDebug( final ProcessInputImages< V > fusion )
	{
		int i = 0;

		final ArrayList< RandomAccessibleInterval< FloatType > > allWeightsNormed = new ArrayList<>();

		for ( final Group< V > group : fusion.getGroups() )
		{
			System.out.println( "Img Instance: " + fusion.getImages().get( group ).getClass().getSimpleName() );
			System.out.println( "Raw Weight Instance: " + fusion.getUnnormalizedWeights().get( group ).getClass().getSimpleName() );
			System.out.println( "Normalized Weight Instance: " + fusion.getNormalizedWeights().get( group ).getClass().getSimpleName() );

			DisplayImage.getImagePlusInstance( fusion.getImages().get( group ), true, "g=" + i + " image", 0, 255 ).show();
			DisplayImage.getImagePlusInstance( fusion.getUnnormalizedWeights().get( group ), true, "g=" + i + " weightsRawDecon", 0, 2 ).show();
			DisplayImage.getImagePlusInstance( fusion.getNormalizedWeights().get( group ), true, "g=" + i + " weightsNormDecon", 0, 2 ).show();

			allWeightsNormed.add( fusion.getNormalizedWeights().get( group ) );

			// might not work if caching/copying is done before calling this method
			if ( FusedRandomAccessibleInterval.class.isInstance( fusion.getImages().get( group ) ) )
			{
				final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
				fusion.getDownsampledBoundingBox().dimensions( dim );
	
				DisplayImage.getImagePlusInstance(
						new CombineWeightsRandomAccessibleInterval(
								new FinalInterval( dim ),
								((FusedRandomAccessibleInterval)fusion.getImages().get( group )).getWeights(),
								CombineType.SUM ),
						true,
						"g=" + i + " weightsFusion",
						0, 1 ).show();
			}

			++i;
		}

		// display the sum of all normed weights
		final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
		fusion.getDownsampledBoundingBox().dimensions( dim );

		DisplayImage.getImagePlusInstance(
				new CombineWeightsRandomAccessibleInterval(
						new FinalInterval( dim ),
						allWeightsNormed,
						CombineType.SUM ),
				true,
				"sum of all normed weights",
				0, 1 ).show();
	}

	public static ArrayList< Group< ViewDescription > > selectViews( final Collection< ViewDescription > views )
	{
		final ArrayList< Group< ViewDescription > > groups = new ArrayList<>();

		final Group< ViewDescription > angle0and180 = new Group<>();
		final Group< ViewDescription > angle45and225 = new Group<>();
		final Group< ViewDescription > angle90and270 = new Group<>();

		for ( final ViewDescription vd : views )
		{
			final int angle = Integer.parseInt( vd.getViewSetup().getAngle().getName() );

			if ( angle == 0 || angle == 180 )
				angle0and180.getViews().add( vd );

			if ( angle == 45 || angle == 225 )
				angle45and225.getViews().add( vd );

			if ( angle == 90 || angle == 270 )
				angle90and270.getViews().add( vd );
		}

		groups.add( angle0and180 );
		groups.add( angle45and225 );
		groups.add( angle90and270 );

		System.out.println( "Views remaining:" );
		for ( final Group< ViewDescription > group : groups )
			System.out.println( group );

		return groups;
	}
}
