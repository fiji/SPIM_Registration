package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.awt.Choice;
import java.awt.Label;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.fusion.Fusion;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.process.fusion.export.ImgExport;

public class PreDefinedBoundingBox extends BoundingBoxGUI
{
	public static int defaultBoundingBox = 0;
	public static boolean defaultAllowModify = false;

	public PreDefinedBoundingBox( final SpimData2 spimData, final List<ViewId> viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public boolean queryParameters( final Fusion fusion, final ImgExport imgExport )
	{
		if ( spimData.getBoundingBoxes().getBoundingBoxes().size() == 0 )
		{
			IOFunctions.println( "No bounding boxes pre-defined." );
			return false;
		}

		final GenericDialog gd1 = new GenericDialog( "Pre-defined Bounding Box" );

		final String[] boundingBoxes = new String[ spimData.getBoundingBoxes().getBoundingBoxes().size() ];

		for ( int i = 0; i < boundingBoxes.length; ++i )
			boundingBoxes[ i ] = spimData.getBoundingBoxes().getBoundingBoxes().get( i ).getTitle();

		if ( defaultBoundingBox >= boundingBoxes.length )
			defaultBoundingBox = 0;

		gd1.addChoice( "Bounding_box_title", boundingBoxes, boundingBoxes[ defaultBoundingBox ] );
		final Choice choice = (Choice)gd1.getChoices().lastElement();

		gd1.addCheckbox( "Allow_to_modify bounding box in next dialog", defaultAllowModify );
		gd1.addMessage( "Note: Not allowing this is very useful for macro programming", GUIHelper.smallStatusFont );

		gd1.addMessage( "" );
		gd1.addMessage( "BoundingBox size: ???x???x??? pixels", GUIHelper.mediumstatusfont );
		final Label l1 = (Label)gd1.getMessage();

		gd1.addMessage( "BoundingBox offset: ???x???x??? pixels", GUIHelper.mediumstatusfont );
		final Label l2 = (Label)gd1.getMessage();

		addListeners( gd1, choice, l1, l2 );

		gd1.showDialog();

		if ( gd1.wasCanceled() )
			return false;

		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( defaultBoundingBox = gd1.getNextChoiceIndex() );
		final boolean allowModifyDimensions = defaultAllowModify = gd1.getNextBoolean();

		this.min = bb.getMin().clone();
		this.max = bb.getMax().clone();

		return super.queryParameters( fusion, imgExport, allowModifyDimensions );
	}

	public static String getBoundingBoxDescription( final BoundingBox bb )
	{
		String title = bb.getTitle() + " (dim=";

		for ( int d = 0; d < bb.numDimensions(); ++d )
			title += bb.dimension( d ) + "x";

		title = title.substring( 0, title.length() - 1 ) + "px, offset=";

		for ( int d = 0; d < bb.numDimensions(); ++d )
			title += bb.min( d ) + "x";

		title = title.substring( 0, title.length() - 1 ) + "px)";

		return title;
	}

	@Override
	public PreDefinedBoundingBox newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new PreDefinedBoundingBox( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Use pre-defined Bounding Box";
	}

	protected void addListeners(
			final GenericDialog gd,
			final Choice choice,
			final Label label1,
			final Label label2 )
	{
		choice.addItemListener( new ItemListener()
		{
			@Override
			public void itemStateChanged(ItemEvent e)
			{
				update( spimData, choice, label1, label2 );
			}
		});

		update( spimData, choice, label1, label2 );
	}

	protected final static void update( final SpimData2 spimData, final Choice choice, final Label label1, final Label label2 )
	{
		final int index = choice.getSelectedIndex();
		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( index );

		label1.setText( "Bounding Box size: " + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 2 ) + " pixels" );
		label2.setText( "Bounding Box offset: " + bb.min( 0 ) + "x" + bb.min( 1 ) + "x" + bb.min( 2 ) + " pixels" );
	}

}
