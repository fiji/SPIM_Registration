package spim.headless.definedataset;

import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.StackImgLoader;
import spim.fiji.spimdata.imgloaders.StackImgLoaderLOCI;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import java.io.File;
import java.util.Properties;

/**
 * DataSet definition for LOCI
 */
public class StackListLOCI extends StackList
{
	public static SpimData2 createDataset( final String file, final StackListParameters params )
	{
		ImgFactory< ? extends NativeType< ? > > imgFactory = null;

		switch( params.container )
		{
			case ArrayImg: imgFactory = new ArrayImgFactory< FloatType >();
				break;
			case CellImg: imgFactory = new CellImgFactory< FloatType >( 256 );
				break;
		}

		// assemble timepints, viewsetups, missingviews and the imgloader
		final SequenceDescription sequenceDescription = createSequenceDescription( params.timepoints, params.channels, params.illuminations, params.angles, loadCalibration(new File(file)) );
		final ImgLoader< UnsignedShortType > imgLoader = createAndInitImgLoader( ".", new File( params.directory ), imgFactory, sequenceDescription, params );
		sequenceDescription.setImgLoader( imgLoader );

		// get the minimal resolution of all calibrations
		final double minResolution = Apply_Transformation.assembleAllMetaData(
				sequenceDescription,
				sequenceDescription.getViewDescriptions().values() );

		IOFunctions.println( "Minimal resolution in all dimensions over all views is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );

		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );

		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( new File( params.directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		return spimData;
	}

	static StackImgLoader createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory, final SequenceDescription sequenceDescription, final StackListParameters params )
	{
		int hasMultipleAngles = 0, hasMultipleTimePoints = 0, hasMultipleChannels = 0, hasMultipleIlluminations = 0;

		switch ( params.multipleAngleOption )
		{
			case OneAngle: hasMultipleAngles = 0; break;
			case OneFilePerAngle: hasMultipleAngles = 1; break;
			case AllAnglesInOneFile: hasMultipleAngles = 2; break;
		}
		switch ( params.multipleTimePointOption )
		{
			case OneTimePoint: hasMultipleTimePoints = 0; break;
			case OneFilePerTimePoint: hasMultipleTimePoints = 1; break;
			case AllTimePointsInOneFile: hasMultipleTimePoints = 2; break;
		}
		switch ( params.multipleChannelOption )
		{
			case OneChannel: hasMultipleChannels = 0; break;
			case OneFilePerChannel: hasMultipleChannels = 1; break;
			case AllChannelsInOneFile: hasMultipleChannels = 2; break;
		}
		switch ( params.multipleIlluminationOption )
		{
			case OneIllumination: hasMultipleIlluminations = 0; break;
			case OneFilePerIllumination: hasMultipleIlluminations = 1; break;
			case AllIlluminationsInOneFile: hasMultipleIlluminations = 2; break;
		}

		String fileNamePattern = assembleDefaultPattern( hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations, hasMultipleAngles );

		return new StackImgLoaderLOCI(
				new File( basePath.getAbsolutePath(), path ),
				fileNamePattern, imgFactory,
				hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations, hasMultipleAngles,
				sequenceDescription );
	}

	static Calibration loadCalibration( final File file )
	{
		IOFunctions.println( "Loading calibration for: " + file.getAbsolutePath() );

		if ( !file.exists() )
		{
			IOFunctions.println( "File '" + file + "' does not exist. Stopping." );
			return null;
		}

		final spim.fiji.spimdata.imgloaders.Calibration cal = StackImgLoaderLOCI.loadMetaData( file );

		if ( cal == null )
			return null;

		final double calX = cal.getCalX();
		final double calY = cal.getCalY();
		final double calZ = cal.getCalZ();

		return new Calibration( calX, calY, calZ );
	}
}
