package spim.process.fusion.boundingbox;

import ij.gui.GenericDialog;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.fusion.Fusion;
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
		{
			final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( i );

			boundingBoxes[ i ] = bb.getTitle() + " (";

			for ( int d = 0; d < bb.numDimensions(); ++d )
				boundingBoxes[ i ] += bb.dimension( d ) + "x";

			boundingBoxes[ i ] = boundingBoxes[ i ].substring( 0, boundingBoxes[ i ].length() - 1 ) + "px)";
		}

		if ( defaultBoundingBox >= boundingBoxes.length )
			defaultBoundingBox = 0;

		gd.addChoice( "Select_Bounding_Box", boundingBoxes, boundingBoxes[ defaultBoundingBox ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final BoundingBox bb = spimData.getBoundingBoxes().getBoundingBoxes().get( defaultBoundingBox = gd.getNextChoiceIndex() );

		this.min = bb.getMin().clone();
		this.max = bb.getMax().clone();

		return super.queryParameters( fusion, imgExport );
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

}
