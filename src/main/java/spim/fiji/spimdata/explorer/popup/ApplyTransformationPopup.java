package spim.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.apply.ApplyParameters;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;

public class ApplyTransformationPopup extends JMenuItem implements ViewExplorerSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ViewSetupExplorerPanel< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public ApplyTransformationPopup()
	{
		super( "Apply Transformation(s) ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setViewExplorer( final ViewSetupExplorerPanel< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = panel.selectedRowsViewId();
					final SpimData data = (SpimData)panel.getSpimData();
		
					final Apply_Transformation t = new Apply_Transformation();
		
					final ApplyParameters params = t.queryParams( data, viewIds );
		
					if ( params == null )
						return;
				
					final Map< ViewDescription, Pair< double[], String > > modelLinks;
				
					// query models and apply them
					if ( params.defineAs == 0 ) // matrix
						modelLinks = t.queryString( data, viewIds, params );
					else if ( params.defineAs == 1 ) //Rotation around axis
						modelLinks = t.queryRotationAxis( data, viewIds, params );
					else // Interactively using the BigDataViewer
						modelLinks = t.queryBigDataViewer( data, viewIds, params );
				
					if ( modelLinks == null )
						return;
				
					t.applyModels( data, params.minResolution, params.applyTo, modelLinks );
		
					// update registration panel if available
					panel.updateContent();
					ViewSetupExplorerPanel.bdvPopup().updateBDV();
				}
			} ).start();
		}
	}
}
