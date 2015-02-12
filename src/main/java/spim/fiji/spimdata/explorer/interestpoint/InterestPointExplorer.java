package spim.fiji.spimdata.explorer.interestpoint;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

import javax.swing.JFrame;

import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import spim.fiji.spimdata.interestpoints.InterestPointList;

public class InterestPointExplorer< AS extends SpimData2, X extends XmlIoAbstractSpimData< ?, AS > >
	implements SelectedViewDescriptionListener
{
	final AS data;
	final String xml;
	final JFrame frame;
	final InterestPointExplorerPanel panel;

	public InterestPointExplorer( final AS data, final String xml, final X io )
	{
		this.data = data;
		this.xml = xml;

		final ViewSetupExplorer< AS, X > viewSetupExplorer = new ViewSetupExplorer< AS, X >( data, xml, io );

		frame = new JFrame( "Interest Point Explorer" );
		panel = new InterestPointExplorerPanel( data.getViewInterestPoints() );
		frame.add( panel, BorderLayout.CENTER );

		frame.setSize( panel.getPreferredSize() );
		
		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing( WindowEvent evt )
					{
						viewSetupExplorer.quit();
					}
				});

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) / 2 );

		// this call also triggers the first update of the registration table
		viewSetupExplorer.addListener( this );
	}

	@Override
	public void seletedViewDescription( final BasicViewDescription<? extends BasicViewSetup> viewDescription )
	{
		panel.updateViewDescription( viewDescription );
	}

	@Override
	public void save()
	{
		for ( final Pair< InterestPointList, ViewId > list : panel.save )
		{
			String output = "";

			if ( list.getA().saveCorrespondingInterestPoints() )
				output = "Saved ";
			else
				output = "FAILED to save ";

			IOFunctions.println( output + "correspondences in timepointid=" + list.getB().getTimePointId() + ", viewid=" + list.getB().getViewSetupId() );
		}

		for ( final Pair< InterestPointList, ViewId > list : panel.delete )
		{
			IOFunctions.println( "Deleting correspondences and interestpoints in timepointid=" + list.getB().getTimePointId() + ", viewid=" + list.getB().getViewSetupId() );

			final File ip = new File( list.getA().getBaseDir(), list.getA().getFile().toString() + list.getA().getInterestPointsExt() );
			final File corr = new File( list.getA().getBaseDir(), list.getA().getFile().toString() + list.getA().getCorrespondencesExt() );

			if ( ip.delete() )
				IOFunctions.println( "Deleted: " + ip.getAbsolutePath() );
			else
				IOFunctions.println( "FAILED to delete: " + ip.getAbsolutePath() );

			if ( corr.delete() )
				IOFunctions.println( "Deleted: " + corr.getAbsolutePath() );
			else
				IOFunctions.println( "FAILED to delete: " + corr.getAbsolutePath() );
		}

		panel.save.clear();
		panel.delete.clear();
	}

	public static void main( String[] args )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Interest Point Explorer", "", false, false, false, false ) )
			return;

		new InterestPointExplorer< SpimData2, XmlIoSpimData2 >( result.getData(), result.getXMLFileName(), result.getIO() );
	}
}
