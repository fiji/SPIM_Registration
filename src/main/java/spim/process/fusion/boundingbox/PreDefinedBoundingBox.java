package spim.process.fusion.boundingbox;

import ij.gui.DialogListener;
import ij.gui.GenericDialog;

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.Label;
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

		final GenericDialog gd = new GenericDialog( "Select pre-defined Bounding Box" );

		final String[] boundingBoxes = new String[ spimData.getBoundingBoxes().getBoundingBoxes().size() ];

		for ( int i = 0; i < boundingBoxes.length; ++i )
			boundingBoxes[ i ] = spimData.getBoundingBoxes().getBoundingBoxes().get( i ).getTitle();

		if ( defaultBoundingBox >= boundingBoxes.length )
			defaultBoundingBox = 0;

		gd.addChoice( "Select_Bounding_Box", boundingBoxes, boundingBoxes[ defaultBoundingBox ] );
		final Choice choice = (Choice)gd.getChoices().lastElement();

		gd.addMessage( "" );
		gd.addMessage( "BoundingBox size: ???x???x??? pixels", GUIHelper.mediumstatusfont );
		final Label l1 = (Label)gd.getMessage();

		gd.addMessage( "BoundingBox offset: ???x???x??? pixels", GUIHelper.mediumstatusfont );
		final Label l2 = (Label)gd.getMessage();

		addListeners( gd, choice, l1, l2 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( defaultBoundingBox = gd.getNextChoiceIndex() );

		this.min = bb.getMin().clone();
		this.max = bb.getMax().clone();

		return super.queryParameters( fusion, imgExport );
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

	protected DialogListener addListeners(
			final GenericDialog gd,
			final Choice choice,
			final Label label1,
			final Label label2 )
	{
		DialogListener d = new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( e.getSource() == choice )
					update( spimData, choice, label1, label2 );

				return true;
			}
		};

		gd.addDialogListener( d );

		update( spimData, choice, label1, label2 );

		return d;
	}

	protected final static void update( final SpimData2 spimData, final Choice choice, final Label label1, final Label label2 )
	{
		final int index = choice.getSelectedIndex();
		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( index );

		label1.setText( "BoundingBox size: " + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 0 ) + " pixels" );
		label2.setText( "BoundingBox offset: " + bb.min( 0 ) + "x" + bb.min( 1 ) + "x" + bb.min( 2 ) + " pixels" );
	}

	public void initDefault( final Fusion fusion, int[] min, int[] max )
	{
		final double[] minBB = new double[ 3 ];
		final double[] maxBB = new double[ 3 ];

		computeMaxBoundingBoxDimensions( spimData, viewIdsToProcess, minBB, maxBB );

		if ( fusion.supportsDownsampling() )
			this.downsampling = BoundingBoxGUI.staticDownsampling;
		else
			this.downsampling = 1;

		if ( fusion.supports16BitUnsigned() )
			this.pixelType = BoundingBoxGUI.defaultPixelType = 1;
		else
			this.pixelType = BoundingBoxGUI.defaultPixelType = 0; //32-bit

		this.imgtype = BoundingBoxGUI.defaultImgType;

		if ( min[ 0 ] > max[ 0 ] || min[ 1 ] > max[ 1 ] || min[ 2 ] > max[ 2 ] )
		{
			IOFunctions.println( "Invalid coordinates, min cannot be larger than max" );
			return;
		}

		this.min[0] = BoundingBoxGUI.defaultMin[ 0 ] = min[ 0 ];
		this.min[1] = BoundingBoxGUI.defaultMin[ 1 ] = min[ 1 ];
		this.min[2] = BoundingBoxGUI.defaultMin[ 2 ] = min[ 2 ];
		this.max[0] = BoundingBoxGUI.defaultMax[ 0 ] = max[ 0 ];
		this.max[1] = BoundingBoxGUI.defaultMax[ 1 ] = max[ 1 ];
		this.max[2] = BoundingBoxGUI.defaultMax[ 2 ] = max[ 2 ];
	}
}
