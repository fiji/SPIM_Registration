package fiji.datasetmanager;

import static mpicbg.spim.data.sequence.XmlKeys.TIMEPOINTS_PATTERN_STRING;
import fiji.plugin.Multi_View_Deconvolution;
import fiji.spimdata.SpimDataBeads;
import fiji.spimdata.beads.ViewBeads;
import fiji.spimdata.sequence.ViewSetupBeads;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.GenericDialog;

import java.awt.BorderLayout;
import java.awt.Checkbox;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.Toolkit;
import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

public abstract class StackList implements MultiViewDatasetDefinition
{
	final public static char TIMEPOINT_PATTERN = 't';
	final public static char CHANNEL_PATTERN = 'c';
	final public static char ILLUMINATION_PATTERN = 'i';
	final public static char ANGLE_PATTERN = 'a';
	
	public static boolean defaultHasMultipleAngles = true;
	public static boolean defaultHasMultipleTimePoints = true;
	public static boolean defaultHasMultipleChannels = false;
	public static boolean defaultHasMultipleIlluminations = false;
	
	protected boolean hasMultipleAngles, hasMultipleTimePoints, hasMultipleChannels, hasMultipleIlluminations;
	
	public static boolean showDebugFileNames = true;
	
	public static String defaultTimepoints = "18-20";
	public static String defaultChannels = "1,2";
	public static String defaultIlluminations = "0,1";
	public static String defaultAngles = "0-315:45";

	protected String timepoints, channels, illuminations, angles;
	protected ArrayList< Integer > timepointList, channelList, illuminationsList, angleList;
	protected ArrayList< Boolean > channelHasBeads;
	protected String replaceTimepoints, replaceChannels, replaceIlluminations, replaceAngles;
	protected int numDigitsTimepoints, numDigitsChannels, numDigitsIlluminations, numDigitsAngles;
	
	/* new int[]{ t, c, i, a } - indices */
	protected ArrayList< int[] > exceptionIds;
	
	protected String[] calibrationChoice = new String[]{ "Same calibration for all files (load from first file)", "Same calibration for all files (user defined)", "Load calibration for each file individually" };
	protected String[] imglib2Container = new String[]{ "ArrayImg (faster)", "CellImg (slower, larger files supported)" };

	public static int defaultContainer = 0;
	public int container;
	public static int defaultCalibration = 0;
	public int calibation;
	
	public static String defaultDirectory = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM";
	public static String defaultFileNamePattern = null;

	protected String directory, fileNamePattern;
	
	protected double calX = 1, calY = 1, calZ = 1;
	protected String calUnit = "µm";

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
	protected abstract ImgLoader createAndInitImgLoader( final String path, final File basePath );
	
	@Override
	public SpimDataBeads createDataset()
	{
		// collect all the information
		if ( !queryInformation() )
			return null;
		
		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints< TimePoint > timepoints = this.createTimePoints();
		final ArrayList< ViewSetupBeads > setups = this.createViewSetups();
		final MissingViews missingViews = this.createMissingViews();
		final ImgLoader imgLoader = createAndInitImgLoader( ".", new File( directory ) );
		
		// instantiate the sequencedescription
		final SequenceDescription< TimePoint, ViewSetupBeads > sequenceDescription = new SequenceDescription< TimePoint, ViewSetupBeads >( timepoints, setups, missingViews, imgLoader );
		
		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = this.createViewRegistrations( sequenceDescription.getViewDescriptions() );
		
		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimDataBeads spimData = new SpimDataBeads( new File( directory ), sequenceDescription, viewRegistrations, new ArrayList< ViewBeads >() );
		
		return spimData;
	}

