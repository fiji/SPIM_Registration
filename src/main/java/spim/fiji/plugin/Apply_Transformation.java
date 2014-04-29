package spim.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.spimdata.SpimData2;

public class Apply_Transformation implements PlugIn
{
	public static boolean defaultSameModelTimePoints = true;
	public static boolean defaultSameModelChannels = true;
	public static boolean defaultSameModelIlluminations = true;
	public static boolean defaultSameModelAngles = false;
	
	public static int defaultModel = 1;
	public static int defaultDefineAs = 1;
	public static int defaultApplyTo = 1;
	
	public static String[] inputChoice = new String[]{
		"Identity transform (resets existing transform)",
		"Calibration only (resets existing transform)",
		"Current view transformations (appends to current transform)" };	
	public static String[] modelChoice = new String[]{ "Translation", "Rigid", "Affine" };
	public static String[] defineChoice = new String[] { "Vector/matrix", "Rotation around axis (rigid only)" };
	
	public String[] axes = new String[] { "x-axis", "y-axis", "z-axis" };
	public static int defaultAxis = 0;
	public static double defaultDegrees = 90;

	public static ArrayList< double[] > defaultModels;

	public class ModelDescription
	{
		public String title;
		public AffineTransform3D model;
	}
	
	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "applying a transformation", "Apply to", true, true, true, true );
		
		if ( result == null )
			return;
		
		final boolean multipleTimePoints = result.getTimePointsToProcess().size() > 1;
		final boolean multipleChannels = result.getChannelsToProcess().size() > 1;
		final boolean multipleIlluminations = result.getIlluminationsToProcess().size() > 1;
		final boolean multipleAngles = result.getAnglesToProcess().size() > 1;
		
		final GenericDialog gd = new GenericDialog( "Choose type of application" );
		
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
		gd.addChoice( "Define as", defineChoice, defineChoice[ defaultDefineAs ] );
		gd.addChoice( "Apply to", inputChoice, inputChoice[ defaultApplyTo ] );
		
		if ( multipleTimePoints )
			gd.addCheckbox( "Same_transformation_for_all_timepoints", defaultSameModelTimePoints );
		
		if ( multipleChannels )
			gd.addCheckbox( "Same_transformation_for_all_channels", defaultSameModelChannels );
		
		if ( multipleIlluminations )
			gd.addCheckbox( "Same_transformation_for_all_illuminations", defaultSameModelIlluminations );
		
		if ( multipleAngles )
			gd.addCheckbox( "Same_transformation_for_all_angles", defaultSameModelAngles );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
	
		final int model = defaultModel = gd.getNextChoiceIndex();
		final int defineAs = defaultDefineAs = gd.getNextChoiceIndex();
		final int applyTo = defaultApplyTo = gd.getNextChoiceIndex();
		
		final boolean sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles;
		
		if ( multipleTimePoints )
			sameModelTimePoints = defaultSameModelTimePoints = gd.getNextBoolean();
		else
			sameModelTimePoints = true;

		if ( multipleChannels )
			sameModelChannels = defaultSameModelChannels = gd.getNextBoolean();
		else
			sameModelChannels = true;
		
		if ( multipleIlluminations )
			sameModelIlluminations = defaultSameModelIlluminations = gd.getNextBoolean();
		else
			sameModelIlluminations = true;
		
		if ( multipleAngles )
			sameModelAngles = defaultSameModelAngles = gd.getNextBoolean();
		else
			sameModelAngles = true;
		
		// reset the transform, in this case we need to make sure the calibration is actually available
		final double minResolution;
		if ( applyTo == 1 )
		{
			minResolution = assembleAllMetaData( result.getData(), result.getTimePointsToProcess(), result.getChannelsToProcess(), result.getIlluminationsToProcess(), result.getAnglesToProcess() );
			
			if ( Double.isNaN( minResolution ) )
				return;
		}
		else
		{
			minResolution = 1;
		}
		
		// query models and apply them
		if ( defineAs == 0 )
			queryString( model, applyTo, minResolution, result, sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles );
		else
			queryRotationAxis( model, sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles );
		
		// now save it
		Interest_Point_Registration.saveXML( result.getData(), result.getXMLFileName() );
	}

	protected boolean queryString( final int model, final int applyTo, final double minResolution, final XMLParseResult result, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	{
		final HashMap< String, List< TimePoint > > timepoints = getTimePoints( result.getTimePointsToProcess(), sameModelTimePoints );
		final HashMap< String, List< Channel > > channels = getChannels( result.getChannelsToProcess(), sameModelChannels );
		final HashMap< String, List< Illumination > > illums = getIlluminations( result.getIlluminationsToProcess(), sameModelIlluminations );
		final HashMap< String, List< Angle > > angles = getAngles( result.getAnglesToProcess(), sameModelAngles );
		
		final int numEntries = getNumEntries( timepoints, channels, illums, angles );
		
		if ( defaultModels == null )
		{
			defaultModels = new ArrayList< double[] >();
			
			for ( int i = 0; i < numEntries; ++i )
				defaultModels.add( new double[]{ 1, 0, 0, 0,    0, 1, 0, 0,    0, 0, 1, 0 } );
		}
		else if ( defaultModels.size() < numEntries )
		{
			for ( int i = defaultModels.size(); i < numEntries; ++i )
				defaultModels.add( new double[]{ 1, 0, 0, 0,    0, 1, 0, 0,    0, 0, 1, 0 } );
		}
		
		final GenericDialog gd;
		
		if ( model == 0 )
		{
			gd = new GenericDialog( "Model parameters for translation model 3d" );

			gd.addMessage( "t(x) = m03, t(y) = m13, t(z) = m23" );
			gd.addMessage( "" );
			gd.addMessage( "Please provide 3d translation in this form (any brackets will be ignored): m03, m13, m23" );
			
			int j = 0;
			
			for ( final String t : timepoints.keySet() )
				for ( final String c : channels.keySet() )
					for ( final String i : illums.keySet() )
						for ( final String a : angles.keySet() )
							gd.addStringField( t + "_" + c + "_" + i + "_" + a, defaultModels.get( j )[ 3 ] + ", " + defaultModels.get( j )[ 7 ] + ", " + defaultModels.get( j++ )[ 11 ], 30 );
		}
		else
		{
			gd = new GenericDialog( "Model parameters for rigid/affine model 3d" );

			gd.addMessage( "| m00 m01 m02 m03 |\n" +
				"| m10 m11 m12 m13 |\n" +
				"| m20 m21 m22 m23 |" );
			gd.addMessage( "Please provide 3d rigid/affine in this form (any brackets will be ignored):\n" +
				"m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23" );
			
			int j = 0;
			
			for ( final String t : timepoints.keySet() )
				for ( final String c : channels.keySet() )
					for ( final String i : illums.keySet() )
						for ( final String a : angles.keySet() )
						{
							final double[] m = defaultModels.get( j++ );
							gd.addStringField( t + "_" + c + "_" + i + "_" + a, 
									m[ 0 ] + ", " + m[ 1 ] + ", " + m[ 2 ] + ", " + m[ 3 ] + ", " + 
									m[ 4 ] + ", " + m[ 5 ] + ", " + m[ 6 ] + ", " + m[ 7 ] + ", " + 
									m[ 8 ] + ", " + m[ 9 ] + ", " + m[ 10 ] + ", " + m[ 11 ], 80 );
						}
		}
		
		if ( numEntries > 10 )
			GUIHelper.addScrollBars( gd );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		final ArrayList< double[] > models = new ArrayList< double[] >();
		
		if ( model == 0 )
		{
			for ( int j = 0; j < numEntries; ++j )
			{
				final double[] v = parseString( gd.getNextString(), 3 );
				
				if ( v == null )
					return false;
				else
					models.add( new double[]{ 1, 0, 0, v[ 0 ],    0, 1, 0, v[ 1 ],    0, 0, 1, v[ 2 ] } );
			}
				
		}
		else
		{
			for ( int j = 0; j < numEntries; ++j )
			{
				final double[] v = parseString( gd.getNextString(), 12 );

				if ( v == null )
					return false;
				else
					models.add( v );
			}
		}
		
		// set the defaults
		defaultModels = models;
		
		// apply the models as asked
		return applyModels( result.getData(), models, applyTo, minResolution, timepoints, channels, illums, angles );
	}
	
	protected boolean applyModels(
			final SpimData2 spimData,
			final ArrayList< double[] > models,
			final int applyTo,
			final double minResolution,
			final HashMap< String, List< TimePoint > > timepoints, 
			final HashMap< String, List< Channel > > channels, 
			final HashMap< String, List< Illumination > > illums, 
			final HashMap< String, List< Angle > > angles )
	{
		int j = 0;
		
		for ( final String ts : timepoints.keySet() )
			for ( final String cs : channels.keySet() )
				for ( final String is : illums.keySet() )
					for ( final String as : angles.keySet() )
					{
						final List< TimePoint > tl = timepoints.get( ts );
						final List< Channel > cl = channels.get( cs );
						final List< Illumination > il = illums.get( is );
						final List< Angle > al = angles.get( as );
						
						final double[] v = models.get( j++ );
						
						for ( final TimePoint t : tl )
							for ( final Channel c : cl )
								for ( final Illumination i : il )
									for ( final Angle a : al )
									{
										final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
										
										final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
												viewId.getTimePointId(), viewId.getViewSetupId() );

										if ( !viewDescription.isPresent() )
											continue;

										
										if ( applyTo == 0 )
										{
											IOFunctions.println( "Reseting model to idenity transform for timepoint " + t.getName() + ", channel " + c.getName() + ", illum " + i.getName() + ", angle " + a.getName() );
											setModelToIdentity( spimData, viewId );
										}
										
										if ( applyTo == 1 )
										{
											IOFunctions.println( "Reseting model to calibration for timepoint " + t.getName() + ", channel " + c.getName() + ", illum " + i.getName() + ", angle " + a.getName() );
											setModelToCalibration( spimData, viewId, minResolution );
										}
										
										IOFunctions.println( "Applying model " + Util.printCoordinates( v ) + " to timepoint " + t.getName() + ", channel " + c.getName() + ", illum " + i.getName() + ", angle " + a.getName() );
										
										final AffineTransform3D model = new AffineTransform3D();
										model.set( v );
										
										preConcatenateTransform(spimData, viewId, model, "Manually defined transformation" );
									}
					}
		return true;
	}
		
	protected double[] parseString( String entry, final int numValues )
	{
		while ( entry.contains( "(" ) )
			entry.replace( "(", "" );

		while ( entry.contains( ")" ) )
			entry.replace( ")", "" );

		while ( entry.contains( "[" ) )
			entry.replace( "[", "" );

		while ( entry.contains( "]" ) )
			entry.replace( "]", "" );

		entry = entry.replaceAll( " ", "" ).trim();
		
		String[] entries = entry.split( "," );

		if ( entries.length != numValues )
		{
			IOFunctions.println( "Cannot parse: '" + entry + "', has " + entries.length + " numbers, but should be " + numValues );
			return null;
		}
		
		final double[] v = new double[ numValues ];
		
		for ( int j = 0; j < numValues; ++j )
			v[ j ] = Double.parseDouble( entries[ j ].trim() );
		
		return v;
	}

	protected ModelDescription queryRotationAxis( final int model, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	protected int getNumEntries( final HashMap< String, List< TimePoint > > t, final HashMap< String, List< Channel > > c, final HashMap< String, List< Illumination > > i, final HashMap< String, List< Angle > > a )
	{
		return t.keySet().size() * c.keySet().size() * i.keySet().size() * a.keySet().size();
	}
	
	protected HashMap< String, List< TimePoint > > getTimePoints( final List< TimePoint > tps, final boolean sameModelTimePoints )
	{
		final HashMap< String, List< TimePoint > > h = new HashMap< String, List< TimePoint > >();
		
		if ( sameModelTimePoints && tps.size() > 1 )
			h.put( "all_timepoints", tps );
		else
		{
			for ( final TimePoint t : tps )
			{
				final ArrayList< TimePoint > tpl = new ArrayList< TimePoint >();
				tpl.add( t );
				
				h.put( "timepoint_" + t.getName(), tpl );
			}
		}
		
		return h;
	}

	protected HashMap< String, List< Channel > > getChannels( final List< Channel > channels, final boolean sameModelChannels )
	{
		final HashMap< String, List< Channel > > h = new HashMap< String, List< Channel > >();
		
		if ( sameModelChannels && channels.size() > 1 )
			h.put( "all_channels", channels );
		else
		{
			for ( final Channel c : channels )
			{
				final ArrayList< Channel > chl = new ArrayList< Channel >();
				chl.add( c );
				
				h.put( "channel_" + c.getName(), chl );
			}
		}
		
		return h;
	}

	protected HashMap< String, List< Illumination > > getIlluminations( final List< Illumination > illums, final boolean sameModelIlluminations )
	{
		final HashMap< String, List< Illumination > > h = new HashMap< String, List< Illumination > >();
		
		if ( sameModelIlluminations && illums.size() > 1 )
			h.put( "all_illuminations", illums );
		else
		{
			for ( final Illumination i : illums )
			{
				final ArrayList< Illumination > ill = new ArrayList< Illumination >();
				ill.add( i );
				
				h.put( "illumination_" + i.getName(), ill );
			}
		}
		
		return h;
	}

	protected HashMap< String, List< Angle > > getAngles( final List< Angle > angles, final boolean sameModelAngles )
	{
		final HashMap< String, List< Angle > > h = new HashMap< String, List< Angle > >();
		
		if ( sameModelAngles && angles.size() > 1 )
			h.put( "all_angles", angles );
		else
		{
			for ( final Angle a : angles )
			{
				final ArrayList< Angle > al = new ArrayList< Angle >();
				al.add( a );
				
				h.put( "angle_" + a.getName(), al );
			}
		}
		
		return h;
	}

	public static void preConcatenateTransform( final SpimData2 spimData, final ViewId viewId, final AffineTransform3D model, final String name )
	{
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();

		// update the view registration
		final ViewRegistration vr = viewRegistrations.getViewRegistration( viewId );
		final ViewTransform vt = new ViewTransformAffine( name, model );
		vr.preconcatenateTransform( vt );
	}
	
	public static void setModelToCalibration( final SpimData2 spimData, final ViewId viewId, final double minResolution )
	{
		setModelToIdentity( spimData, viewId );
		
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();
		final ViewRegistration r = viewRegistrations.getViewRegistration( viewId );
		
		final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
				viewId.getTimePointId(), viewId.getViewSetupId() );

		final double calX = viewDescription.getViewSetup().getPixelWidth() / minResolution;
		final double calY = viewDescription.getViewSetup().getPixelHeight() / minResolution;
		final double calZ = viewDescription.getViewSetup().getPixelDepth() / minResolution;
		
		final AffineTransform3D m = new AffineTransform3D();
		m.set( calX, 0.0f, 0.0f, 0.0f, 
			   0.0f, calY, 0.0f, 0.0f,
			   0.0f, 0.0f, calZ, 0.0f );
		final ViewTransform vt = new ViewTransformAffine( "calibration", m );
		r.preconcatenateTransform( vt );
	}

	public static void setModelToIdentity( final SpimData2 spimData, final ViewId viewId )
	{
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();
		final ViewRegistration r = viewRegistrations.getViewRegistration( viewId );
		r.identity();
	}

	/**
	 * Should be called before registration to make sure all metadata is right
	 * 
	 * @return - minimal resolution in all dimensions
	 */
	public static double assembleAllMetaData(
			final SpimData2 spimData,
			final ArrayList< TimePoint > timepointsToProcess, 
			final ArrayList< Channel > channelsToProcess,
			final ArrayList< Illumination > illumsToProcess,
			final ArrayList< Angle > anglesToProcess )
	{
		double minResolution = Double.MAX_VALUE;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						// bureaucracy
						final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
						
						if ( viewId == null )
						{
							IOFunctions.println( "An error occured. Could not find the corresponding ViewSetup for timepoint: " + t.getName() + " angle: " + 
									a.getName() + " channel: " + c.getName() + " illum: " + i.getName() );
						
							return Double.NaN;
						}
						
						final ViewDescription< TimePoint, ViewSetup > viewDescription = spimData.getSequenceDescription().getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						if ( !viewDescription.isPresent() )
							continue;
						
						// load metadata to update the registrations if required
						// only use calibration as defined in the metadata
						if ( !calibrationAvailable( viewDescription.getViewSetup() ) )
						{
							if ( !spimData.getSequenceDescription().getImgLoader().loadMetaData( viewDescription ) )
							{
								IOFunctions.println( "An error occured. Cannot load calibration for timepoint: " + t.getName() + " angle: " + 
										a.getName() + " channel: " + c.getName() + " illum: " + i.getName() );
								
								IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML" );
								
								return Double.NaN;
							}						
						}

						if ( !calibrationAvailable( viewDescription.getViewSetup() ) )
						{
							IOFunctions.println( "An error occured. No calibration available for timepoint: " + t.getName() + " angle: " + 
									a.getName() + " channel: " + c.getName() + " illum: " + i.getName() );
							
							IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML." );
							IOFunctions.println( "Note: if you selected to load calibration independently for each image, it should." );
							IOFunctions.println( "      have been loaded during interest point detection." );
							
							return Double.NaN;
						}
						
						final double calX = viewDescription.getViewSetup().getPixelWidth();
						final double calY = viewDescription.getViewSetup().getPixelHeight();
						final double calZ = viewDescription.getViewSetup().getPixelDepth();
						
						minResolution = Math.min( minResolution, calX );
						minResolution = Math.min( minResolution, calY );
						minResolution = Math.min( minResolution, calZ );
					}
		
		return minResolution;
	}

	public static boolean calibrationAvailable( final ViewSetup viewSetup )
	{
		if ( viewSetup.getPixelWidth() <= 0 || viewSetup.getPixelHeight() <= 0 || viewSetup.getPixelDepth() <= 0 )
			return false;
		else
			return true;
	}	

	/**
	 * @param args
	 */
	public static void main( final String[] args )
	{
		new Apply_Transformation().run( null );
	}
}
