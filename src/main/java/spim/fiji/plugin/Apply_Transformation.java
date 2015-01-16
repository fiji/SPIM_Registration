package spim.fiji.plugin;

import bdv.BigDataViewer;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.media.j3d.Transform3D;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;
import spim.fiji.plugin.apply.BigDataViewerTransformationWindow;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;

public class Apply_Transformation implements PlugIn
{
	public static boolean defaultSameModelTimePoints = true;
	public static boolean defaultSameModelChannels = true;
	public static boolean defaultSameModelIlluminations = true;
	public static boolean defaultSameModelAngles = true;
	
	public static int defaultModel = 2;
	public static int defaultDefineAs = 2;
	public static int defaultApplyTo = 2;
	
	public static String[] inputChoice = new String[]{
		"Identity transform (removes any existing transforms)",
		"Calibration (removes any existing transforms)",
		"Current view transformations (appends to current transforms)" };	
	public static String[] modelChoice = new String[]{ "Identity (no transformation)", "Translation", "Rigid", "Affine" };
	public static String[] defineChoice = new String[] { "Matrix", "Rotation around axis", "Interactively using the BigDataViewer" };
	
	public String[] axesChoice = new String[] { "x-axis", "y-axis", "z-axis" };
	public static int[] defaultAxis = null;
	public static double[] defaultDegrees = null;

	public static ArrayList< double[] > defaultModels;

	public class Entry implements Comparable< Entry >
	{
		public String title;
		public int id;
		
		public Entry( final int id, final String title )
		{
			this.id = id;
			this.title = title;
		}
		
		public String getTitle() { return title; }
		
		@Override
		public int hashCode() { return id; }
		
		@Override
		public boolean equals( final Object o )
		{
			if ( o == null )
			{
				return false;
			}
			else if ( o instanceof Entry )
			{
				if ( ((Entry)o).id == id )
					return true;
				else
					return false;
			}
			else
			{
				return false;
			}
		}
		
