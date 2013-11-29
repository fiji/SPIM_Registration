package spim.fiji.datasetmanager;

import static mpicbg.spim.data.sequence.XmlKeys.TIMEPOINTS_PATTERN_STRING;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import spim.fiji.plugin.GUIHelper;
import spim.fiji.spimdata.SpimDataInterestPoints;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;

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
	
	public static String defaultTimepoints = "18-20";
	public static String defaultChannels = "1,2";
	public static String defaultIlluminations = "0,1";
	public static String defaultAngles = "0-315:45";

	protected String timepoints, channels, illuminations, angles;
	protected ArrayList< String > timepointNameList, channelNameList, illuminationsNameList, angleNameList;
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	
	/* new int[]{ t, c, i, a } - indices */
	protected ArrayList< int[] > exceptionIds;
	
	protected String[] calibrationChoice = new String[]{ "Same calibration for all files (load from first file)", "Same calibration for all files (user defined)", "Load calibration for each file individually" };
	protected String[] imglib2Container = new String[]{ "ArrayImg (faster)", "CellImg (slower, larger files supported)" };

	public static int defaultContainer = 0;
	public ImgFactory< ? extends NativeType< ? > > imgFactory;
	public static int defaultCalibration = 0;
	public int calibation;
	
	public static String defaultDirectory = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM";
	public static String defaultFileNamePattern = null;

	protected String directory, fileNamePattern;
	
	protected double calX = 1, calY = 1, calZ = 1;
	protected String calUnit = "µm";
	
	protected abstract boolean supportsMultipleTimepointsPerFile();
	protected abstract boolean supportsMultipleChannelsPerFile();
	protected abstract boolean supportsMultipleAnglesPerFile();
	protected abstract boolean supportsMultipleIlluminationsPerFile();
	
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
			
			if ( calibation == 0 && !loadFirstCalibration() )
				return false;
			
			if ( !queryDetails() )
				return false;
		} 
		catch ( ParseException e )
		{
			IJ.log( e.toString() );
			return false;
		}
				
		return true;
	}

	/**
	 * Instantiate the {@link ImgLoader}
	 * 
	 * @param path - The path relative to the basepath
	 * @param basePath - The base path, where XML will be and the image stack are
	 * @return
	 */
	protected abstract ImgLoader createAndInitImgLoader( final String path, final File basePath, final ImgFactory< ? extends NativeType< ? > > imgFactory );
	
	@Override
	public SpimDataInterestPoints createDataset()
	{
		// collect all the information
		if ( !queryInformation() )
			return null;
		
		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints< TimePoint > timepoints = this.createTimePoints();
		final ArrayList< ViewSetup > setups = this.createViewSetups();
		final MissingViews missingViews = this.createMissingViews();
		final ImgLoader imgLoader = createAndInitImgLoader( ".", new File( directory ), imgFactory );
		
		// instantiate the sequencedescription
		final SequenceDescription< TimePoint, ViewSetup > sequenceDescription = new SequenceDescription< TimePoint, ViewSetup >( timepoints, setups, missingViews, imgLoader );
		
		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = this.createViewRegistrations( sequenceDescription.getViewDescriptions() );
		
		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimDataInterestPoints spimData = new SpimDataInterestPoints( new File( directory ), sequenceDescription, viewRegistrations, new ArrayList< ViewInterestPoints >() );
		
		return spimData;
	}

	/**
	 * Assembles the list of {@link ViewRegistration}s for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList
	 * @return
	 */
	protected ViewRegistrations createViewRegistrations( final List< ViewDescription< TimePoint, ViewSetup > > viewDescriptionList )
	{
		final ArrayList< ViewRegistration > viewRegistrationList = new ArrayList< ViewRegistration >();
		
		for ( final ViewDescription< TimePoint, ViewSetup > viewDescription : viewDescriptionList )
			if ( viewDescription.isPresent() )
				viewRegistrationList.add( new ViewRegistration( viewDescription.getTimePointId(), viewDescription.getViewSetupId() ) );
		
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
							for ( int[] exceptions : exceptionIds )
								if ( exceptions[ 1 ] == c && exceptions[ 2 ] == i && exceptions[ 3 ] == a )
									missingViews.add( new ViewId( t, setupId ) );
							
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
		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		
		for ( int c = 0; c < channelNameList.size(); ++c )
			for ( int i = 0; i < illuminationsNameList.size(); ++i )
				for ( int a = 0; a < angleNameList.size(); ++a )
				{
					final Channel channel = new Channel( c, channelNameList.get( c ) );
					final Illumination illumination = new Illumination( i, illuminationsNameList.get( i ) );
					final Angle angle = new Angle( a, angleNameList.get( a ) );
					
					if ( calibation < 2 )
						viewSetups.add( new ViewSetup( viewSetups.size(), angle, illumination, channel, -1, -1, -1, calUnit, calX, calY, calZ ) );
					else
						viewSetups.add( new ViewSetup( viewSetups.size(), angle, illumination, channel, -1, -1, -1, calUnit, -1, -1, -1 ) );
				}
		
		return viewSetups;
	}
	
	/**
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 * 
	 * @return
	 */
	protected TimePoints< TimePoint > createTimePoints()
	{
		final ArrayList< TimePoint > timepointList = new ArrayList< TimePoint >();
		
		for ( final String timepoint : this.timepointNameList )
			timepointList.add( new TimePoint( timepointList.size(), timepoint ) );
		
		final TimePoints< TimePoint > timepoints = new TimePoints< TimePoint >( timepointList );
		
		// remember the pattern
		timepoints.getHashMap().put( TIMEPOINTS_PATTERN_STRING, this.timepoints );
		
		return timepoints;
	}
		
	protected boolean queryDetails()
	{
		if ( calibation < 2 )
		{
			final GenericDialog gd = new GenericDialog( "Define dataset (3/3)" );
		
			gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );			
			if ( calibation == 1 )
				gd.addMessage( "(read from file)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
			gd.addMessage( "" );
			
			gd.addNumericField( "Pixel_distance_x", calX, 5 );
			gd.addNumericField( "Pixel_distance_y", calY, 5 );
			gd.addNumericField( "Pixel_distance_z", calZ, 5 );
			gd.addStringField( "Pixel_unit", calUnit );
		
			gd.showDialog();
	
			if ( gd.wasCanceled() )
				return false;

			calX = gd.getNextNumber();
			calY = gd.getNextNumber();
			calZ = gd.getNextNumber();
			
			calUnit = gd.getNextString();
		}
		
		return true;
	}
	
	protected boolean debugShowFiles()
	{
		final GenericDialog gd = new GenericDialog( "3d image stacks files" );

		gd.addMessage( "" );
		gd.addMessage( "Path: " + directory + "   " );

		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
					{
						String fileName = getFileNameFor( t, c, i, a );
						
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
						
						gd.addCheckbox( fileName, true );
						
						// otherwise underscores are gone ...
						((Checkbox)gd.getCheckboxes().lastElement()).setLabel( fileName );
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
							exceptionIds.add( new int[]{ t, c, i, a } );					
				
		return true;
	}
	
	protected boolean queryNames() throws ParseException
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (2/3)" );
		
		gd.addDirectoryOrFileField( "Image_File_directory", defaultDirectory );
		gd.addStringField( "Image_File_Pattern", defaultFileNamePattern, 40 );

		if ( hasMultipleTimePoints > 0 )
			gd.addStringField( "Timepoints", defaultTimepoints );
		
		if ( hasMultipleChannels > 0 )
			gd.addStringField( "Channels", defaultChannels );

		if ( hasMultipleIlluminations > 0 )
			gd.addStringField( "Illumination_directions", defaultIlluminations );
		
		if ( hasMultipleAngles > 0 )
			gd.addStringField( "Acquisition_angles", defaultAngles );
		
		gd.addChoice( "Calibration", calibrationChoice, calibrationChoice[ defaultCalibration ] );
		
		gd.addChoice( "ImgLib2_data_container", imglib2Container, imglib2Container[ defaultContainer ] );
		gd.addMessage( "Use ArrayImg if -ALL- input views are smaller than ~2048x2048x500 px (2^31 px), or if the\n" +
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
		timepointNameList = convertIntegerList( IntegerPattern.parseIntegerString( timepoints ) );
		channelNameList = convertIntegerList( IntegerPattern.parseIntegerString( channels ) );
		illuminationsNameList = convertIntegerList( IntegerPattern.parseIntegerString( illuminations ) );
		angleNameList = convertIntegerList( IntegerPattern.parseIntegerString( angles ) );

		exceptionIds = new ArrayList< int[] >();
		
		defaultCalibration = calibation = gd.getNextChoiceIndex();

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
	 * @param tpID
	 * @param chID
	 * @param illID
	 * @param angleID
	 * @return
	 */
	protected String getFileNameFor( final int tpID, final int chID, final int illID, final int angleID )
	{
		return getFileNameFor( fileNamePattern, replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles, 
				timepointNameList.get( tpID ), channelNameList.get( chID ), illuminationsNameList.get( illID ), angleNameList.get( angleID ) );
	}

	public static String getFileNameFor( String fileName, 
			final String replaceTimepoints, final String replaceChannels, 
			final String replaceIlluminations, final String replaceAngles, 
			final String tpName, final String chName, final String illName, final String angleName )
	{
		if ( replaceTimepoints != null )
			fileName = fileName.replace( replaceTimepoints, tpName );

		if ( replaceChannels != null )
			fileName = fileName.replace( replaceChannels, chName );

		if ( replaceIlluminations != null )
			fileName = fileName.replace( replaceIlluminations, illName );

		if ( replaceAngles != null )
			fileName = fileName.replace( replaceAngles, angleName );
		
		return fileName;
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
							return loadCalibration( new File( directory, getFileNameFor( t, c, i, a ) ) );
						}
					}
		
		return false;
	}
	
	/**
	 * Loads the calibration stored in a specific file and closes it afterwards. Depends on the type of opener that is used.
	 * 
	 * @param file
	 * @return
	 */
	protected abstract boolean loadCalibration( final File file );

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
			gd.addMessage( "Supports multiple timepoints per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
		else
			gd.addMessage( "NO support for multiple timepoints per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );

		if ( supportsMultipleTimepointsPerFile() )
			gd.addChoice( "Multiple_timepoints", dimensionChoiceTimePointsTrue, dimensionChoiceTimePointsTrue[ getDefaultMultipleTimepoints() ] );
		else
			gd.addChoice( "Multiple_timepoints", dimensionChoiceTimePointsFalse, dimensionChoiceTimePointsTrue[ getDefaultMultipleTimepoints() ] );

		gd.addMessage( "" );

		if ( supportsMultipleChannelsPerFile() )
			gd.addMessage( "Supports multiple channels per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
		else
			gd.addMessage( "NO support for multiple channels per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );

		if ( supportsMultipleChannelsPerFile() )
			gd.addChoice( "Multiple_channels", dimensionChoiceChannelsTrue, dimensionChoiceChannelsTrue[ getDefaultMultipleChannels() ] );
		else
			gd.addChoice( "Multiple_channels", dimensionChoiceChannelsFalse, dimensionChoiceChannelsTrue[ getDefaultMultipleChannels() ] );

		gd.addMessage( "" );

		if ( supportsMultipleIlluminationsPerFile() )
			gd.addMessage( "Supports multiple illumination directions per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
		else
			gd.addMessage( "NO support for multiple illumination directions per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );

		if ( supportsMultipleIlluminationsPerFile() )
			gd.addChoice( "_____Multiple_illumination_directions", dimensionChoiceIlluminationsTrue, dimensionChoiceIlluminationsTrue[ getDefaultMultipleIlluminations() ] );
		else
			gd.addChoice( "_____Multiple_illumination_directions", dimensionChoiceIlluminationsFalse, dimensionChoiceIlluminationsTrue[ getDefaultMultipleIlluminations() ] );

		gd.addMessage( "" );
		
		if ( supportsMultipleAnglesPerFile() )
			gd.addMessage( "Supports multiple angles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
		else
			gd.addMessage( "NO support for multiple angles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );

		if ( supportsMultipleAnglesPerFile() )
			gd.addChoice( "Multiple_angles", dimensionChoiceAnglesTrue, dimensionChoiceAnglesTrue[ getDefaultMultipleAngles() ] );
		else
			gd.addChoice( "Multiple_angles", dimensionChoiceAnglesFalse, dimensionChoiceAnglesTrue[ getDefaultMultipleAngles() ] );
		/*
		gd.addCheckbox( "Dataset_with_multiple_timepoints", defaultHasMultipleTimePoints );
		gd.addCheckbox( "Dataset_with_multiple_channels", defaultHasMultipleChannels );
		gd.addCheckbox( "Dataset_with_multiple_illumination_directions", defaultHasMultipleIlluminations );
		gd.addCheckbox( "Dataset_with_multiple_angles", defaultHasMultipleAngles );
		*/
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
		
		/*
		hasMultipleTimePoints = defaultHasMultipleTimePoints = gd.getNextBoolean();
		hasMultipleChannels = defaultHasMultipleChannels = gd.getNextBoolean();
		hasMultipleIlluminations = defaultHasMultipleIlluminations = gd.getNextBoolean();
		hasMultipleAngles = defaultHasMultipleAngles = gd.getNextBoolean();
		*/
		return true;
	}	
}
