package spim.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

import net.imglib2.util.Util;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.LoadParseQueryXML.XMLParseResult;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIo;
import spim.fiji.spimdata.XmlIoSpimData2;

public class Specify_Calibration implements PlugIn
{
	@Override
	public void run( final String arg0 )
	{
		// ask for everything but the channels
		final XMLParseResult result = new LoadParseQueryXML().queryXML( "specifying calibration", false, true, true, true );
		
		if ( result == null )
			return;
		
		// this is the same for all timepoints, we are just interested in the ViewSetup
		final TimePoint t = result.getData().getSequenceDescription().getTimePoints().getTimePointList().get( 0 );
		
		final ArrayList< Cal > calibrations = new ArrayList< Cal >(); 
		
		for ( final Channel c : result.getChannelsToProcess() )
			for ( final Angle a : result.getAnglesToProcess() )
				for ( final Illumination i : result.getIlluminationsToProcess() )
				{
					final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, i );
					final ViewDescription<TimePoint, ViewSetup> desc = result.getData().getSequenceDescription().getViewDescription( viewId ); 
					final ViewSetup viewSetup = desc.getViewSetup();
					final String name = "angle: " + a.getName() + " channel: " + c.getName() + " illum: " + i.getName() + 
							", present at timepoint: " + t.getName() + ": " + desc.isPresent();

					final double x = viewSetup.getPixelWidth();
					final double y = viewSetup.getPixelHeight();
					final double z = viewSetup.getPixelDepth();
					
					IOFunctions.println( "cal: [" + x + ", " + y + ", " + z + "] -- " + name );
					
					final Cal calTmp = new Cal( new double[]{ x, y, z } );
					boolean foundMatch = false;
					
					for ( int j = 0; j < calibrations.size() && !foundMatch; ++j )
					{
						final Cal cal = calibrations.get( j );
						if ( cal.equals( calTmp ) )
						{
							cal.increaseCount();
							foundMatch = true;
						}
					}
					
					if ( !foundMatch )
						calibrations.add( calTmp );
				}
		
		int max = 0;
		Cal maxCal = null;
		
		for ( final Cal cal : calibrations )
		{
			if ( cal.getCount() > max )
			{
				max = cal.getCount();
				maxCal = cal;
			}
		}
		
		IOFunctions.println( "Number of calibrations: " + calibrations.size() );
		IOFunctions.println( "Calibration most often present: " + Util.printCoordinates( maxCal.getCal() ) + " (" + maxCal.getCount() + " times)" );
		
		final GenericDialog gd = new GenericDialog( "Define new calibration" );
		
		gd.addNumericField( "Calibration_x", maxCal.getCal()[ 0 ], 40, 20, "" );
		gd.addNumericField( "Calibration_y", maxCal.getCal()[ 1 ], 40, 20, "" );
		gd.addNumericField( "Calibration_z", maxCal.getCal()[ 2 ], 40, 20, "" );

		if ( calibrations.size() > 0 )
			gd.addMessage( "WARNING: Calibrations are not the same for all view setups\n" +
					"will be overwritten for all view setups if defined here.",
					GUIHelper.mediumstatusfont, GUIHelper.warning );

		gd.addMessage( "Note: These values will be applied to all view setups as chosen before, existing\n" +
				"registration are not affected and need to be recomputed if necessary.",
				GUIHelper.mediumstatusfont );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		maxCal.getCal()[ 0 ] = gd.getNextNumber();
		maxCal.getCal()[ 1 ] = gd.getNextNumber();
		maxCal.getCal()[ 2 ] = gd.getNextNumber();
		
		for ( final Channel c : result.getChannelsToProcess() )
			for ( final Angle a : result.getAnglesToProcess() )
				for ( final Illumination i : result.getIlluminationsToProcess() )
				{
					final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, i );
					final ViewDescription<TimePoint, ViewSetup> desc = result.getData().getSequenceDescription().getViewDescription( viewId ); 
					final ViewSetup viewSetup = desc.getViewSetup();
					
					viewSetup.setPixelWidth( maxCal.getCal()[ 0 ] );
					viewSetup.setPixelHeight( maxCal.getCal()[ 1 ] );
					viewSetup.setPixelDepth( maxCal.getCal()[ 2 ] );
				}
		
		// save the xml
		final XmlIoSpimData2 io = XmlIo.createDefaultIo();
		
		final String xml = new File( result.getData().getBasePath(), new File( result.getXMLFileName() ).getName() ).getAbsolutePath();
		try 
		{
			io.save( result.getData(), xml );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + xml + "'." );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + xml + "': " + e );
			e.printStackTrace();
		}
	}
	
	protected class Cal
	{
		final double[] cal;
		int count;
		
		public Cal( final double[] cal )
		{
			this.cal = cal;
			this.count = 1;
		}
		
		public void increaseCount() { ++count; }
		public int getCount() { return count; }
		public double[] getCal() { return cal; }
		
		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof Cal )
			{
				final Cal c2 = (Cal)o;
				
				if ( c2.cal.length != this.cal.length )
					return false;
				else
				{
					for ( int d = 0; d < cal.length; ++d )
						if ( c2.cal[ d ] != cal[ d ] )
							return false;
					
					return true;
				}
			}
			else
				return false;
		}
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();
		new Specify_Calibration().run( null );
	}
}
