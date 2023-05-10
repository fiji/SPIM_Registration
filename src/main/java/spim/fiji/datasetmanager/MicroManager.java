/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2023 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.fiji.datasetmanager;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;

import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.MicroManagerImgLoader;
import spim.fiji.spimdata.imgloaders.MultipageTiffReader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class MicroManager implements MultiViewDatasetDefinition
{
	public static String[] rotAxes = new String[] { "X-Axis", "Y-Axis", "Z-Axis" };

	public static String defaultFirstFile = "";
	public static boolean defaultModifyCal = false;
	public static boolean defaultRotAxis = false;
	public static boolean defaultApplyRotAxis = true;

	@Override
	public String getTitle() { return "MicroManager diSPIM Dataset"; }

	@Override
	public String getExtendedDescription()
	{
		return "This datset definition supports files saved by MicroManager on the diSPIM.";
	}

	@Override
	public SpimData2 createDataset()
	{
		final File mmFile = queryMMFile();

		if ( mmFile == null )
			return null;

		MultipageTiffReader reader = null;

		try
		{
			reader = new MultipageTiffReader( mmFile );
		}
		catch ( IOException e )
		{
			IOFunctions.println( "Failed to analyze file '" + mmFile.getAbsolutePath() + "': " + e );
			return null;
		}

		if ( !showDialogs( reader ) )
			return null;

		final String directory = mmFile.getParent();

		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints timepoints = this.createTimePoints( reader );
		final ArrayList< ViewSetup > setups = this.createViewSetups( reader );
		final MissingViews missingViews = null;

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, missingViews );
		final ImgLoader imgLoader = new MicroManagerImgLoader( mmFile, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( reader.calX(), reader.calY() ), reader.calZ() );

		IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );
		
		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = StackList.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );
		
		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		if ( reader.applyAxis() )
			Apply_Transformation.applyAxis( spimData );

		try { reader.close(); } catch (IOException e) { IOFunctions.println( "Could not close file '" + mmFile.getAbsolutePath() + "': " + e ); }

		return spimData;
	}

	/**
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups( final MultipageTiffReader meta )
	{
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( int c = 0; c < meta.numChannels(); ++c )
			channels.add( new Channel( c, meta.channelName( c ) ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		for ( int i = 0; i < meta.numPositions(); ++i )
			illuminations.add( new Illumination( i, String.valueOf( i ) ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		for ( int a = 0; a < meta.numAngles(); ++a )
		{
			final Angle angle = new Angle( a, meta.rotationAngle( a ) );
			
			try
			{
				final double degrees = Double.parseDouble( meta.rotationAngle( a ) );
				double[] axis = meta.rotationAxis();

				if ( axis != null && !Double.isNaN( degrees ) &&  !Double.isInfinite( degrees ) )
					angle.setRotation( axis, degrees );
			}
			catch ( Exception e ) {};

			angles.add( angle );
		}

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( final Angle a : angles )
				{
					final VoxelDimensions voxelSize = new FinalVoxelDimensions( meta.calUnit(), meta.calX(), meta.calY(), meta.calZ() );
					final Dimensions dim = new FinalDimensions( new long[]{ meta.width(), meta.height(), meta.depth() } );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, dim, voxelSize, c, a, i ) );
				}

		return viewSetups;
	}

	/**
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints( MultipageTiffReader meta )
	{
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.numTimepoints(); ++t )
			timepoints.add( new TimePoint( t ) );

		return new TimePoints( timepoints );
	}

	protected boolean showDialogs( final MultipageTiffReader meta )
	{
		GenericDialog gd = new GenericDialog( "MicroManager diSPIM Properties" );

		gd.addMessage( "Angles (" + meta.numAngles() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int a = 0; a < meta.numAngles(); ++a )
			gd.addStringField( "Angle_" + (a+1) + ":", String.valueOf( meta.rotationAngle( a ) ) );

		gd.addMessage( "Channels (" + meta.numChannels() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int c = 0; c < meta.numChannels(); ++c )
			gd.addStringField( "Channel_" + (c+1) + ":", meta.channelName( c ) );

		if ( meta.numPositions() > 1 )
		{
			IOFunctions.println( "WARNING: " + meta.numPositions() + " stage positions detected. This will be imported as different illumination directions." );
			gd.addMessage( "" );
		}

		gd.addMessage( "Timepoints (" + meta.numTimepoints() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addCheckbox( "Modify_calibration", defaultModifyCal );
		gd.addMessage(
				"Pixel Distance X: " + meta.calX() + " " + meta.calUnit() + "\n" +
				"Pixel Distance Y: " + meta.calY() + " " + meta.calUnit() + "\n" +
				"Pixel Distance Z: " + meta.calZ() + " " + meta.calUnit() + "\n" );

		gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );
		gd.addCheckbox( "Modify_rotation_axis", defaultRotAxis );
		gd.addCheckbox( "Apply_rotation_to_dataset", defaultApplyRotAxis );

		gd.addMessage(
				"Rotation axis: " + meta.rotationAxisName() + " axis\n" +
				"Pixel type: " + meta.getPixelType(),
				new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		GUIHelper.addScrollBars( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		final ArrayList< String > angles = new ArrayList< String >();
		for ( int a = 0; a < meta.numAngles(); ++a )
			angles.add( gd.getNextString() );
		meta.setAngleNames( angles );

		final ArrayList< String > channels = new ArrayList< String >();
		for ( int c = 0; c < meta.numChannels(); ++c )
			channels.add( gd.getNextString() );
		meta.setChannelNames( channels );

		final boolean modifyCal = defaultModifyCal = gd.getNextBoolean();
		final boolean modifyAxis = defaultRotAxis = gd.getNextBoolean();
		meta.setApplyAxis( defaultApplyRotAxis = gd.getNextBoolean() );

		if ( modifyAxis || modifyCal )
		{
			gd = new GenericDialog( "Modify Meta Data" );

			if ( modifyCal )
			{
				gd.addNumericField( "Pixel_distance_x", meta.calX(), 5 );
				gd.addNumericField( "Pixel_distance_y", meta.calY(), 5 );
				gd.addNumericField( "Pixel_distance_z", meta.calZ(), 5 );
				gd.addStringField( "Pixel_unit", meta.calUnit() );
			}

			if ( modifyAxis )
			{
				if ( meta.rotationAxisIndex() < 0 )
					gd.addChoice( "Rotation_around", rotAxes, rotAxes[ 0 ] );
				else
					gd.addChoice( "Rotation_around", rotAxes, rotAxes[ meta.rotationAxisIndex() ] );
			}

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			if ( modifyCal )
			{
				meta.setCalX( gd.getNextNumber() );
				meta.setCalY( gd.getNextNumber() );
				meta.setCalZ( gd.getNextNumber() );
				meta.setCalUnit( gd.getNextString() );
			}

			if ( modifyAxis )
			{
				int axis = gd.getNextChoiceIndex();
	
				if ( axis == 0 )
					meta.setRotAxis( new double[]{ 1, 0, 0 } );
				else if ( axis == 1 )
					meta.setRotAxis( new double[]{ 0, 1, 0 } );
				else
					meta.setRotAxis( new double[]{ 0, 0, 1 } );
			}
		}

		return true;
	}

	protected File queryMMFile()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Define MicroMananger diSPIM Dataset" );
	
		gd.addFileField( "MicroManager OME TIFF file", defaultFirstFile, 50 );
	
		gd.showDialog();
	
		if ( gd.wasCanceled() )
			return null;
	
		final File firstFile = new File( defaultFirstFile = gd.getNextString() );
	
		if ( !firstFile.exists() )
		{
			IOFunctions.println( "File '" + firstFile.getAbsolutePath() + "' does not exist. Stopping" );
			return null;
		}
		else
		{
			IOFunctions.println( "Investigating file '" + firstFile.getAbsolutePath() + "'." );
			return firstFile;
		}
	}

	@Override
	public MicroManager newInstance() { return new MicroManager(); }

	public static void main( String[] args )
	{
		//defaultFirstFile = "/Volumes/My Passport/worm7/Track1(3).czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/130706_Aiptasia8.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/abe_Arabidopsis1.czi";
		defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/multiview.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
		new MicroManager().createDataset();
	}
}
