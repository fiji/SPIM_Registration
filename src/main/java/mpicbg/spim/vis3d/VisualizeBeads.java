package mpicbg.spim.vis3d;

import java.awt.Font;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import spim.vecmath.Color3f;

import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.models.AffineModel3D;
import mpicbg.spim.io.ConfigurationParserException;
import mpicbg.spim.io.ConfigurationParserGeneral;
import mpicbg.spim.io.ConfigurationParserSPIM;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.io.ProgramConfiguration;
import mpicbg.spim.io.SPIMConfiguration;
import mpicbg.spim.io.TextFileAccess;
import mpicbg.spim.registration.ViewDataBeads;
import mpicbg.spim.registration.ViewStructure;
import mpicbg.spim.registration.bead.Bead;
import mpicbg.spim.registration.bead.BeadIdentification;
import mpicbg.spim.registration.bead.BeadRegistration;

public class VisualizeBeads
{
	final SPIMConfiguration config;
	final ArrayList<ViewDataBeads> views;
	final ViewStructure viewStructure;
	
	final static public Color3f backgroundColor = new Color3f( 1f, 1f, 1f );
	final static public Color3f foregroundColor = new Color3f( 0.5f, 0.5f, 0.59f );
	
	final static public Color3f beadColor = new Color3f( 0f, 0f, 0f );
	final static public float beadSize = 3f;
	
	final static public Color3f boundingBoxColor = new Color3f( 0.7f, 0.7f, 0.85f );
	final static public Color3f imagingBoxColor = new Color3f( 1f, 0.0f, 0.25f );

	final static public Font statusbarFont = new Font("Cambria", Font.PLAIN, 12);

	public VisualizeBeads( SPIMConfiguration config )
	{
		this.config = config;

		viewStructure = ViewStructure.initViewStructure( config, 0, new AffineModel3D(), "ViewStructure Timepoint " + 0, config.debugLevelInt );						
		views = viewStructure.getViews();

		for ( ViewDataBeads view : viewStructure.getViews() )
		{
			view.loadDimensions();
			view.loadSegmentation();
			view.loadRegistration();
		}

		writeSketchTikZ("src/templates/beadimage-1.sk", "src/templates/allViewsWorm.sk");
	}

	protected void writeSketchTikZ( final String template, final String output )
	{
		//
		// scale the views
		//
		BeadRegistration.concatenateAxialScaling( views, viewStructure.getDebugLevel() );

		final float factor = 0.005f;
		boolean reachedInsertPosition = false;

		try
		{
			BufferedReader in = TextFileAccess.openFileRead( template );
			PrintWriter out = TextFileAccess.openFileWrite( output );

			// find the insert site and copy the beginning
			while ( in.ready() && !reachedInsertPosition )
			{
				String line = in.readLine();
				if ( line.contains("%<--for Java-->"))
					reachedInsertPosition = true;
				else
					out.println( line );
			}
	
			//
			// draw
			//
			
			int num = views.size();
			//num = 1;			
			for ( int i = 0; i < num; i++ )
			{			
				out.println( VisualizationSketchTikZ.drawView( views.get( i ), factor ) );
				out.println( VisualizationSketchTikZ.drawBeads( views.get( i ).getBeadStructure().getBeadList(), views.get( i ).getTransform3D(), "Bead", factor ) );
			}

			//out.println( drawFalseCorrespondences( views[0], views[4], factor ) );
			//out.println( drawRANSACCorrespondences( views[0], views[4], factor ) );

			out.println( drawAllFalseCorrespondences( views, factor ) );
			out.println( drawAllRANSACCorrespondences( views, factor ) );
			

			// copy the rest
			while ( in.ready() )
			{
				String line = in.readLine();
				out.println( line );
			}

			in.close();
			out.close();		
		}
		catch (IOException e)
		{
			IOFunctions.printErr("Error reading/writing template or output file: " + e);
			return;
		}
	
		System.exit(0);
	}

	protected String drawFalseCorrespondences( final ViewDataBeads view, final ViewDataBeads correspondingView, final float factor )
	{
		final ArrayList<Bead> beads = view.getBeadStructure().getBeadList();
		final ArrayList<Bead> correspondences = new ArrayList<Bead>();
		
		for ( final Bead bead : beads)
		{
			boolean isDescriptorCorrespondingBead = false;
			boolean isRANSACCorrespondingBead = false;
			
			final ArrayList<BeadIdentification> correspondingRANSACBeads = bead.getRANSACCorrespondence();
			for ( final BeadIdentification correspondingBead : correspondingRANSACBeads )
				if (correspondingBead.getViewID() == correspondingView.getID())
					isRANSACCorrespondingBead = true;
			
			final ArrayList<BeadIdentification> correspondingDescriptorBeads = bead.getDescriptorCorrespondence();			
			for ( final BeadIdentification correspondingBead : correspondingDescriptorBeads )
				if (correspondingBead.getViewID() == correspondingView.getID())
					isDescriptorCorrespondingBead = true;
			
			if ( isDescriptorCorrespondingBead && ! isRANSACCorrespondingBead )
				correspondences.add( bead );
		}
		
		return VisualizationSketchTikZ.drawBeads( correspondences, view.getTransform3D(), "FalseBead", factor );
	}

