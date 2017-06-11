package spim.headless.deconvolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;

import ij.ImageJ;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import simulation.imgloader.SimulatedBeadsImgLoader;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.deconvolution.ProcessInputImages;
import spim.process.deconvolution.ProcessInputImages.ImgDataType;
import spim.process.fusion.export.DisplayImage;
import spim.process.fusion.transformed.FusedRandomAccessibleInterval;
import spim.process.fusion.transformed.FusedWeightsRandomAccessibleInterval;
import spim.process.interestpointregistration.TransformationTools;
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

		testDeconvolution( spimData, groups, "My Bounding Box" );
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

		System.out.println( BoundingBox.getBoundingBoxDescription( boundingBox ) );

		final ProcessInputImages< V > fusion = new ProcessInputImages<>(
				spimData,
				groups,
				boundingBox,
				1.0,
				true,
				true );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Virtual Fusion of groups " );
		fusion.fuseGroups();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): De-virtualization ... " );
		fusion.deVirtualizeImages( ImgDataType.CACHED );
		fusion.deVirtualizeUnnormalizedWeights( ImgDataType.CACHED );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Normalizing weights ... " );
		fusion.normalizeWeights( 1.0, true, 0.1f, 0.05f );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Displaying " );
		//displayDebug( fusion );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Extracting PSF's " );

		final HashMap< Group< V >, Img< FloatType > > psfs = new HashMap<>();

		for ( final Group< V > group : fusion.getGroups() )
		{
			final ArrayList< Img< FloatType > > viewPsfs = new ArrayList<>();
	
			for ( final V view : group )
			{
				final PSFExtraction< FloatType > extractPSF = new PSFExtraction<>( spimData, view, "beads", true, new FloatType(), new long[]{ 19, 19, 25 } );

				// remove min projections in all dimensions to remove background
				extractPSF.removeMinProjections();

				// remember the normalized, transformed version (including downsampling!)
				viewPsfs.add( extractPSF.getTransformedNormalizedPSF( fusion.getDownsampledModels().get( view ) ) );
			}

			// compute the PSF for a group by averaging over the minimal size of all inputs
			// the sizes can be different if the transformations are not tranlations but affine.
			// they should, however, not differ significantly but only combine views that have
			// basically the same transformation (e.g. angle 0 vs 180, or before after correction of chromatic abberations)
			psfs.put( group, PSFCombination.computeAverageImage( viewPsfs, new ArrayImgFactory< FloatType >(), false ) );

			//DisplayImage.getImagePlusInstance( psfs.get( group ), false, "psf", 0, 1 ).show();
		}

		final Img< FloatType > avgPSF = PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );
		final Img< FloatType > maxAvgPSF = PSFCombination.computeMaxAverageTransformedPSF( psfs.values(), new ArrayImgFactory< FloatType >() );

		DisplayImage.getImagePlusInstance( Views.rotate( avgPSF, 0, 2 ), false, "avgPSF", 0, 1 ).show();
		DisplayImage.getImagePlusInstance( maxAvgPSF, false, "maxAvgPSF", 0, 1 ).show();
	}

	public static < V extends ViewId > void displayDebug( final ProcessInputImages< V > fusion )
	{
		int i = 0;

		for ( final Group< V > group : fusion.getGroups() )
		{
			System.out.println( "Img Instance: " + fusion.getImages().get( group ).getClass().getSimpleName() );
			System.out.println( "Raw Weight Instance: " + fusion.getUnnormalizedWeights().get( group ).getClass().getSimpleName() );
			System.out.println( "Normalized Weight Instance: " + fusion.getNormalizedWeights().get( group ).getClass().getSimpleName() );

			DisplayImage.getImagePlusInstance( fusion.getImages().get( group ), true, "g=" + i + " image", 0, 255 ).show();
			DisplayImage.getImagePlusInstance( fusion.getUnnormalizedWeights().get( group ), true, "g=" + i + " weightsRawDecon", 0, 2 ).show();
			DisplayImage.getImagePlusInstance( fusion.getNormalizedWeights().get( group ), true, "g=" + i + " weightsNormDecon", 0, 2 ).show();

			if ( FusedRandomAccessibleInterval.class.isInstance( fusion.getImages().get( group ) ) )
			{
				final long[] dim = new long[ fusion.getDownsampledBoundingBox().numDimensions() ];
				fusion.getDownsampledBoundingBox().dimensions( dim );
	
				DisplayImage.getImagePlusInstance(
						new FusedWeightsRandomAccessibleInterval(
								new FinalInterval( dim ),
								((FusedRandomAccessibleInterval)fusion.getImages().get( group )).getWeights() ),
						true,
						"g=" + i + " weightsFusion",
						0, 1 ).show();
			}

			++i;
		}
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
