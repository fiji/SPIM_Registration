package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.Interactive_Remove_Detections;
import spim.fiji.plugin.ThinOut_Detections;
import spim.fiji.plugin.removedetections.InteractiveProjections;
import spim.fiji.plugin.thinout.ChannelProcessThinOut;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.interestpoints.InterestPoint;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class RemoveDetectionsPopup extends JMenu implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	ViewSetupExplorerPanel< ?, ? > panel;

	public RemoveDetectionsPopup()
	{
		super( "Remove Interest Points" );

		final JMenuItem byDistance = new JMenuItem( "By Distance ..." );
		final JMenuItem interactivelyXY = new JMenuItem( "Interactively (XY Projection) ..." );
		final JMenuItem interactivelyXZ = new JMenuItem( "Interactively (XZ Projection) ..." );
		final JMenuItem interactivelyYZ = new JMenuItem( "Interactively (YZ Projection) ..." );

		byDistance.addActionListener( new MyActionListener( 0 ) );
		interactivelyXY.addActionListener( new MyActionListener( 1 ) );
		interactivelyXZ.addActionListener( new MyActionListener( 2 ) );
		interactivelyYZ.addActionListener( new MyActionListener( 3 ) );

		this.add( byDistance );
		this.add( interactivelyXY );
		this.add( interactivelyXZ );
		this.add( interactivelyYZ );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		final int index;

		public MyActionListener( final int index )
		{
			this.index = index;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}


			if ( index == 0 )
			{
				final List< ViewId > viewIds = panel.selectedRowsViewId();
				final SpimData2 data = (SpimData2)panel.getSpimData();

				// ask which channels have the objects we are searching for
				final List< ChannelProcessThinOut > channels = ThinOut_Detections.getChannelsAndLabels( data, viewIds );

				if ( channels == null )
					return;

				// get the actual min/max thresholds for cutting out
				if ( !ThinOut_Detections.getThinOutThresholds( data, viewIds, channels ) )
					return;

				// thin out detections and save the new interestpoint files
				if ( !ThinOut_Detections.thinOut( data, viewIds, channels, false ) )
					return;

				panel.updateContent(); // update interestpoint panel if available

				return;
			}
			else
			{
				final List< BasicViewDescription< ? extends BasicViewSetup > > vds = panel.selectedRows();

				if ( vds.size() != 1 )
				{
					JOptionPane.showMessageDialog( null, "Interactive Removal of Detections only supports a single view at a time." );
					return;
				}

				final Pair< String, String > labels = Interactive_Remove_Detections.queryLabelAndNewLabel( (SpimData2)panel.getSpimData(), (ViewDescription)vds.get( 0 ) );

				if ( labels == null )
					return;

				final SpimData2 spimData = (SpimData2)panel.getSpimData();
				final ViewDescription vd = (ViewDescription)vds.get( 0 );
				final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
				final ViewInterestPointLists lists = interestPoints.getViewInterestPointLists( vd );
				final String label = labels.getA();
				final String newLabel = labels.getB();

				final InteractiveProjections ip = new InteractiveProjections( spimData, vd, label, newLabel, 2 - (index - 1) );

				ip.runWhenDone( new Thread( new Runnable()
				{
					@Override
					public void run()
					{
						if ( ip.wasCanceled() )
							return;

						final List< InterestPoint > ipList = ip.getInterestPointList();

						if ( ipList.size() == 0 )
						{
							IOFunctions.println( "No detections remaining. Quitting." );
							return;
						}

						// add new label
						final InterestPointList newIpl = new InterestPointList(
								lists.getInterestPointList( label ).getBaseDir(),
								new File(
										lists.getInterestPointList( label ).getFile().getParentFile(),
										"tpId_" + vd.getTimePointId() + "_viewSetupId_" + vd.getViewSetupId() + "." + newLabel ) );

						newIpl.setInterestPoints( ipList );
						newIpl.setParameters( "manually removed detections from '" +label + "'" );

						lists.addInterestPointList( newLabel, newIpl );

						panel.updateContent(); // update interestpoint panel if available
					}
				}) );

				return;
			}
		}
	}
}