	/**
	 * Assembles the list of {@link ViewRegistration}s for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList
	 * @return
	 */
	protected ViewRegistrations createViewRegistrations( final List< ViewDescription< TimePoint, ViewSetupBeads > > viewDescriptionList )
	{
		final ArrayList< ViewRegistration > viewRegistrationList = new ArrayList< ViewRegistration >();
		
		for ( final ViewDescription< TimePoint, ViewSetupBeads > viewDescription : viewDescriptionList )
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
				
		for ( int t = 0; t < timepointList.size(); ++t )
		{			
			// assemble a subset of exceptions for the current timepoint
			final ArrayList< int[] > tmp = new ArrayList< int[] >();
			
			for ( int[] exceptions : exceptionIds )
				if ( exceptions[ 0 ] == t )
					tmp.add( exceptions );
		
			if ( tmp.size() > 0 )
			{
				int setupId = 0;

				for ( int c = 0; c < channelList.size(); ++c )
					for ( int i = 0; i < illuminationsList.size(); ++i )
						for ( int a = 0; a < angleList.size(); ++a )
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
	protected ArrayList< ViewSetupBeads > createViewSetups()
	{
		final ArrayList< ViewSetupBeads > viewSetups = new ArrayList< ViewSetupBeads >();
		
		for ( int c = 0; c < channelList.size(); ++c )
			for ( int i = 0; i < illuminationsList.size(); ++i )
				for ( int a = 0; a < angleList.size(); ++a )
				{
					final int channel = channelList.get( c );
					final boolean hasBeads = channelHasBeads.get( c );
					final int illumination = illuminationsList.get( i );
					final int angle = angleList.get( a );
					
					if ( calibation < 2 )
						viewSetups.add( new ViewSetupBeads( viewSetups.size(), angle, illumination, channel, -1, -1, -1, calUnit, calX, calY, calZ, hasBeads ) );
					else
						viewSetups.add( new ViewSetupBeads( viewSetups.size(), angle, illumination, channel, -1, -1, -1, calUnit, -1, -1, -1, hasBeads ) );
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
		
		for ( final int timepoint : this.timepointList )
			timepointList.add( new TimePoint( timepointList.size(), Integer.toString( timepoint ) ) );
		
		final TimePoints< TimePoint > timepoints = new TimePoints< TimePoint >( timepointList );
		
		// remember the pattern
		timepoints.getHashMap().put( TIMEPOINTS_PATTERN_STRING, this.timepoints );
		
		return timepoints;
	}
		
	protected boolean queryDetails()
	{
		final GenericDialog gd = new GenericDialog( "Define dataset (3/3)" );
		
		gd.addMessage( "Channel definitions", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );
		gd.addMessage( "" );
		
		for ( int c = 0; c < channelList.size(); ++c )
			gd.addCheckbox( "Beads_visible_in_channel_" + channelList.get( c ), true );

		if ( calibation < 2 )
		{
			gd.addMessage( "" );
			gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );			
			if ( calibation == 1 )
				gd.addMessage( "(read from file)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
			gd.addMessage( "" );
			
			gd.addNumericField( "Pixel_distance_x", calX, 5 );
			gd.addNumericField( "Pixel_distance_y", calY, 5 );
			gd.addNumericField( "Pixel_distance_z", calZ, 5 );
			gd.addStringField( "Pixel_unit", calUnit );
		}

		gd.showDialog();

		for ( int c = 0; c < channelList.size(); ++c )
			gd.addCheckbox( "Beads_visible_in_channel_" + channelList.get( c ), true );

		if ( gd.wasCanceled() )
			return false;
		
		channelHasBeads = new ArrayList<Boolean>( channelList.size() );

		for ( int c = 0; c < channelList.size(); ++c )
			channelHasBeads.add( gd.getNextBoolean() );

		if ( calibation < 2 )
		{
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

		for ( int t = 0; t < timepointList.size(); ++t )
			for ( int c = 0; c < channelList.size(); ++c )
				for ( int i = 0; i < illuminationsList.size(); ++i )
					for ( int a = 0; a < angleList.size(); ++a )
					{
						String fileName = getFileNameFor( t, c, i, a );

						gd.addCheckbox( fileName, true );
						
						// otherwise underscores are gone ...
						((Checkbox)gd.getCheckboxes().lastElement()).setLabel( fileName );
					}
				
		addScrollBars( gd );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		exceptionIds = new ArrayList<int[]>();

		// collect exceptions to the definitions
		for ( int t = 0; t < timepointList.size(); ++t )
			for ( int c = 0; c < channelList.size(); ++c )
				for ( int i = 0; i < illuminationsList.size(); ++i )
					for ( int a = 0; a < angleList.size(); ++a )
						if ( gd.getNextBoolean() == false )
							exceptionIds.add( new int[]{ t, c, i, a } );					
				
		return true;
	}
	
	protected boolean queryNames() throws ParseException
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (2/3)" );
		
		gd.addDirectoryOrFileField( "Image_File_directory", defaultDirectory );
		gd.addStringField( "Image_File_Pattern", defaultFileNamePattern, 40 );

		if ( hasMultipleTimePoints )
			gd.addStringField( "Timepoints", defaultTimepoints );
		
		if ( hasMultipleChannels )
			gd.addStringField( "Channels", defaultChannels );

		if ( hasMultipleIlluminations )
			gd.addStringField( "Illumination_directions", defaultIlluminations );
		
		if ( hasMultipleAngles )
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
		if ( hasMultipleTimePoints )
		{
			defaultTimepoints = timepoints = gd.getNextString();
			replaceTimepoints = IntegerPattern.getReplaceString( fileNamePattern, TIMEPOINT_PATTERN );
			
			if ( replaceTimepoints == null )
				throw new ParseException( "Pattern {" + TIMEPOINT_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several timepoints.", 0 );
			
			numDigitsTimepoints = replaceTimepoints.length() - 2;
		}

		if ( hasMultipleChannels )
		{
			defaultChannels = channels = gd.getNextString();
			replaceChannels = IntegerPattern.getReplaceString( fileNamePattern, CHANNEL_PATTERN );
			
			if ( replaceChannels == null )
				throw new ParseException( "Pattern {" + CHANNEL_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several channels.", 0 );
			
			numDigitsChannels = replaceChannels.length() - 2;
		}

		if ( hasMultipleIlluminations )
		{
			defaultIlluminations = illuminations = gd.getNextString();
			replaceIlluminations = IntegerPattern.getReplaceString( fileNamePattern, ILLUMINATION_PATTERN );
			
			if ( replaceIlluminations == null )
				throw new ParseException( "Pattern {" + ILLUMINATION_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several illumination directions.", 0 );

			numDigitsIlluminations = replaceIlluminations.length() - 2;
		}

		if ( hasMultipleAngles )
		{
			defaultAngles = angles = gd.getNextString();
			replaceAngles = IntegerPattern.getReplaceString( fileNamePattern, ANGLE_PATTERN );
			
			if ( replaceAngles == null )
				throw new ParseException( "Pattern {" + ANGLE_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several angles.", 0 );
			
			numDigitsAngles = replaceAngles.length() - 2;
		}

		// get the list of integers
		timepointList = IntegerPattern.parseIntegerString( timepoints );
		channelList = IntegerPattern.parseIntegerString( channels );
		illuminationsList = IntegerPattern.parseIntegerString( illuminations );
		angleList = IntegerPattern.parseIntegerString( angles );

		exceptionIds = new ArrayList< int[] >();
		
		defaultContainer = container = gd.getNextChoiceIndex();
		defaultCalibration = calibation = gd.getNextChoiceIndex();
		showDebugFileNames = gd.getNextBoolean();
		
		return true;		
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
				timepointList.get( tpID ), channelList.get( chID ), illuminationsList.get( illID ), angleList.get( angleID ) );
	}

	public static String getFileNameFor( String fileName, 
			final String replaceTimepoints, final String replaceChannels, 
			final String replaceIlluminations, final String replaceAngles, 
			final int tp, final int ch, final int ill, final int angle )
	{
		if ( replaceTimepoints != null )
			fileName = fileName.replace( replaceTimepoints, "" + tp );

		if ( replaceChannels != null )
			fileName = fileName.replace( replaceChannels, "" + ch );

		if ( replaceIlluminations != null )
			fileName = fileName.replace( replaceIlluminations, "" + ill );

		if ( replaceAngles != null )
			fileName = fileName.replace( replaceAngles, "" + angle );
		
		return fileName;
	}

	/**
	 * populates the fields calX, calY, calZ from the first file of the series
	 * 
	 * @return - true if successful
	 */
	protected boolean loadFirstCalibration()
	{
		for ( int t = 0; t < timepointList.size(); ++t )
			for ( int c = 0; c < channelList.size(); ++c )
				for ( int i = 0; i < illuminationsList.size(); ++i )
					for ( int a = 0; a < angleList.size(); ++a )
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
		
		if ( hasMultipleTimePoints )
			pattern += "_TL{t}";
		
		if ( hasMultipleChannels )
			pattern += "_Channel{c}";
		
		if ( hasMultipleIlluminations )
			pattern += "_Illum{i}";
		
		if ( hasMultipleAngles )
			pattern += "_Angle{a}";
		
		return pattern + ".tif";
	}
	
	protected boolean queryGeneralInformation()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (1/3)" );
		
		gd.addCheckbox( "Dataset_with_multiple_timepoints", defaultHasMultipleTimePoints );
		gd.addCheckbox( "Dataset_with_multiple_channels", defaultHasMultipleChannels );
		gd.addCheckbox( "Dataset_with_multiple_illumination_directions", defaultHasMultipleIlluminations );
		gd.addCheckbox( "Dataset_with_multiple_angles", defaultHasMultipleAngles );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		hasMultipleTimePoints = defaultHasMultipleTimePoints = gd.getNextBoolean();
		hasMultipleChannels = defaultHasMultipleChannels = gd.getNextBoolean();
		hasMultipleIlluminations = defaultHasMultipleIlluminations = gd.getNextBoolean();
		hasMultipleAngles = defaultHasMultipleAngles = gd.getNextBoolean();
		
		return true;
	}
	
	/**
	 * A copy of Curtis's method
	 * 
	 * https://github.com/openmicroscopy/bioformats/blob/v4.4.8/components/loci-plugins/src/loci/plugins/util/WindowTools.java#L72
	 * 
	 * <dependency>
     * <groupId>${bio-formats.groupId}</groupId>
     * <artifactId>loci_plugins</artifactId>
     * <version>${bio-formats.version}</version>
     * </dependency>
	 * 
	 * @param pane
	 */
	public static void addScrollBars(Container pane) {
		GridBagLayout layout = (GridBagLayout) pane.getLayout();

		// extract components
		int count = pane.getComponentCount();
		Component[] c = new Component[count];
		GridBagConstraints[] gbc = new GridBagConstraints[count];
		for (int i = 0; i < count; i++) {
			c[i] = pane.getComponent(i);
			gbc[i] = layout.getConstraints(c[i]);
		}

		// clear components
		pane.removeAll();
		layout.invalidateLayout(pane);

		// create new container panel
		Panel newPane = new Panel();
		GridBagLayout newLayout = new GridBagLayout();
		newPane.setLayout(newLayout);
		for (int i = 0; i < count; i++) {
			newLayout.setConstraints(c[i], gbc[i]);
			newPane.add(c[i]);
		}

		// HACK - get preferred size for container panel
		// NB: don't know a better way:
		// - newPane.getPreferredSize() doesn't work
		// - newLayout.preferredLayoutSize(newPane) doesn't work
		Frame f = new Frame();
		f.setLayout(new BorderLayout());
		f.add(newPane, BorderLayout.CENTER);
		f.pack();
		final Dimension size = newPane.getSize();
		f.remove(newPane);
		f.dispose();

		// compute best size for scrollable viewport
		size.width += 25;
		size.height += 15;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int maxWidth = 7 * screen.width / 8;
		int maxHeight = 3 * screen.height / 4;
		if (size.width > maxWidth)
			size.width = maxWidth;
		if (size.height > maxHeight)
			size.height = maxHeight;

		// create scroll pane
		ScrollPane scroll = new ScrollPane() {
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize() {
				return size;
			}
		};
		scroll.add(newPane);

		// add scroll pane to original container
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.weightx = 1.0;
		constraints.weighty = 1.0;
		layout.setConstraints(scroll, constraints);
		pane.add(scroll);
	}	
}