		@Override
		public int compareTo( final Entry o ) { return id - o.id; }
	}
	
	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "applying a transformation", "Apply to", true, true, true, true ) )
			return;
		
		final boolean multipleTimePoints = result.getTimePointsToProcess().size() > 1;
		final boolean multipleChannels = result.getChannelsToProcess().size() > 1;
		final boolean multipleIlluminations = result.getIlluminationsToProcess().size() > 1;
		final boolean multipleAngles = result.getAnglesToProcess().size() > 1;
		
		final GenericDialog gd = new GenericDialog( "Choose transformation model" );
		
		gd.addChoice( "Transformation model", modelChoice, modelChoice[ defaultModel ] );
		gd.addChoice( "Apply on top of", inputChoice, inputChoice[ defaultApplyTo ] );
		
		if ( multipleTimePoints )
			gd.addCheckbox( "Same_transformation_for_all_timepoints", defaultSameModelTimePoints );
		
		if ( multipleChannels )
			gd.addCheckbox( "Same_transformation_for_all_channels", defaultSameModelChannels );
		
		if ( multipleIlluminations )
			gd.addCheckbox( "Same_transformation_for_all_illuminations", defaultSameModelIlluminations );
		
		if ( multipleAngles )
			gd.addCheckbox( "Same_transformation_for_all_angles", defaultSameModelAngles );

		gd.addMessage(
				"Interactive application of transformations using the BigDataViewer is available when selecting a Rigid Model.",
				GUIHelper.mediumstatusfont );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
	
		final int model = defaultModel = gd.getNextChoiceIndex();
		final int applyTo = defaultApplyTo = gd.getNextChoiceIndex();
		final int defineAs;
		
		if ( model == 2 ) // rigid
		{
			final GenericDialog gd2 = new GenericDialog( "Choose application for transformation model" );
			gd2.addChoice( "Define as", defineChoice, defineChoice[ defaultDefineAs ] );

			gd2.showDialog();
			
			if ( gd2.wasCanceled() )
				return;

			defineAs = defaultDefineAs = gd2.getNextChoiceIndex();
		}
		else
		{
			defineAs = 0;
		}
		
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
		
		// reset the transform to the calibration (x,y,z resolution), in this case we need to make sure the calibration is actually available
		final double minResolution;
		if ( applyTo == 1 )
		{
			minResolution = assembleAllMetaData( result.getData().getSequenceDescription(), result.getTimePointsToProcess(), result.getChannelsToProcess(), result.getIlluminationsToProcess(), result.getAnglesToProcess() );
			
			if ( Double.isNaN( minResolution ) )
			{
				IOFunctions.println( "Could not assemble the calibration, quitting." );
				return;
			}
		}
		else
		{
			minResolution = 1;
		}
		
		// query models and apply them
		if ( defineAs == 0 )
		{
			if ( !queryString( model, applyTo, minResolution, result, sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles ) )
				return;
		}
		else if ( defineAs == 1 )
		{
			if ( !queryRotationAxis( model, applyTo, minResolution, result, sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles ) )
				return;
		}
		else
		{
			if ( !sameModelAngles || !sameModelChannels || !sameModelIlluminations || !sameModelIlluminations )
			{
				IOFunctions.println( "You selected to not have the same transformation model for all views in the" );
				IOFunctions.println( "previous dialog. This is not supported using the interactive setup using the" );
				IOFunctions.println( "BigDataViewer. You can select single views in the xml open dialog though." );

				return;
			}

			if ( !queryBigDataViewer( applyTo, minResolution, result ) )
				return;
		}
		
		// now save it
		Interest_Point_Registration.saveXML( result.getData(), result.getXMLFileName(), result.getClusterExtension() );
	}

	protected boolean queryBigDataViewer( final int applyTo, final double minResolution, final LoadParseQueryXML result )
	{
		try
		{
			final BigDataViewer bdv = new BigDataViewer( result.getXMLFileName(), "Set dataset transformation", null );

			try
			{
				Thread.sleep( 1000 );
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}

			final BigDataViewerTransformationWindow bdvw = new BigDataViewerTransformationWindow( bdv );

			do
			{
				try
				{
					Thread.sleep( 100 );
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			while ( bdvw.isRunning() );

			BigDataViewerTransformationWindow.disposeViewerWindow( bdv );

			if ( bdvw.wasCancelled() )
				return false;

			final ArrayList< double[] > models = new ArrayList< double[] >();
			final ArrayList< String > modelDescriptions = new ArrayList< String >();

			final AffineTransform3D t = bdvw.getTransform();
			models.add( t.getRowPackedCopy() );
			modelDescriptions.add( "Rigid transform defined by BigDataViewer" );

			final HashMap< Entry, List< TimePoint > > timepoints = getTimePoints( result.getTimePointsToProcess(), true );
			final HashMap< Entry, List< Channel > > channels = getChannels( result.getChannelsToProcess(), true );
			final HashMap< Entry, List< Illumination > > illums = getIlluminations( result.getIlluminationsToProcess(), true );
			final HashMap< Entry, List< Angle > > angles = getAngles( result.getAnglesToProcess(), true );

			return applyModels( result.getData(), models, modelDescriptions, applyTo, minResolution, timepoints, channels, illums, angles );
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "Failed to run the BigDataViewer ... " );
			e.printStackTrace();
			return false;
		}
	}

	protected boolean queryRotationAxis( final int model, final int applyTo, final double minResolution, final LoadParseQueryXML result, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	{
		if ( model != 2 )
		{
			IOFunctions.println( "No rigid model selected." );
			return false;
		}

		final HashMap< Entry, List< TimePoint > > timepoints = getTimePoints( result.getTimePointsToProcess(), sameModelTimePoints );
		final HashMap< Entry, List< Channel > > channels = getChannels( result.getChannelsToProcess(), sameModelChannels );
		final HashMap< Entry, List< Illumination > > illums = getIlluminations( result.getIlluminationsToProcess(), sameModelIlluminations );
		final HashMap< Entry, List< Angle > > angles = getAngles( result.getAnglesToProcess(), sameModelAngles );
		
		final int numEntries = getNumEntries( timepoints, channels, illums, angles );

		if ( defaultAxis == null || defaultDegrees == null || defaultAxis.length != numEntries || defaultDegrees.length != numEntries )
		{
			defaultAxis = new int[ numEntries ];
			defaultDegrees = new double[ numEntries ];

			int j = 0;
			
			for ( int t = 0; t < sortedList( timepoints.keySet() ).size(); ++t )
				for ( int c = 0; c < sortedList( channels.keySet() ).size(); ++c )
					for ( int i = 0; i < sortedList( illums.keySet() ).size(); ++i )
						for ( final Entry a : sortedList( angles.keySet() ) )
						{
							// it is for one individual angle, let's set the name as default rotation
							if ( angles.get( a ).size() == 1 )
							{
								try
								{
									defaultDegrees[ j ] = Double.parseDouble( angles.get( a ).get( 0 ).getName() );
								}
								catch ( Exception e ) {};
							}
							
							++j;
						}
		}
		
		final GenericDialog gd = new GenericDialog( "Parameters for rigid model 3d" );

		int j = 0;
		
		for ( final Entry t : sortedList( timepoints.keySet() ) )
			for ( final Entry c : sortedList( channels.keySet() ) )
				for ( final Entry i : sortedList( illums.keySet() ) )
					for ( final Entry a : sortedList( angles.keySet() ) )
					{
						gd.addChoice( "Axis_" + t.getTitle() + "_" + c.getTitle() + "_" + i.getTitle() + "_" + a.getTitle(), axesChoice, axesChoice[ defaultAxis[ j ] ] );
						gd.addSlider( "Rotation_" + t.getTitle() + "_" + c.getTitle() + "_" + i.getTitle() + "_" + a.getTitle(), -360, 360, defaultDegrees[ j++ ] );
					}

		if ( numEntries > 5 )
			GUIHelper.addScrollBars( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final int[] axes = new int[ numEntries ];
		final double[] degrees = new double[ numEntries ];
		final ArrayList< double[] > models = new ArrayList< double[] >();
		final ArrayList< String > modelDescriptions = new ArrayList< String >();
		
		final double[] tmp = new double[ 16 ];
		
		for ( j = 0; j < numEntries; ++j )
		{
			axes[ j ] = gd.getNextChoiceIndex();
			degrees[ j ] = gd.getNextNumber();
			
			final Transform3D t = new Transform3D();
			if ( axes[ j ] == 0 )
			{
				t.rotX( Math.toRadians( degrees[ j ] ) );
				modelDescriptions.add( "Rotation around x-axis by " + degrees[ j ] + " degrees" );	
			}
			else if ( axes[ j ] == 1 )
			{
				t.rotY( Math.toRadians( degrees[ j ] ) );
				modelDescriptions.add( "Rotation around y-axis by " + degrees[ j ] + " degrees" );	
			}
			else
			{
				t.rotZ( Math.toRadians( degrees[ j ] ) );
				modelDescriptions.add( "Rotation around z-axis by " + degrees[ j ] + " degrees" );	
			}

			t.get( tmp );

			models.add( new double[]{
					tmp[ 0 ], tmp[ 1 ], tmp[ 2 ], tmp[ 3 ],
					tmp[ 4 ], tmp[ 5 ], tmp[ 6 ], tmp[ 7 ],
					tmp[ 8 ], tmp[ 9 ], tmp[ 10 ], tmp[ 11 ] } );			
		}
		
		// set defaults
		defaultAxis = axes;
		defaultDegrees = degrees;
		
		defaultModels = models;
		
		// apply the models as asked
		return applyModels( result.getData(), models, modelDescriptions, applyTo, minResolution, timepoints, channels, illums, angles );
	}

	protected boolean queryString( final int model, final int applyTo, final double minResolution, final LoadParseQueryXML result, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	{
		final HashMap< Entry, List< TimePoint > > timepoints = getTimePoints( result.getTimePointsToProcess(), sameModelTimePoints );
		final HashMap< Entry, List< Channel > > channels = getChannels( result.getChannelsToProcess(), sameModelChannels );
		final HashMap< Entry, List< Illumination > > illums = getIlluminations( result.getIlluminationsToProcess(), sameModelIlluminations );
		final HashMap< Entry, List< Angle > > angles = getAngles( result.getAnglesToProcess(), sameModelAngles );
		
		final int numEntries = getNumEntries( timepoints, channels, illums, angles );

		IOFunctions.println( "Querying " + numEntries + " different transformation models." );

		if ( defaultModels == null || defaultModels.size() == 0 || defaultModels.size() != numEntries )
		{
			defaultModels = new ArrayList< double[] >();

			for ( int i = 0; i < numEntries; ++i )
				defaultModels.add( new double[]{ 1, 0, 0, 0,    0, 1, 0, 0,    0, 0, 1, 0 } );
		}

		final GenericDialog gd;
		
		if ( model == 0 ) // identity transform
		{
			gd = null;
		}
		else if ( model == 1 )
		{
			gd = new GenericDialog( "Model parameters for translation model 3d" );

			gd.addMessage( "t(x) = m03, t(y) = m13, t(z) = m23" );
			gd.addMessage( "" );
			gd.addMessage( "Please provide 3d translation in this form (any brackets will be ignored): m03, m13, m23" );
			
			int j = 0;
			
			for ( final Entry t : sortedList( timepoints.keySet() ) )
				for ( final Entry c : sortedList( channels.keySet() ) )
					for ( final Entry i : sortedList( illums.keySet() ) )
						for ( final Entry a : sortedList( angles.keySet() ) )
							gd.addStringField( t.getTitle() + "_" + c.getTitle() + "_" + i.getTitle() + "_" + a.getTitle(), defaultModels.get( j )[ 3 ] + ", " + defaultModels.get( j )[ 7 ] + ", " + defaultModels.get( j++ )[ 11 ], 30 );
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
			
			for ( final Entry t : sortedList( timepoints.keySet() ) )
				for ( final Entry c : sortedList( channels.keySet() ) )
					for ( final Entry i : sortedList( illums.keySet() ) )
						for ( final Entry a : sortedList( angles.keySet() ) )
						{
							final double[] m = defaultModels.get( j++ );
							gd.addStringField( t.getTitle() + "_" + c.getTitle() + "_" + i.getTitle() + "_" + a.getTitle(), 
									m[ 0 ] + ", " + m[ 1 ] + ", " + m[ 2 ] + ", " + m[ 3 ] + ", " + 
									m[ 4 ] + ", " + m[ 5 ] + ", " + m[ 6 ] + ", " + m[ 7 ] + ", " + 
									m[ 8 ] + ", " + m[ 9 ] + ", " + m[ 10 ] + ", " + m[ 11 ], 80 );
						}
		}
		
		if ( gd != null )
		{
			if ( numEntries > 10 )
				GUIHelper.addScrollBars( gd );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return false;
		}
		
		final ArrayList< double[] > models = new ArrayList< double[] >();
		final ArrayList< String > modelDescriptions = new ArrayList< String >();
		
		if ( model == 0 )
		{
			for ( int j = 0; j < numEntries; ++j )
				models.add( null );
		}
		else if ( model == 1 )
		{
			for ( int j = 0; j < numEntries; ++j )
			{
				final double[] v = parseString( gd.getNextString(), 3 );
				
				if ( v == null )
					return false;
				else
				{
					models.add( new double[]{ 1, 0, 0, v[ 0 ], 0, 1, 0, v[ 1 ], 0, 0, 1, v[ 2 ] } );
					modelDescriptions.add( "Translation [" + v[ 0 ] + "," + v[ 1 ] + "," + v[ 2 ] + "]" );
				}
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
				{
					models.add( v );
					modelDescriptions.add( "Rigid/Affine by matrix" );
				}
			}
		}
		
		// set the defaults
		defaultModels = models;
		
		// apply the models as asked
		return applyModels( result.getData(), models, modelDescriptions, applyTo, minResolution, timepoints, channels, illums, angles );
	}
	
	protected boolean applyModels(
			final SpimData2 spimData,
			final ArrayList< double[] > models,
			final ArrayList< String > modelDescriptions,
			final int applyTo,
			final double minResolution,
			final HashMap< Entry, List< TimePoint > > timepoints, 
			final HashMap< Entry, List< Channel > > channels, 
			final HashMap< Entry, List< Illumination > > illums, 
			final HashMap< Entry, List< Angle > > angles )
	{
		int j = 0;
		
		// needs to be sorted as well it relies on the index j for accessing the transforms
		for ( final Entry ts : sortedList( timepoints.keySet() ) )
			for ( final Entry cs : sortedList( channels.keySet() ) )
				for ( final Entry is : sortedList( illums.keySet() ) )
					for ( final Entry as : sortedList( angles.keySet() ) )
					{
						final List< TimePoint > tl = timepoints.get( ts );
						final List< Channel > cl = channels.get( cs );
						final List< Illumination > il = illums.get( is );
						final List< Angle > al = angles.get( as );
						
						final double[] v = models.get( j );
						final String modelDesc;
						
						if ( modelDescriptions == null || modelDescriptions.size() == 0 )
							modelDesc = "";
						else
							modelDesc = modelDescriptions.get( j );

						++j;
						
						for ( final TimePoint t : tl )
							for ( final Channel c : cl )
								for ( final Illumination i : il )
									for ( final Angle a : al )
									{
										final ViewId viewId = SpimData2.getViewId( spimData.getSequenceDescription(), t, c, a, i );
										
										// this happens only if a viewsetup is not present in any timepoint
										// (e.g. after appending fusion to a dataset)
										if ( viewId == null )
											continue;

										final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
												viewId.getTimePointId(), viewId.getViewSetupId() );

										if ( !viewDescription.isPresent() )
											continue;
										
										if ( applyTo == 0 )
										{
											IOFunctions.println( "Reseting model to identity transform for timepoint " + t.getName() + ", channel " + c.getName() + ", illum " + i.getName() + ", angle " + a.getName() );
											setModelToIdentity( spimData, viewId );
										}
										
										if ( applyTo == 1 )
										{
											IOFunctions.println( "Reseting model to calibration for timepoint " + t.getName() + ", channel " + c.getName() + ", illum " + i.getName() + ", angle " + a.getName() );
											setModelToCalibration( spimData, viewId, minResolution );
										}
										
										if ( v != null )
										{
											IOFunctions.println( "Applying model " + Util.printCoordinates( v ) + " (" + modelDesc + ") to timepoint " + t.getName() + ", channel " + c.getName() + ", illum " + i.getName() + ", angle " + a.getName() );
											
											final AffineTransform3D model = new AffineTransform3D();
											model.set( v );
											
											preConcatenateTransform(spimData, viewId, model, "Manually defined transformation (" + modelDesc + ")" );
										}
									}
					}
		return true;
	}
	
	public static ArrayList< Entry > sortedList( final Set< Entry > set )
	{
		final ArrayList< Entry > list = new ArrayList< Entry >( set );
		Collections.sort( list );
		
		return list;
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
	
	protected int getNumEntries( final HashMap< Entry, List< TimePoint > > t, final HashMap< Entry, List< Channel > > c, final HashMap< Entry, List< Illumination > > i, final HashMap< Entry, List< Angle > > a )
	{
		return t.keySet().size() * c.keySet().size() * i.keySet().size() * a.keySet().size();
	}
	
	protected HashMap< Entry, List< TimePoint > > getTimePoints( final List< TimePoint > tps, final boolean sameModelTimePoints )
	{
		final HashMap< Entry, List< TimePoint > > h = new HashMap< Entry, List< TimePoint > >();
		
		if ( sameModelTimePoints && tps.size() > 1 )
			h.put( new Entry( -1, "all_timepoints" ), tps );
		else
		{
			for ( final TimePoint t : tps )
			{
				final ArrayList< TimePoint > tpl = new ArrayList< TimePoint >();
				tpl.add( t );
				
				h.put( new Entry( t.getId(), "timepoint_" + t.getName() ), tpl );
			}
		}
		
		return h;
	}

	protected HashMap< Entry, List< Channel > > getChannels( final List< Channel > channels, final boolean sameModelChannels )
	{
		final HashMap< Entry, List< Channel > > h = new HashMap< Entry, List< Channel > >();
		
		if ( sameModelChannels && channels.size() > 1 )
			h.put( new Entry( -1, "all_channels" ), channels );
		else
		{
			for ( final Channel c : channels )
			{
				final ArrayList< Channel > chl = new ArrayList< Channel >();
				chl.add( c );
				
				h.put( new Entry( c.getId(), "channel_" + c.getName() ), chl );
			}
		}
		
		return h;
	}

	protected HashMap< Entry, List< Illumination > > getIlluminations( final List< Illumination > illums, final boolean sameModelIlluminations )
	{
		final HashMap< Entry, List< Illumination > > h = new HashMap< Entry, List< Illumination > >();
		
		if ( sameModelIlluminations && illums.size() > 1 )
			h.put( new Entry( -1, "all_illuminations" ), illums );
		else
		{
			for ( final Illumination i : illums )
			{
				final ArrayList< Illumination > ill = new ArrayList< Illumination >();
				ill.add( i );
				
				h.put( new Entry( i.getId(), "illumination_" + i.getName() ), ill );
			}
		}
		
		return h;
	}

	protected HashMap< Entry, List< Angle > > getAngles( final List< Angle > angles, final boolean sameModelAngles )
	{
		final HashMap< Entry, List< Angle > > h = new HashMap< Entry, List< Angle > >();
		
		if ( sameModelAngles && angles.size() > 1 )
			h.put( new Entry( -1, "all_angles" ), angles );
		else
		{
			for ( final Angle a : angles )
			{
				final ArrayList< Angle > al = new ArrayList< Angle >();
				al.add( a );
				
				h.put( new Entry( a.getId(), "angle_" + a.getName() ), al );
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
		
		final ViewDescription viewDescription = spimData.getSequenceDescription().getViewDescription( 
				viewId.getTimePointId(), viewId.getViewSetupId() );

		VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSizeOrLoad( viewDescription.getViewSetup(), viewDescription.getTimePoint(), spimData.getSequenceDescription().getImgLoader() );
		final double calX = voxelSize.dimension( 0 ) / minResolution;
		final double calY = voxelSize.dimension( 1 ) / minResolution;
		final double calZ = voxelSize.dimension( 2 ) / minResolution;
		
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
			final SequenceDescription sequenceDescription,
			final List< TimePoint > timepointsToProcess, 
			final List< Channel > channelsToProcess,
			final List< Illumination > illumsToProcess,
			final List< Angle > anglesToProcess )
	{
		double minResolution = Double.MAX_VALUE;
		
		for ( final TimePoint t : timepointsToProcess )
			for ( final Channel c : channelsToProcess )
				for ( final Illumination i : illumsToProcess )
					for ( final Angle a : anglesToProcess )
					{
						// bureaucracy
						final ViewId viewId = SpimData2.getViewId( sequenceDescription, t, c, a, i );

						// this happens only if a viewsetup is not present in any timepoint
						// (e.g. after appending fusion to a dataset)
						if ( viewId == null )
							continue;

						final ViewDescription viewDescription = sequenceDescription.getViewDescription( 
								viewId.getTimePointId(), viewId.getViewSetupId() );

						if ( !viewDescription.isPresent() )
							continue;
						
						ViewSetup setup = viewDescription.getViewSetup();
						
						// load metadata to update the registrations if required
						// only use calibration as defined in the metadata
						if ( !setup.hasVoxelSize() )
						{
							VoxelDimensions voxelSize = sequenceDescription.getImgLoader().getVoxelSize( viewId );
							if ( voxelSize == null )
							{
								IOFunctions.println( "An error occured. Cannot load calibration for timepoint: " + t.getName() + " angle: " + 
										a.getName() + " channel: " + c.getName() + " illum: " + i.getName() );
								
								IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML" );
								
								return Double.NaN;
							}
							setup.setVoxelSize( voxelSize );
						}

						if ( !setup.hasVoxelSize() )
						{
							IOFunctions.println( "An error occured. No calibration available for timepoint: " + t.getName() + " angle: " + 
									a.getName() + " channel: " + c.getName() + " illum: " + i.getName() );
							
							IOFunctions.println( "Quitting. Please set it manually when defining the dataset or by modifying the XML." );
							IOFunctions.println( "Note: if you selected to load calibration independently for each image, it should." );
							IOFunctions.println( "      have been loaded during interest point detection." );
							
							return Double.NaN;
						}
						
						VoxelDimensions voxelSize = setup.getVoxelSize();
						final double calX = voxelSize.dimension( 0 );
						final double calY = voxelSize.dimension( 1 );
						final double calZ = voxelSize.dimension( 2 );
						
						minResolution = Math.min( minResolution, calX );
						minResolution = Math.min( minResolution, calY );
						minResolution = Math.min( minResolution, calZ );
					}
		
		return minResolution;
	}

	/**
	 * @param args
	 */
	public static void main( final String[] args )
	{
		//new BigDataViewerTransformationWindow( null );
		new Apply_Transformation().run( null );
	}
}