	protected String drawRANSACCorrespondences( final ViewDataBeads view, final ViewDataBeads correspondingView, final float factor )
	{
		final ArrayList<Bead> beads = view.getBeadStructure().getBeadList();
		final ArrayList<Bead> correspondences = new ArrayList<Bead>();
		
		for ( final Bead bead : beads)
		{
			final ArrayList<BeadIdentification> correspondingBeads = bead.getRANSACCorrespondence();
			boolean isCorrespondingBead = false;
			
			for ( final BeadIdentification correspondingBead : correspondingBeads )
				if (correspondingBead.getViewID() == correspondingView.getID())
					isCorrespondingBead = true;
			
			if ( isCorrespondingBead )
				correspondences.add( bead );
		}
		
		return VisualizationSketchTikZ.drawBeads( correspondences, view.getTransform3D(), "RansacBead", factor );
	}
	

	protected String drawAllRANSACCorrespondences( final ArrayList<ViewDataBeads> views, final float factor )
	{
		String insert = "";
		final ArrayList <int[]> comparePairs = new ArrayList<int[]>();
		
		for ( int viewA = 0; viewA < views.size() - 1; viewA++ )
    		for ( int viewB = viewA + 1; viewB < views.size(); viewB++ )
    			comparePairs.add(new int[]{viewA, viewB});
		
		for ( int[] pair : comparePairs )
		{
			final ViewDataBeads viewA = views.get( pair[0] );
			final ViewDataBeads viewB = views.get( pair[1] );
		
			final ArrayList<Bead> beads = viewA.getBeadStructure().getBeadList();
			final ArrayList<Bead> correspondences = new ArrayList<Bead>();
			
			for ( final Bead bead : beads)
			{
				final ArrayList<BeadIdentification> correspondingBeads = bead.getRANSACCorrespondence();
				boolean isCorrespondingBead = false;
				
				for ( final BeadIdentification correspondingBead : correspondingBeads )
					if (correspondingBead.getViewID() == viewB.getID())
						isCorrespondingBead = true;
				
				if ( isCorrespondingBead )
					correspondences.add( bead );
			}
			
			insert += VisualizationSketchTikZ.drawBeads( correspondences, viewA.getTransform3D(), "RansacBead", factor );
		}		
		
		return insert;
	}

	protected String drawAllFalseCorrespondences( final ArrayList<ViewDataBeads> views, final float factor )
	{
		String insert = "";
		
		final ArrayList <int[]> comparePairs = new ArrayList<int[]>();
		
		for ( int viewA = 0; viewA < views.size() - 1; viewA++ )
    		for ( int viewB = viewA + 1; viewB < views.size(); viewB++ )
    			comparePairs.add(new int[]{viewA, viewB});
		
		for ( int[] pair : comparePairs )
		{
			final ViewDataBeads viewA = views.get( pair[0] );
			final ViewDataBeads viewB = views.get( pair[1] );
		
			final ArrayList<Bead> beads = viewA.getBeadStructure().getBeadList();
			final ArrayList<Bead> correspondences = new ArrayList<Bead>();

			for ( final Bead bead : beads)
			{
				boolean isDescriptorCorrespondingBead = false;
				boolean isRANSACCorrespondingBead = false;
				
				final ArrayList<BeadIdentification> correspondingRANSACBeads = bead.getRANSACCorrespondence();
				for ( final BeadIdentification correspondingBead : correspondingRANSACBeads )
					if (correspondingBead.getViewID() == viewB.getID())
						isRANSACCorrespondingBead = true;
				
				final ArrayList<BeadIdentification> correspondingDescriptorBeads = bead.getDescriptorCorrespondence();			
				for ( final BeadIdentification correspondingBead : correspondingDescriptorBeads )
					if (correspondingBead.getViewID() == viewB.getID())
						isDescriptorCorrespondingBead = true;
				
				if ( isDescriptorCorrespondingBead && ! isRANSACCorrespondingBead )
					correspondences.add( bead );
			}
			
			
			insert += VisualizationSketchTikZ.drawBeads( correspondences, viewA.getTransform3D(), "FalseBead", factor );			
		}
		
		return insert;
	}

	protected String drawPointDescriptorCorrespondences( final ViewDataBeads view, final ViewDataBeads correspondingView, final float factor )
	{
		final ArrayList<Bead> beads = view.getBeadStructure().getBeadList();
		final ArrayList<Bead> correspondences = new ArrayList<Bead>();
		
		for ( final Bead bead : beads)
		{
			final ArrayList<BeadIdentification> correspondingBeads = bead.getDescriptorCorrespondence();
			boolean isCorrespondingBead = false;
			
			for ( final BeadIdentification correspondingBead : correspondingBeads )
				if (correspondingBead.getViewID() == correspondingView.getID())
					isCorrespondingBead = true;
			
			if ( isCorrespondingBead )
				correspondences.add( bead );
		}
		
		return VisualizationSketchTikZ.drawBeads( correspondences, view.getTransform3D(), "CorrespondingBead", factor );
	}

	public static void main(String[] args) 
	{		
		// read&parse configuration file
		ProgramConfiguration conf = null;
		try
		{
			conf = ConfigurationParserGeneral.parseFile("config/configuration.txt");
		} 
		catch (final Exception e)
		{
			IOFunctions.printErr("Cannot open configuration file: " + e);
			e.printStackTrace();
			return;
		}

		// open imageJ window
		System.getProperties().setProperty("plugins.dir", conf.pluginsDir);
		final String params[] = {"-ijpath " + conf.pluginsDir};
		ij.ImageJ.main(params);

		// read SPIM configuration
		SPIMConfiguration config = null;

		try
		{
			config = ConfigurationParserSPIM.parseFile("spimconfig/configuration.txt");
			config.printProperties();
		}
		catch (final ConfigurationParserException e)
		{
			e.printStackTrace();
			System.exit(0);
		}

		new VisualizeBeads( config ); 
	}
}
