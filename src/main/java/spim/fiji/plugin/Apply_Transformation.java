package spim.fiji.plugin;

import java.util.HashMap;
import java.util.List;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.TimePoint;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;

public class Apply_Transformation implements PlugIn
{
	public static boolean defaultSameModelTimePoints = true;
	public static boolean defaultSameModelChannels = true;
	public static boolean defaultSameModelIlluminations = true;
	public static boolean defaultSameModelAngles = false;
	
	public static int defaultModel = 1;
	public static int defaultDefineAs = 1;
	public static int defaultApplyTo = 0;
	
	public static String[] modelChoice = new String[]{ "Translation", "Rigid", "Affine" };
	public static String[] defineChoice = new String[] { "Vector/matrix", "Rotation around axis (rigid only)" };
	
	public String[] axes = new String[] { "x-axis", "y-axis", "z-axis" };
	public static int defaultAxis = 0;
	public static double defaultDegrees = 90;

	public static double m00 = 1, m01 = 0, m02 = 0, m03 = 0;
	public static double m10 = 0, m11 = 1, m12 = 0, m13 = 0;
	public static double m20 = 0, m21 = 0, m22 = 1, m23 = 0;

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
		gd.addChoice( "Apply to", Interest_Point_Registration.inputChoice, Interest_Point_Registration.inputChoice[ defaultApplyTo ] );
		
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
		
		if ( defineAs == 0 )
			queryString( model, result, sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles );
		else
			queryRotationAxis( model, sameModelTimePoints, sameModelChannels, sameModelIlluminations, sameModelAngles );
		
		// now apply it
	}

	protected ModelDescription queryString( final int model, final XMLParseResult result, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	{
		final GenericDialog gd;
		
		if ( model == 0 )
		{
			gd = new GenericDialog( "Model parameters for translation model 3d" );

			gd.addMessage( "t(x) = m03, t(y) = m13, t(z) = m23" );
			gd.addMessage( "" );
			gd.addMessage( "Please provide 3d translation in this form (any brackets will be ignored): m03, m13, m23" );
			gd.addStringField( "Translation_vector", m03 + ", " + m13 + ", " + m23, 30 );
		}
		else
		{
			gd = new GenericDialog( "Model parameters for rigid/affine model 3d" );

			gd.addMessage( "| m00 m01 m02 m03 |\n" +
				"| m10 m11 m12 m13 |\n" +
				"| m20 m21 m22 m23 |" );
			gd.addMessage( "Please provide 3d rigid/affine in this form (any brackets will be ignored):\n" +
				"m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23" );
			gd.addStringField( "Affine_matrix", m00 + ", " + m01 + ", " + m02 + ", " + m03 + ", " + m10 + ", " + m11 + ", " +  m12 + ", " + m13 + ", " + m20 + ", " + m21 + ", " + m22 + ", " + m23, 80 );
		}
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		return null;
	}

	protected ModelDescription queryRotationAxis( final int model, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	//protected int getNumEntries( final XMLParseResult result, final boolean sameModelTimePoints, final boolean sameModelChannels, final boolean sameModelIlluminations, final boolean sameModelAngles )
	
	protected HashMap< String, TimePoint > getTimePoints( final List< TimePoint > tps, final boolean sameModelTimePoints )
	{
		final HashMap< String, TimePoint > h = new HashMap< String, TimePoint >();
		
		if ( sameModelTimePoints )
		{
			//if ( tps.size() > 1 )
			//	h.put( "", value)
		}
		
		return h;
	}
	

	/**
	 * @param args
	 */
	public static void main( final String[] args )
	{
		new Apply_Transformation().run( null );
	}
}
