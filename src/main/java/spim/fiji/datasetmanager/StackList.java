package spim.fiji.datasetmanager;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.TimePointsPattern;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.NamePattern;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.StackImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public abstract class StackList implements MultiViewDatasetDefinition
{
	final public static char TIMEPOINT_PATTERN = 't';
	final public static char CHANNEL_PATTERN = 'c';
	final public static char ILLUMINATION_PATTERN = 'i';
	final public static char ANGLE_PATTERN = 'a';
	
	protected String[] dimensionChoiceTimePointsTrue = new String[] { "NO (one time-point)", "YES (one file per time-point)", "YES (all time-points in one file)" }; 
	protected String[] dimensionChoiceTimePointsFalse = new String[] { dimensionChoiceTimePointsTrue[ 0 ], dimensionChoiceTimePointsTrue[ 1 ] }; 

	protected String[] dimensionChoiceChannelsTrue = new String[] { "NO (one channel)", "YES (one file per channel)", "YES (all channels in one file)" }; 
	protected String[] dimensionChoiceChannelsFalse = new String[] { dimensionChoiceChannelsTrue[ 0 ], dimensionChoiceChannelsTrue[ 1 ] }; 

	protected String[] dimensionChoiceIlluminationsTrue = new String[] { "NO (one illumination direction)", "YES (one file per illumination direction)", "YES (all illumination directions in one file)" }; 
	protected String[] dimensionChoiceIlluminationsFalse = new String[] { dimensionChoiceIlluminationsTrue[ 0 ], dimensionChoiceIlluminationsTrue[ 1 ] }; 

	protected String[] dimensionChoiceAnglesTrue = new String[] { "NO (one angle)", "YES (one file per angle)", "YES (all angles in one file)" }; 
	protected String[] dimensionChoiceAnglesFalse = new String[] { dimensionChoiceAnglesTrue[ 0 ], dimensionChoiceAnglesTrue[ 1 ] }; 

	protected abstract int getDefaultMultipleAngles();
	protected abstract int getDefaultMultipleTimepoints();
	protected abstract int getDefaultMultipleChannels();
	protected abstract int getDefaultMultipleIlluminations();
	protected abstract void setDefaultMultipleAngles( int defaultAngleChoice );
	protected abstract void setDefaultMultipleTimepoints( int defaultTimepointChoice );
	protected abstract void setDefaultMultipleChannels( int defaultChannelChoice );
	protected abstract void setDefaultMultipleIlluminations( int defaultIlluminationChoice );
		
	protected int hasMultipleAngles, hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations;
	
	public static boolean showDebugFileNames = true;
	
	public static String defaultTimepoints = "18,19,30";
	public static String defaultChannels = "1,2";
	public static String defaultIlluminations = "0,1";
	public static String defaultAngles = "0-315:45";

	protected String timepoints, channels, illuminations, angles;
	protected ArrayList< String > timepointNameList, channelNameList, illuminationsNameList, angleNameList;
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	
	/* new int[]{ t, c, i, a } - indices */
	protected ArrayList< int[] > exceptionIds;
	
	protected String[] calibrationChoice1 = new String[]{
			"Same voxel-size for all views",
			"Different voxel-sizes for each view" };
	protected String[] calibrationChoice2 = new String[]{
			"Load voxel-size(s) from file(s)",
			"Load voxel-size(s) from file(s) and display for verification",
			"User define voxel-size(s)" };
	public static String[] imglib2Container = new String[]{ "ArrayImg (faster)", "CellImg (slower, larger files supported)" };

	public static int defaultContainer = 0;
	public ImgFactory< ? extends NativeType< ? > > imgFactory;
	public static int defaultCalibration1 = 0;
	public static int defaultCalibration2 = 1;
	public int calibration1, calibration2;
	
	protected HashMap< ViewSetupPrecursor, Calibration > calibrations = new HashMap< ViewSetupPrecursor, Calibration >();
	
	public static String defaultDirectory = "";
	public static String defaultFileNamePattern = null;

	protected String directory, fileNamePattern;
	
	protected abstract boolean supportsMultipleTimepointsPerFile();
	protected abstract boolean supportsMultipleChannelsPerFile();
	protected abstract boolean supportsMultipleAnglesPerFile();
	protected abstract boolean supportsMultipleIlluminationsPerFile();
	
	protected class Calibration
	{
		public double calX = 1, calY = 1, calZ = 1;
		public String calUnit = "um";

		public Calibration( final double calX, final double calY, final double calZ, final String calUnit )
		{
			this.calX = calX;
			this.calY = calY;
			this.calZ = calZ;
			this.calUnit = calUnit;
		}
		
		public Calibration( final double calX, final double calY, final double calZ )
		{
			this.calX = calX;
			this.calY = calY;
			this.calZ = calZ;
		}
		
		public Calibration() {};
	}
	
	protected class ViewSetupPrecursor
	{
		final public int c, i, a;
		
		public ViewSetupPrecursor( final int c, final int i, final int a )
		{
			this.c = c;
			this.i = i;
			this.a = a;
		}
		
		@Override
		public int hashCode()
		{
			return c * illuminationsNameList.size() * angleNameList.size() + i * angleNameList.size() + a;
		}
		
		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof ViewSetupPrecursor )
				return c == ((ViewSetupPrecursor)o).c && i == ((ViewSetupPrecursor)o).i && a == ((ViewSetupPrecursor)o).a;
			else
				return false;
		}
		
		@Override
		public String toString() { return "channel=" + channelNameList.get( c ) + ", ill.dir.=" + illuminationsNameList.get( i ) + ", angle=" + angleNameList.get( a ); }
	}
	
	protected boolean queryInformation()
	{		
		try 
		{
			if ( !queryGeneralInformation() )
				return false;

			if ( defaultFileNamePattern == null )
				defaultFileNamePattern = assembleDefaultPattern();

			if ( !queryNames() )
				return false;

			if ( showDebugFileNames && !debugShowFiles() )
				return false;

			if ( ( calibration1 == 0 && calibration2 < 2 && !loadFirstCalibration() ) || ( calibration1 == 1  && calibration2 < 2 && !loadAllCalibrations() ) )
				return false;

			if ( !queryDetails() )
				return false;
		} 
		catch ( ParseException e )
		{
			IOFunctions.println( e.toString() );
			return false;
		}

		return true;
	}

	/**
	 * Instantiate the {@link ImgLoader}
	 * 
	 * @param path - The path relative to the basepath
	 * @param basePath - The base path, where XML will be and the image stack are
	 * @param sequenceDescription 
	 * @return
	 */
	protected abstract StackImgLoader createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory, SequenceDescription sequenceDescription );
	
	@Override
	public SpimData2 createDataset()
	{
		// collect all the information
		if ( !queryInformation() )
			return null;
		
		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints timepoints = this.createTimePoints();
		final ArrayList< ViewSetup > setups = this.createViewSetups();
		final MissingViews missingViews = this.createMissingViews();
		
		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, missingViews );
		final ImgLoader imgLoader = createAndInitImgLoader( ".", new File( directory ), imgFactory, sequenceDescription );
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
		final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		return spimData;
	}

	/**
	 * Assembles the {@link ViewRegistration} object consisting of a list of {@link ViewRegistration}s for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList
	 * @param minResolution - the smallest resolution in any dimension (distance between two pixels in the output image will be that wide)
	 * @return
	 */
	protected static ViewRegistrations createViewRegistrations( final Map< ViewId, ViewDescription > viewDescriptionList, final double minResolution )
	{
		final HashMap< ViewId, ViewRegistration > viewRegistrationList = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewDescription viewDescription : viewDescriptionList.values() )
			if ( viewDescription.isPresent() )
			{
				final ViewRegistration viewRegistration = new ViewRegistration( viewDescription.getTimePointId(), viewDescription.getViewSetupId() );
				
				final VoxelDimensions voxelSize = viewDescription.getViewSetup().getVoxelSize(); 

				final double calX = voxelSize.dimension( 0 ) / minResolution;
				final double calY = voxelSize.dimension( 1 ) / minResolution;
				final double calZ = voxelSize.dimension( 2 ) / minResolution;
				
				final AffineTransform3D m = new AffineTransform3D();
				m.set( calX, 0.0f, 0.0f, 0.0f, 
					   0.0f, calY, 0.0f, 0.0f,
					   0.0f, 0.0f, calZ, 0.0f );
				final ViewTransform vt = new ViewTransformAffine( "calibration", m );
				viewRegistration.preconcatenateTransform( vt );
				
				viewRegistrationList.put( viewRegistration, viewRegistration );
			}
		
		return new ViewRegistrations( viewRegistrationList );
	}

	/**
	 * Assembles the list of missing view instances, i.e. {@link ViewSetup} that
	 * are missing at certain {@link TimePoint}s.
	 * 
	 * @return
	 */
	protected MissingViews createMissingViews()
	{
		if ( exceptionIds.size() == 0 )
			return null;

		final ArrayList< ViewId > missingViews = new ArrayList< ViewId >();
				
		for ( int t = 0; t < timepointNameList.size(); ++t )
		{			
			// assemble a subset of exceptions for the current timepoint
			final ArrayList< int[] > tmp = new ArrayList< int[] >();
			
			for ( int[] exceptions : exceptionIds )
				if ( exceptions[ 0 ] == t )
					tmp.add( exceptions );
		
			if ( tmp.size() > 0 )
			{
				int setupId = 0;

				for ( int c = 0; c < channelNameList.size(); ++c )
					for ( int i = 0; i < illuminationsNameList.size(); ++i )
						for ( int a = 0; a < angleNameList.size(); ++a )
						{
							for ( int[] exceptions : tmp )
								if ( exceptions[ 1 ] == c && exceptions[ 2 ] == i && exceptions[ 3 ] == a )
								{
									missingViews.add( new ViewId( Integer.parseInt( timepointNameList.get( t ) ), setupId ) );
									System.out.println( "creating missing views t:" + Integer.parseInt( timepointNameList.get( t ) ) + " c:" + c + " i:" + i + " a:" + a + " setupid: " + setupId );
								}
							
							++setupId;
						}
			}
		}
		
		return new MissingViews( missingViews );
	}

	/**
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups()
	{
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( int c = 0; c < channelNameList.size(); ++c )
			channels.add( new Channel( c, channelNameList.get( c ) ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		for ( int i = 0; i < illuminationsNameList.size(); ++i )
			illuminations.add( new Illumination( i, illuminationsNameList.get( i ) ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		for ( int a = 0; a < angleNameList.size(); ++a )
			angles.add( new Angle( a, angleNameList.get( a ) ) );

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( final Angle a : angles )
				{
					final Calibration cal = calibrations.get( new ViewSetupPrecursor( c.getId(), i.getId(), a.getId() ) );
					final VoxelDimensions voxelSize = new FinalVoxelDimensions( cal.calUnit, cal.calX, cal.calY, cal.calZ );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, null, voxelSize, c, a, i ) );
				}

		return viewSetups;
	}

	/**
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints()
	{
		try
		{
			return new TimePointsPattern( timepoints );
		}
		catch ( final ParseException e )
		{
			throw new RuntimeException( e );
		}
	}

	protected boolean queryDetails()
	{
		final GenericDialog gd;

		if ( calibration2 == 0 )
			gd = null;
		else
			gd = new GenericDialog( "Define dataset (3/3)" );

		if ( calibration1 == 0 ) // same voxel-size for all views
		{
			Calibration cal = null;
			
			if ( calibrations.values().size() != 1 )
				cal = new Calibration();
			else
				for ( final Calibration c : calibrations.values() )
					cal = c;

			if ( calibration2 > 0 ) // user define or verify the values
			{
				gd.addMessage( "Calibration (voxel size)", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );
				if ( calibration2 == 1 )
					gd.addMessage( "(read from file)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
				gd.addMessage( "" );
				
				gd.addNumericField( "Pixel_distance_x", cal.calX, 5 );
				gd.addNumericField( "Pixel_distance_y", cal.calY, 5 );
				gd.addNumericField( "Pixel_distance_z", cal.calZ, 5 );
				gd.addStringField( "Pixel_unit", cal.calUnit );
			
				gd.showDialog();
		
				if ( gd.wasCanceled() )
					return false;
	
				cal.calX = gd.getNextNumber();
				cal.calY = gd.getNextNumber();
				cal.calZ = gd.getNextNumber();
				
				cal.calUnit = gd.getNextString();
			}
			else
			{
				IOFunctions.println( "Calibration (voxel-size) read from file: x:" + cal.calX + " y:" + cal.calY + " z:" + cal.calZ + " " + cal.calUnit );
			}

			// same calibrations for all views
			calibrations.clear();			
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						calibrations.put( new ViewSetupPrecursor( c, i, a ),  cal );
		}
		else // different voxel-size for all views
		{
			if ( calibration2 > 0 ) // user define or verify the values
			{
				gd.addMessage( "Calibrations", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );
				if ( calibration2 == 1 )
					gd.addMessage( "(read from file)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
				gd.addMessage( "" );
			}

			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
					{
						ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a );
						Calibration cal = calibrations.get( vsp );

						if ( cal == null )
						{
							if ( calibration2 < 2 ) // load from file
							{
								IOFunctions.println( "Could not read calibration for view: " + vsp );
								IOFunctions.println( "Replacing with uniform calibration." );
							}
						
							cal = new Calibration();
							calibrations.put( vsp, cal );
						}

						if ( calibration2 > 0 ) // user define or verify the values
						{
							gd.addMessage( "View [" + vsp + "]" );

							gd.addNumericField( "Pixel_distance_x", cal.calX, 5 );
							gd.addNumericField( "Pixel_distance_y", cal.calY, 5 );
							gd.addNumericField( "Pixel_distance_z", cal.calZ, 5 );
							gd.addStringField( "Pixel_unit", cal.calUnit );
	
							gd.addMessage( "" );
						}
						else
						{
							IOFunctions.println( "Calibration (voxel-size) read from file x:" + cal.calX + " y:" + cal.calY + " z:" + cal.calZ + " " + cal.calUnit + " for view " + vsp );
						}
					}

			if ( calibration2 > 0 ) // user define or verify the values
			{
				GUIHelper.addScrollBars( gd );
	
				gd.showDialog();
	
				if ( gd.wasCanceled() )
					return false;

				for ( int c = 0; c < channelNameList.size(); ++c )
					for ( int i = 0; i < illuminationsNameList.size(); ++i )
						for ( int a = 0; a < angleNameList.size(); ++a )
						{
							final ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a );
							final Calibration cal = calibrations.get( vsp );
							
							cal.calX = gd.getNextNumber();
							cal.calY = gd.getNextNumber();
							cal.calZ = gd.getNextNumber();
							
							cal.calUnit = gd.getNextString();
						}
			}
		}
		
		return true;
	}
	
	protected boolean debugShowFiles()
	{
		final GenericDialog gd = new GenericDialog( "3d image stacks files" );

		gd.addMessage( "" );
		gd.addMessage( "Path: " + directory + "   " );
		gd.addMessage( "Note: Not selected files will be treated as missing views (e.g. missing files).", GUIHelper.smallStatusFont );

		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
					{
						String fileName = getFileNameFor( t, c, i, a );
						
						final boolean fileExisits = new File( directory, fileName ).exists();
						
						String ext = "";
						
						if ( hasMultipleChannels > 0 && numDigitsChannels == 0 )
							ext +=  "c = " + channelNameList.get( c );

						if ( hasMultipleTimePoints > 0 && numDigitsTimepoints == 0 )
							if ( ext.length() > 0 )
								ext += ", t = " + timepointNameList.get( t );
							else
								ext += "t = " + timepointNameList.get( t );

						if ( hasMultipleIlluminations > 0 && numDigitsIlluminations == 0 )
							if ( ext.length() > 0 )
								ext += ", i = " + illuminationsNameList.get( i );
							else
								ext += "i = " + illuminationsNameList.get( i );

						if ( hasMultipleAngles > 0 && numDigitsAngles == 0 )
							if ( ext.length() > 0 )
								ext += ", a = " + angleNameList.get( a );
							else
								ext += "a = " + angleNameList.get( a );

						if ( ext.length() > 1 )
							fileName += "   >> [" + ext + "]";

						final boolean select;

						if ( fileExisits )
						{
							fileName += " (file found)";
							select = true;
						}
						else
						{
							select = false;
							fileName += " (file NOT found)";
						}
						
						gd.addCheckbox( fileName, select );
						
						// otherwise underscores are gone ...
						((Checkbox)gd.getCheckboxes().lastElement()).setLabel( fileName );
						if ( !fileExisits )
							((Checkbox)gd.getCheckboxes().lastElement()).setBackground( GUIHelper.error );
					}
				
		GUIHelper.addScrollBars( gd );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		exceptionIds = new ArrayList<int[]>();

		// collect exceptions to the definitions
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						if ( gd.getNextBoolean() == false )
						{
							exceptionIds.add( new int[]{ t, c, i, a } );
							System.out.println( "adding missing views t:" + t + " c:" + c + " i:" + i + " a:" + a );
						}

		return true;
	}
	
	protected boolean queryNames() throws ParseException
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (2/3)" );
		
		gd.addDirectoryOrFileField( "Image_File_directory", defaultDirectory, 40 );
		gd.addStringField( "Image_File_Pattern", defaultFileNamePattern, 40 );

		if ( hasMultipleTimePoints > 0 )
			gd.addStringField( "Timepoints_", defaultTimepoints, 15 );
		
		if ( hasMultipleChannels > 0 )
			gd.addStringField( "Channels_", defaultChannels, 15 );

		if ( hasMultipleIlluminations > 0 )
			gd.addStringField( "Illumination_directions_", defaultIlluminations, 15 );
		
		if ( hasMultipleAngles > 0 )
			gd.addStringField( "Acquisition_angles_", defaultAngles, 15 );

		gd.addChoice( "Calibration_Type", calibrationChoice1, calibrationChoice1[ defaultCalibration1 ] );
		gd.addChoice( "Calibration_Definition", calibrationChoice2, calibrationChoice2[ defaultCalibration2 ] );

		gd.addChoice( "ImgLib2_data_container", imglib2Container, imglib2Container[ defaultContainer ] );
		gd.addMessage(
				"Use ArrayImg if -ALL- input views are smaller than ~2048x2048x500 px (2^31 px), or if the\n" +
				"program throws an OutOfMemory exception while processing.  CellImg is slower, but more\n" +
				"memory efficient and supports much larger file sizes only limited by the RAM of the machine.", 
				new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		
		gd.addCheckbox( "Show_list of filenames (to debug and it allows to deselect individual files)", showDebugFileNames );
		gd.addMessage( "Note: this might take a few seconds if thousands of files are present", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		defaultDirectory = directory = gd.getNextString();
		defaultFileNamePattern = fileNamePattern = gd.getNextString();

		timepoints = channels = illuminations = angles = null;
		replaceTimepoints = replaceChannels = replaceIlluminations = replaceAngles = null;
		
		// get the String patterns and verify that the corresponding pattern, 
		// e.g. {t} or {tt} exists in the pattern
		if ( hasMultipleTimePoints > 0 )
		{
			defaultTimepoints = timepoints = gd.getNextString();
			
			if ( hasMultipleTimePoints == 1 )
			{
				replaceTimepoints = IntegerPattern.getReplaceString( fileNamePattern, TIMEPOINT_PATTERN );
				
				if ( replaceTimepoints == null )
					throw new ParseException( "Pattern {" + TIMEPOINT_PATTERN + "} not present in " + fileNamePattern + 
							" although you indicated there would be several timepoints. Stopping.", 0 );					
				else
					numDigitsTimepoints = replaceTimepoints.length() - 2;
			}
			else 
			{
				replaceTimepoints = null;
				numDigitsTimepoints = 0;
			}
		}

		if ( hasMultipleChannels > 0 )
		{
			defaultChannels = channels = gd.getNextString();
			
			if ( hasMultipleChannels == 1 )
			{			
				replaceChannels = IntegerPattern.getReplaceString( fileNamePattern, CHANNEL_PATTERN );
				if ( replaceChannels == null )
						throw new ParseException( "Pattern {" + CHANNEL_PATTERN + "} not present in " + fileNamePattern + 
								" although you indicated there would be several channels. Stopping.", 0 );					
				else
					numDigitsChannels = replaceChannels.length() - 2;
			}
			else
			{
				replaceChannels = null;
				numDigitsChannels = 0;
			}
		}

		if ( hasMultipleIlluminations > 0 )
		{
			defaultIlluminations = illuminations = gd.getNextString();
			
			if ( hasMultipleIlluminations == 1 )
			{
				replaceIlluminations = IntegerPattern.getReplaceString( fileNamePattern, ILLUMINATION_PATTERN );
				
				if ( replaceIlluminations == null )
					throw new ParseException( "Pattern {" + ILLUMINATION_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several illumination directions. Stopping.", 0 );
				else
					numDigitsIlluminations = replaceIlluminations.length() - 2;
			}
			else
			{
				replaceIlluminations = null;
				numDigitsIlluminations = 0;
			}
		}

		if ( hasMultipleAngles > 0 )
		{
			defaultAngles = angles = gd.getNextString();
			
			if ( hasMultipleAngles == 1 )
			{
				replaceAngles = IntegerPattern.getReplaceString( fileNamePattern, ANGLE_PATTERN );

				if ( replaceAngles == null )
					throw new ParseException( "Pattern {" + ANGLE_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several angles.", 0 );
				else
					numDigitsAngles = replaceAngles.length() - 2;
			}
			else
			{
				replaceAngles = null;
				numDigitsAngles = 0;
			}
		}

		// get the list of integers
		timepointNameList = ( NamePattern.parseNameString( timepoints, false ) );
		channelNameList = ( NamePattern.parseNameString( channels, true ) );
		illuminationsNameList = ( NamePattern.parseNameString( illuminations, true ) );
		angleNameList = ( NamePattern.parseNameString( angles, true ) );

		exceptionIds = new ArrayList< int[] >();

		defaultCalibration1 = calibration1 = gd.getNextChoiceIndex();
		defaultCalibration2 = calibration2 = gd.getNextChoiceIndex();

		defaultContainer = gd.getNextChoiceIndex();
		
		if ( defaultContainer == 0 )
			imgFactory = new ArrayImgFactory< FloatType >();
		else
			imgFactory = new CellImgFactory< FloatType >( 256 );
		
		showDebugFileNames = gd.getNextBoolean();
		
		return true;		
	}
	
	public static ArrayList< String > convertIntegerList( final List< Integer > list )
	{
		final ArrayList< String > stringList = new ArrayList< String >();
		
		for ( final int i : list )
			stringList.add( Integer.toString( i ) );
		
		return stringList;
	}
	
	/**
	 * Assemble the filename for the corresponding file based on the indices for time, channel, illumination and angle
	 * 
	 * If the fileNamePattern is separated by ';', it will return multiple solutions for each filenamepattern
	 * 
	 * @param tpID
	 * @param chID
	 * @param illID
	 * @param angleID
	 * @return
	 */
	protected String getFileNameFor( final int tpID, final int chID, final int illID, final int angleID )
	{
		return getFileNamesFor( fileNamePattern, replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles, 
				timepointNameList.get( tpID ), channelNameList.get( chID ), illuminationsNameList.get( illID ), angleNameList.get( angleID ),
				numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles )[ 0 ];
	}

	/**
	 * Assemble the filename for the corresponding file based on the indices for time, channel, illumination and angle
	 * 
	 * If the fileNamePattern is separated by ';', it will return multiple solutions for each filenamepattern
	 * 
	 * @param fileNames
	 * @param replaceTimepoints
	 * @param replaceChannels
	 * @param replaceIlluminations
	 * @param replaceAngles
	 * @param tpName
	 * @param chName
	 * @param illName
	 * @param angleName
	 * @param numDigitsTP
	 * @param numDigitsCh
	 * @param numDigitsIll
	 * @param numDigitsAngle
	 * @return
	 */
	public static String[] getFileNamesFor( String fileNames, 
			final String replaceTimepoints, final String replaceChannels, 
			final String replaceIlluminations, final String replaceAngles, 
			final String tpName, final String chName, final String illName, final String angleName,
			final int numDigitsTP, final int numDigitsCh, final int numDigitsIll, final int numDigitsAngle )
	{
		String[] fileName = fileNames.split( ";" );
		
		for ( int i = 0; i < fileName.length; ++i )
		{
			if ( replaceTimepoints != null )
				fileName[ i ] = fileName[ i ].replace( replaceTimepoints, leadingZeros( tpName, numDigitsTP ) );
	
			if ( replaceChannels != null )
				fileName[ i ] = fileName[ i ].replace( replaceChannels, leadingZeros( chName, numDigitsCh ) );
	
			if ( replaceIlluminations != null )
				fileName[ i ] = fileName[ i ].replace( replaceIlluminations, leadingZeros( illName, numDigitsIll ) );
	
			if ( replaceAngles != null )
				fileName[ i ] = fileName[ i ].replace( replaceAngles, leadingZeros( angleName, numDigitsAngle ) );
		}
		return fileName;
	}
	
	public static String leadingZeros( String s, final int numDigits )
	{
		while ( s.length() < numDigits )
			s = "0" + s;
		
		return s;
	}

	/**
	 * populates the fields calX, calY, calZ from the first file of the series
	 * 
	 * @return - true if successful
	 */
	protected boolean loadFirstCalibration()
	{
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
					{
						if ( exceptionIds.size() > 0 && 
							 exceptionIds.get( 0 )[ 0 ] == t && exceptionIds.get( 0 )[ 1 ] == c && 
							 exceptionIds.get( 0 )[ 2 ] == t && exceptionIds.get( 0 )[ 3 ] == a )
						{
							continue;
						}
						else
						{
							final Calibration cal = loadCalibration( new File( directory, getFileNameFor( t, c, i, a ) ) );
							
							if ( cal == null )
							{
								return false;
							}
							else
							{
								calibrations.put( new ViewSetupPrecursor( c, i, a ), cal );
								return true;
							}
						}
					}
		
		return false;
	}

	/**
	 * populates the fields calX, calY, calZ from the first file of the series
	 * 
	 * @return - true if successful
	 */
	protected boolean loadAllCalibrations()
	{
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
					{
						if ( exceptionIds.size() > 0 && 
							 exceptionIds.get( 0 )[ 0 ] == t && exceptionIds.get( 0 )[ 1 ] == c && 
							 exceptionIds.get( 0 )[ 2 ] == t && exceptionIds.get( 0 )[ 3 ] == a )
						{
							continue;
						}
						else
						{
							final ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a );
							
							if ( calibrations.get( vsp ) == null )
							{
								final Calibration cal = loadCalibration( new File( directory, getFileNameFor( t, c, i, a ) ) );
								
								if ( cal != null )
									calibrations.put( vsp, cal );
							}
						}
					}
		
		return true;
	}

	/**
	 * Loads the calibration stored in a specific file and closes it afterwards. Depends on the type of opener that is used.
	 * 
	 * @param file
	 * @return - the Calibration or null if it could not be loaded for some reason
	 */
	protected abstract Calibration loadCalibration( final File file );

	protected String assembleDefaultPattern()
	{
		String pattern = "spim";
		
		if ( hasMultipleTimePoints == 1 )
			pattern += "_TL{t}";
		
		if ( hasMultipleChannels == 1 )
			pattern += "_Channel{c}";
		
		if ( hasMultipleIlluminations == 1 )
			pattern += "_Illum{i}";
		
		if ( hasMultipleAngles == 1 )
			pattern += "_Angle{a}";
		
		return pattern + ".tif";
	}
	
	protected boolean queryGeneralInformation()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (1/3)" );
		
		final Color green = new Color( 0, 139, 14 );
		final Color red = Color.RED;
		
		gd.addMessage( "File reader: " + getTitle(), new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );

		gd.addMessage( "" );		

		if ( supportsMultipleTimepointsPerFile() )
		{
			if ( getDefaultMultipleTimepoints() >= dimensionChoiceTimePointsTrue.length )
				setDefaultMultipleTimepoints( 0 );

			gd.addMessage( "Supports multiple timepoints per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_timepoints", dimensionChoiceTimePointsTrue, dimensionChoiceTimePointsTrue[ getDefaultMultipleTimepoints() ] );
		}
		else
		{
			if ( getDefaultMultipleTimepoints() >= dimensionChoiceTimePointsFalse.length )
				setDefaultMultipleTimepoints( 0 );

			gd.addMessage( "NO support for multiple timepoints per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_timepoints", dimensionChoiceTimePointsFalse, dimensionChoiceTimePointsFalse[ getDefaultMultipleTimepoints() ] );
		}

		gd.addMessage( "" );

		if ( supportsMultipleChannelsPerFile() )
		{
			if ( getDefaultMultipleChannels() >= dimensionChoiceChannelsTrue.length )
				setDefaultMultipleChannels( 0 );

			gd.addMessage( "Supports multiple channels per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_channels", dimensionChoiceChannelsTrue, dimensionChoiceChannelsTrue[ getDefaultMultipleChannels() ] );
		}
		else
		{
			if ( getDefaultMultipleChannels() >= dimensionChoiceChannelsFalse.length )
				setDefaultMultipleChannels( 0 );

			gd.addMessage( "NO support for multiple channels per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_channels", dimensionChoiceChannelsFalse, dimensionChoiceChannelsFalse[ getDefaultMultipleChannels() ] );
		}

		gd.addMessage( "" );

		if ( supportsMultipleIlluminationsPerFile() )
		{
			if ( getDefaultMultipleIlluminations() >= dimensionChoiceIlluminationsTrue.length )
				setDefaultMultipleIlluminations( 0 );

			gd.addMessage( "Supports multiple illumination directions per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "_____Multiple_illumination_directions", dimensionChoiceIlluminationsTrue, dimensionChoiceIlluminationsTrue[ getDefaultMultipleIlluminations() ] );
		}
		else
		{
			if ( getDefaultMultipleIlluminations() >= dimensionChoiceIlluminationsFalse.length )
				setDefaultMultipleIlluminations( 0 );

			gd.addMessage( "NO support for multiple illumination directions per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "_____Multiple_illumination_directions", dimensionChoiceIlluminationsFalse, dimensionChoiceIlluminationsFalse[ getDefaultMultipleIlluminations() ] );
		}

		gd.addMessage( "" );

		if ( supportsMultipleAnglesPerFile() )
		{
			if ( getDefaultMultipleAngles() >= dimensionChoiceAnglesTrue.length )
				setDefaultMultipleAngles( 0 );

			gd.addMessage( "Supports multiple angles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_angles", dimensionChoiceAnglesTrue, dimensionChoiceAnglesTrue[ getDefaultMultipleAngles() ] );
		}
		else
		{
			if ( getDefaultMultipleAngles() >= dimensionChoiceAnglesFalse.length )
				setDefaultMultipleAngles( 0 );

			gd.addMessage( "NO support for multiple angles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_angles", dimensionChoiceAnglesFalse, dimensionChoiceAnglesFalse[ getDefaultMultipleAngles() ] );
		}

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		hasMultipleTimePoints = gd.getNextChoiceIndex();
		hasMultipleChannels = gd.getNextChoiceIndex();
		hasMultipleIlluminations = gd.getNextChoiceIndex();
		hasMultipleAngles = gd.getNextChoiceIndex();

		setDefaultMultipleTimepoints( hasMultipleTimePoints );
		setDefaultMultipleChannels( hasMultipleChannels );
		setDefaultMultipleIlluminations( hasMultipleIlluminations );
		setDefaultMultipleAngles( hasMultipleAngles );

		return true;
	}	
}
