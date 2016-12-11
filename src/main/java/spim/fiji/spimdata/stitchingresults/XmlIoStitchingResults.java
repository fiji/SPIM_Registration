package spim.fiji.spimdata.stitchingresults;

import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STICHING_CORRELATION_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STICHING_SHIFT_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHING_VS_A_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHING_VS_B_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHING_TP_A_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHING_TP_B_TAG;

import org.jdom2.Element;

import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHINGRESULT_PW_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHINGRESULTS_TAG;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.XmlHelpers;
import mpicbg.spim.data.generic.base.XmlIoSingleton;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.ValuePair;



public class XmlIoStitchingResults extends XmlIoSingleton<StitchingResults>
{

	public XmlIoStitchingResults()
	{
		super( STITCHINGRESULTS_TAG, StitchingResults.class );
		handledTags.add( STITCHINGRESULTS_TAG );
	}
	
	public Element toXml( final StitchingResults stitchingResults )
	{
		final Element elem = super.toXml();

		for ( final PairwiseStitchingResult< ViewId > sr : stitchingResults.getPairwiseResults().values() )
			elem.addContent( pairwiseResultToXml( sr ) );

		return elem;
	}
	
	public StitchingResults fromXml( final Element allStitchingResults ) throws SpimDataException
	{
		final StitchingResults stitchingResults = super.fromXml( allStitchingResults );

		for ( final Element pairwiseResultsElement : allStitchingResults.getChildren( STITCHINGRESULT_PW_TAG ) )
		{
			final int vsA = Integer.parseInt( pairwiseResultsElement.getAttributeValue( STITCHING_VS_A_TAG ));
			final int vsB = Integer.parseInt( pairwiseResultsElement.getAttributeValue( STITCHING_VS_B_TAG ));
			final int tpA = Integer.parseInt( pairwiseResultsElement.getAttributeValue( STITCHING_TP_A_TAG ));
			final int tpB = Integer.parseInt( pairwiseResultsElement.getAttributeValue( STITCHING_TP_B_TAG ));

			final double[] shift = XmlHelpers.getDoubleArray( pairwiseResultsElement, STICHING_SHIFT_TAG );
			final double corr = XmlHelpers.getDouble( pairwiseResultsElement, STICHING_CORRELATION_TAG );
			
			AffineTransform3D transform = new AffineTransform3D();
			// backwards-compatibility with just translation
			if (shift.length == 3)
				transform.setTranslation( shift );
			// tag contains row-packed copy of transformation matrix
			else
				transform.set( shift );

			final ViewId vidA = new ViewId( tpA, vsA );
			final ViewId vidB = new ViewId( tpB, vsB );
			final ValuePair< ViewId, ViewId > pair = new ValuePair< ViewId, ViewId >( vidA, vidB );
			
			final PairwiseStitchingResult< ViewId > pairwiseStitchingResult = new PairwiseStitchingResult<>(pair, transform, corr );
			stitchingResults.setPairwiseResultForPair( pair, pairwiseStitchingResult );
		}

		return stitchingResults;
	}
	
	protected Element pairwiseResultToXml( final PairwiseStitchingResult< ViewId > sr )
	{
		final Element elem = new Element( STITCHINGRESULT_PW_TAG );

		elem.setAttribute( STITCHING_VS_A_TAG, Integer.toString( sr.pair().getA().getViewSetupId()));
		elem.setAttribute( STITCHING_VS_B_TAG, Integer.toString( sr.pair().getB().getViewSetupId()));
		elem.setAttribute( STITCHING_TP_A_TAG, Integer.toString( sr.pair().getA().getTimePointId()));
		elem.setAttribute( STITCHING_TP_B_TAG, Integer.toString( sr.pair().getB().getTimePointId()));
		
		elem.addContent( XmlHelpers.doubleArrayElement( STICHING_SHIFT_TAG, sr.getTransform().getRowPackedCopy() ) );
		elem.addContent( XmlHelpers.doubleElement(  STICHING_CORRELATION_TAG, sr.r() ) );
		
		return elem;
	}

}
