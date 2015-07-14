package spim.fiji.datasetmanager;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import loci.formats.in.SlideBook6Reader;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;

import javax.media.j3d.Transform3D;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.SlideBook6ImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class SlideBook6 implements MultiViewDatasetDefinition
{
	public static String[] rotAxes = new String[] { "X-Axis", "Y-Axis", "Z-Axis" };

	public static String defaultFirstFile = "";
	public static boolean defaultModifyCal = false;
	public static boolean defaultRotAxis = false;
	public static boolean defaultApplyRotAxis = true;

	@Override
	public String getTitle() { return "Slidebook6 Dataset"; }

	@Override
	public String getExtendedDescription()
	{
		return "This datset definition supports files saved by SlideBook6";
	}

	@Override
	public SpimData2 createDataset()
	{
		final File sldFile = querySldFile();

		if ( sldFile == null )
			return null;

		try {
			SlideBook6Reader reader = new SlideBook6Reader();

			if (!reader.isThisType( sldFile.getPath())) {
				IOFunctions.println( "Wrong file type (SLD)'" + sldFile.getAbsolutePath() + "'" );
				reader.close();
				return null;
			}
			reader.openFile(sldFile.getPath());

			if ( !showDialogs(reader) ) {
				reader.close();
				return null;
			}

			final String directory = sldFile.getParent();
			final ImgFactory< ? extends NativeType< ? > > imgFactory = new ArrayImgFactory< FloatType >();

			// assemble timepints, viewsetups, missingviews and the imgloader
			final TimePoints timepoints = this.createTimePoints( reader );
			final ArrayList< ViewSetup > setups = this.createViewSetups( reader );
			final MissingViews missingViews = null;

			// instantiate the sequence descriptions
			final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, missingViews );
			final ImgLoader< UnsignedShortType > imgLoader = new SlideBook6ImgLoader( sldFile, imgFactory, sequenceDescription );
			sequenceDescription.setImgLoader( imgLoader );

			// get the minimal resolution of all calibrations, TODO: different views can have different calibrations?
			final int capture = 0;
			float zSpacing = 1;
			if (reader.getNumZPlanes(capture) > 1) {
				zSpacing = (float) Math.abs(reader.getZPosition(capture, 0, 1) - reader.getZPosition(capture, 0, 0));
			}
			final double minResolution = Math.min( reader.getVoxelSize(capture), zSpacing );

			IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
			IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );

			// create the initial view registrations (they are all the identity transform)
			final ViewRegistrations viewRegistrations = StackList.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );

			// create the initial view interest point object
			final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
			viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

			// finally create the SpimData itself based on the sequence description and the view registration
			final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

			// TODO: apply rotations, if known
			//if ( reader.applyAxis() )
			//	applyAxis( spimData );

			reader.closeFile();
			
			return spimData;		
		}
		catch (UnsatisfiedLinkError e) {
			IOFunctions.println( "UnsatisfiedLinkError: Missing SlideBook6Reader.dll Library." );
		}
		catch (Exception e) {
			IOFunctions.println( e );
		}
		return null;
	}

	public static void applyAxis( final SpimData data )
	{
		ViewRegistrations viewRegistrations = data.getViewRegistrations();
		for ( final ViewDescription vd : data.getSequenceDescription().getViewDescriptions().values() )
		{
			if ( vd.isPresent() )
			{
				final Angle a = vd.getViewSetup().getAngle();

				if ( a.hasRotation() )
				{
					final ViewRegistration vr = viewRegistrations.getViewRegistration( vd );

					final Dimensions dim = vd.getViewSetup().getSize();

					AffineTransform3D model = new AffineTransform3D();
					model.set(
							1, 0, 0, -dim.dimension( 0 )/2,
							0, 1, 0, -dim.dimension( 1 )/2,
							0, 0, 1, -dim.dimension( 2 )/2 );
					ViewTransform vt = new ViewTransformAffine( "Center view", model );
					vr.preconcatenateTransform( vt );

					final double[] tmp = new double[ 16 ];
					final double[] axis = a.getRotationAxis();
					final double degrees = a.getRotationAngleDegrees();
					final Transform3D t = new Transform3D();
					final String d;

					if ( axis[ 0 ] == 1 && axis[ 1 ] == 0 && axis[ 2 ] == 0 )
					{
						t.rotX( Math.toRadians( degrees ) );
						d = "Rotation around x-axis by " + degrees + " degrees";
					}
					else if ( axis[ 0 ] == 0 && axis[ 1 ] == 1 && axis[ 2 ] == 0 )
					{
						t.rotY( Math.toRadians( degrees ) );
						d = "Rotation around y-axis by " + degrees + " degrees";
					}
					else if ( axis[ 0 ] == 1 && axis[ 0 ] == 0 && axis[ 2 ] == 1 )
					{
						t.rotZ( Math.toRadians( degrees ) );
						d = "Rotation around z-axis by " + degrees + " degrees";
					}
					else
					{
						IOFunctions.println( "Arbitrary rotation axis not supported yet." );
						continue;
					}

					t.get( tmp );

					model = new AffineTransform3D();
					model.set( tmp[ 0 ], tmp[ 1 ], tmp[ 2 ], tmp[ 3 ],
							tmp[ 4 ], tmp[ 5 ], tmp[ 6 ], tmp[ 7 ],
							tmp[ 8 ], tmp[ 9 ], tmp[ 10 ], tmp[ 11 ] );

					vt = new ViewTransformAffine( d, model );
					vr.preconcatenateTransform( vt );
					vr.updateModel();
				}
			}
		}
	}

	/**
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups( final SlideBook6Reader meta )
	{
		// only one channel per view (SlideBook channels are diSPIM angles, there should have only two)
		// TODO: define multiple channels
		final Channel c  = new Channel( 0, meta.getChannelName( 0, 0 ) );

		// TODO: define multiple illuminations
		final Illumination i = new Illumination( 0, String.valueOf( 0 ) ) ;
		
		// TODO: query rotation angle of each SlideBook channel
		final ArrayList< Angle > angles = new ArrayList< Angle >();
		final Angle angle0 = new Angle( 0, "0");
		// TODO: set to known rotation
		// angle0.setRotation(axis, 0);
		angles.add( angle0 );

		final Angle angle180 = new Angle( 1, "0");
		// TODO: set to known rotation
		// angle180.setRotation(axis, 180);
		angles.add( angle180 );

		// TODO: process more than one image in SLD file
		final int capture = 0;
		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Angle a : angles )
		{
			int channel = a.getId();
			// TODO: make sure a < getNumCaptures()
			float voxelSizeUm = meta.getVoxelSize(capture);
			float zSpacing = 1;
			if (meta.getNumZPlanes(capture) > 1) {
				zSpacing = (float) Math.abs(meta.getZPosition(capture, 0, 1) - meta.getZPosition(capture, 0, 0));
			}

			final VoxelDimensions voxelSize = new FinalVoxelDimensions( "um", voxelSizeUm, voxelSizeUm, zSpacing );
			final Dimensions dim = new FinalDimensions( new long[]{ meta.getNumXColumns(capture), meta.getNumYRows(capture), meta.getNumZPlanes(capture) } );
			viewSetups.add( new ViewSetup( viewSetups.size(), a.getName(), dim, voxelSize, c, a, i ) );
		}

		return viewSetups;
	}

	/**
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints( final SlideBook6Reader meta )
	{
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.getNumTimepoints(0); ++t )
			timepoints.add( new TimePoint( t ) );

		return new TimePoints( timepoints );
	}

	protected boolean showDialogs(final SlideBook6Reader meta)
	{
		GenericDialog gd = new GenericDialog( "SlideBook6 diSPIM Properties" );

		gd.addMessage( "Angles (" + meta.getNumCaptures() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int a = 0; a < meta.getNumCaptures(); ++a )
			gd.addStringField( "Angle_" + (a+1) + ":", String.valueOf( "0" ) ); // meta.rotationAngle( a )

		gd.addMessage( "Channels (" + meta.getNumChannels(0) + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int c = 0; c < meta.getNumChannels(0); ++c )
			gd.addStringField( "Channel_" + (c+1) + ":", meta.getChannelName(0, c ) );

		if ( meta.getNumPositions(0) > 1 )
		{
			IOFunctions.println( "WARNING: " + meta.getNumPositions(0) + " stage positions detected. This will be imported as different illumination directions." );
			gd.addMessage( "" );
		}

		gd.addMessage( "Timepoints (" + meta.getNumTimepoints(0) + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		// TODO: make sure a < getNumCaptures()
		float voxelSize = meta.getVoxelSize(0);
		float zSpacing = 1;
		if (meta.getNumZPlanes(0) > 1) {
			zSpacing = (float) Math.abs(meta.getZPosition(0, 0, 1) - meta.getZPosition(0, 0, 0));
		}	

		gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addCheckbox( "Modify_calibration", defaultModifyCal );
		gd.addMessage(
				"Pixel Distance X: " + voxelSize + " " + "um" + "\n" +
						"Pixel Distance Y: " + voxelSize + " " + "um" + "\n" +
						"Pixel Distance Z: " + zSpacing + " " + "um" + "\n" );

		gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );
		gd.addCheckbox( "Modify_rotation_axis", defaultRotAxis );
		gd.addCheckbox( "Apply_rotation_to_dataset", defaultApplyRotAxis );

		gd.addMessage(
				"Rotation axis: " + "Rot0" + " axis\n" +
						"Pixel type: " + "UInt16",
						new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		GUIHelper.addScrollBars( gd );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final ArrayList< String > angles = new ArrayList< String >();
		for ( int a = 0; a < meta.getNumCaptures(); ++a )
			angles.add( gd.getNextString() );
		// meta.setAngleNames( angles );

		final ArrayList< String > channels = new ArrayList< String >();
		for ( int c = 0; c < meta.getNumChannels(0); ++c )
			channels.add( gd.getNextString() );
		// meta.setChannelNames( channels );

		final boolean modifyCal = defaultModifyCal = gd.getNextBoolean();
		final boolean modifyAxis = defaultRotAxis = gd.getNextBoolean();
		//meta.setApplyAxis( defaultApplyRotAxis = gd.getNextBoolean() );

		if ( modifyAxis || modifyCal )
		{
			gd = new GenericDialog( "Modify Meta Data" );

			if ( modifyCal )
			{
				gd.addNumericField( "Pixel_distance_x", voxelSize, 5 );
				gd.addNumericField( "Pixel_distance_y", voxelSize, 5 );
				gd.addNumericField( "Pixel_distance_z", zSpacing, 5 );
				gd.addStringField( "Pixel_unit", "um" );
			}

			if ( modifyAxis )
			{
				//if ( meta.rotationAxisIndex() < 0 )
				//	gd.addChoice( "Rotation_around", rotAxes, rotAxes[ 0 ] );
				//else
				//	gd.addChoice( "Rotation_around", rotAxes, rotAxes[ meta.rotationAxisIndex() ] );
			}

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			if ( modifyCal )
			{
				//meta.setCalX( gd.getNextNumber() );
				//meta.setCalY( gd.getNextNumber() );
				//meta.setCalZ( gd.getNextNumber() );
				//meta.setCalUnit( gd.getNextString() );
			}

			if ( modifyAxis )
			{
				//int axis = gd.getNextChoiceIndex();

				//if ( axis == 0 )
				//meta.setRotAxis( new double[]{ 1, 0, 0 } );
				//else if ( axis == 1 )
				//meta.setRotAxis( new double[]{ 0, 1, 0 } );
				//else
				//meta.setRotAxis( new double[]{ 0, 0, 1 } );
			}
		}

		return true;
	}

	protected File querySldFile()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Define SlideBook6 diSPIM Dataset" );

		gd.addFileField( "SlideBook6 SLD file", defaultFirstFile, 50 );

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
	public SlideBook6 newInstance() { return new SlideBook6(); }

	public static void main( String[] args )
	{
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
		new SlideBook6().createDataset();
	}
}
