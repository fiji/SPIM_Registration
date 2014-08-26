package spim.fiji.plugin.apply;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import javax.media.j3d.Transform3D;
import javax.swing.SwingUtilities;

import net.imglib2.realtransform.AffineTransform3D;
import bdv.BigDataViewer;
import bdv.viewer.ViewerFrame;

public class BigDataViewerTransformationWindow
{
	final protected Timer timer;
	
	protected boolean isRunning = true;
	protected boolean wasCancelled = false;
	protected boolean ignoreScaling = true;

	protected double m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23;

	public BigDataViewerTransformationWindow( final BigDataViewer bdv )
	{
		final Frame frame = new Frame( "Current Global Transformation" );
		frame.setSize( 400, 200 );

		/* Instantiation */
		final GridBagLayout layout = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();

		final Label text1 = new Label( "1.00000   0.00000", Label.CENTER );
		final Label text2 = new Label( "0.00000   0.00000", Label.CENTER );
		
		final Label text3 = new Label( "0.00000   1.00000", Label.CENTER );
		final Label text4 = new Label( "0.00000   0.00000", Label.CENTER );
		
		final Label text5 = new Label( "0.00000   0.00000", Label.CENTER );
		final Label text6 = new Label( "1.00000   0.00000", Label.CENTER );

		text1.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text2.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text3.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text4.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text5.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text6.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );

		final Button apply = new Button( "Apply Transformation" );
		final Button cancel = new Button( "Cancel" );
		final Checkbox ignoreScale = new Checkbox( "Ignore scaling factor from BigDataViewer", ignoreScaling );

		/* Location */
		frame.setLayout( layout );

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;

		frame.add( text1, c );
		++c.gridx;
		frame.add( text2, c );
		--c.gridx;

		++c.gridy;
		frame.add( text3, c );
		++c.gridx;
		frame.add( text4, c );
		--c.gridx;

		++c.gridy;
		frame.add( text5, c );
		++c.gridx;
		frame.add( text6, c );
		--c.gridx;

		++c.gridy;
		//c.insets = new Insets(0,130,0,75);
		frame.add( ignoreScale, c );

		++c.gridy;
		c.insets = new Insets( 20,0,0,0 );
		frame.add( apply, c );

		++c.gridx;
		frame.add( cancel, c );

		apply.addActionListener( new ApplyButtonListener( frame, bdv ) );
		cancel.addActionListener( new CancelButtonListener( frame, bdv ) );

		frame.setVisible( true );

		timer = new Timer();
		timer.schedule( new BDVChecker( bdv, text1, text2, text3, text4, text5, text6 ), 500 );
	}

	public boolean isRunning() { return isRunning; }
	public boolean wasCancelled() { return wasCancelled; }

	protected void close( final Frame parent, final BigDataViewer bdv )
	{
		if ( parent != null )
			parent.dispose();

		isRunning = false;
	}

	protected class CancelButtonListener implements ActionListener
	{
		final Frame parent;
		final BigDataViewer bdv;

		public CancelButtonListener( final Frame parent, final BigDataViewer bdv )
		{
			this.parent = parent;
			this.bdv = bdv;
		}
		
		@Override
		public void actionPerformed( final ActionEvent arg0 ) 
		{ 
			wasCancelled = true;
			close( parent, bdv );
		}
	}

	protected class BDVChecker extends TimerTask
	{
		final BigDataViewer bdv;
		final Label text1, text2, text3, text4, text5, text6;

		public BDVChecker(
				final BigDataViewer bdv,
				final Label text1,
				final Label text2,
				final Label text3,
				final Label text4,
				final Label text5,
				final Label text6 )
		{
			this.bdv = bdv;
			this.text1 = text1;
			this.text2 = text2;
			this.text3 = text3;
			this.text4 = text4;
			this.text5 = text5;
			this.text6 = text6;
		}

		@Override
		public void run()
		{
			if ( isRunning )
			{
				final AffineTransform3D t = new AffineTransform3D();
				bdv.getViewer().getState().getViewerTransform( t );

				final double[] m = new double[ 16 ];
				int i = 0;

				for ( int row = 0; row < 3; ++row )
					for ( int col = 0; col < 4; ++col )
						m[ i++ ] = t.get( row, col );

				m[ 15 ] = 1;

				Transform3D trans = new Transform3D( m );
				System.out.println( trans.getScale() );

				trans.setScale( 1 );
				System.out.println( trans );

				final DecimalFormat df = new DecimalFormat( "0.00000" );

				text1.setText( df.format( t.get( 0, 0 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 0, 1 ) ).substring( 0, 7 ) );
				text2.setText( df.format( t.get( 0, 2 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 0, 3 ) ).substring( 0, 7 ) );

				text3.setText( df.format( t.get( 1, 0 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 1, 1 ) ).substring( 0, 7 ) );
				text4.setText( df.format( t.get( 1, 2 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 1, 3 ) ).substring( 0, 7 ) );

				text5.setText( df.format( t.get( 2, 0 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 2, 1 ) ).substring( 0, 7 ) );
				text6.setText( df.format( t.get( 2, 2 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 2, 3 ) ).substring( 0, 7 ) );

				// Reschedule myself (new instance is required, why?)
				timer.schedule( new BDVChecker( bdv,text1, text2, text3, text4, text5, text6 ), 500 );
			}
		}
		
	}
	
	protected class ApplyButtonListener implements ActionListener
	{
		final Frame parent;
		final BigDataViewer bdv;

		public ApplyButtonListener( final Frame parent, final BigDataViewer bdv )
		{
			this.parent = parent;
			this.bdv = bdv;
		}
		
		@Override
		public void actionPerformed( final ActionEvent arg0 ) 
		{ 
			wasCancelled = false;
			close( parent, bdv );
		}
	}

	public static void disposeViewerWindow( final BigDataViewer bdv )
	{
		try
		{
			SwingUtilities.invokeAndWait( new Runnable()
			{
				@Override
				public void run()
				{
					final ViewerFrame frame = bdv.getViewerFrame();
					final WindowEvent windowClosing = new WindowEvent( frame, WindowEvent.WINDOW_CLOSING );
					frame.dispatchEvent( windowClosing );
				}
			} );
		}
		catch ( final Exception e )
		{}
	}

}
