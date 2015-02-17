package spim.fiji.spimdata.explorer;

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.util.Util;

public class ViewSetupExplorerInfoBox< AS extends AbstractSpimData< ? > >
{
	final JFrame frame;

	public ViewSetupExplorerInfoBox( final AS data, final String xml )
	{
		try
		{
			UIManager.setLookAndFeel( UIManager.getCrossPlatformLookAndFeelClassName() );
		}
		catch ( Exception e )
		{
			System.out.println( "Could not set look-and-feel" );
		}

		frame = new JFrame( "Information" );

		final JTextArea text = new JTextArea();

		text.append( "ImgLoader:\n");
		text.append( data.getSequenceDescription().getImgLoader().getClass().getName() + "\n" );
		text.append( data.getSequenceDescription().getImgLoader().toString() + "\n" );
		
		for ( final BasicViewSetup vs : data.getSequenceDescription().getViewSetupsOrdered() )
		{
			text.append( "\n" );
			text.append( "ViewSetup id=" + vs.getId() + ": \n" );

			final Dimensions dim = vs.getSize();
			final VoxelDimensions vDim = vs.getVoxelSize();

			if ( dim == null )
			{
				text.append( "Dimensions of image stack not loaded yet.\n");
			}
			else
			{
				text.append( "Dimensions: ");
				for ( int d = 0; d < dim.numDimensions() - 1; ++d )
					text.append( Long.toString( dim.dimension( d ) ) + " x " );
				text.append( Long.toString( dim.dimension( dim.numDimensions() - 1 ) ) + "px\n" );
			}

			if ( vDim == null )
			{
				text.append( "Voxel Dimensions of image stack not loaded yet.\n");
			}
			else
			{
				text.append( "Voxel Dimensions: ");
				for ( int d = 0; d < vDim.numDimensions() - 1; ++d )
					text.append( Double.toString( vDim.dimension( d ) ) + " x " );
				text.append( Double.toString( vDim.dimension( vDim.numDimensions() - 1 ) ) + vDim.unit() + "\n" );
			}
			
			for ( final String attrib : vs.getAttributes().keySet() )
			{
				final Entity e = vs.getAttributes().get( attrib );

				if ( Angle.class.isInstance( e ) )
				{
					final Angle a = (Angle)e;
					text.append( attrib + " " + a.getName() + " (id=" + a.getId() + ")" );

					if ( a.hasRotation() )
						text.append( ", Rotation Axis " + Util.printCoordinates( a.getRotationAxis() ) + ", Rotation Angle " + a.getRotationAngleDegrees() );

					text.append( "\n" );
				}
				else if ( NamedEntity.class.isInstance( e ) )
					text.append( attrib + " " +((NamedEntity)e).getName() + " (id=" + e.getId() + ")\n" );
				else
					text.append( attrib + " (id=" + e.getId() + ")\n" );
			}
		}

		text.append( "\n\nTimePoints:\n");
		String tps = "";
		for ( final TimePoint t : data.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
			tps += t.getId() + ", ";

		text.append( tps.substring( 0, tps.length() - 2 ) + "\n" );
		frame.add( new JScrollPane( text ), BorderLayout.CENTER );

		frame.pack();
		frame.setVisible( true );
	}
}
