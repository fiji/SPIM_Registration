package spim.fiji.spimdata.stitchingresults;

import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STICHING_CORRELATION_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STICHING_SHIFT_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHING_VS_A_TAG;
import static spim.fiji.spimdata.stitchingresults.XmlKeysStitchingResults.STITCHING_VS_B_TAG;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
			
			
			List< Integer > vsA = Arrays.asList( pairwiseResultsElement.getAttributeValue( STITCHING_VS_A_TAG ).split( "," )).stream().map( s -> Integer.parseInt( s ) ).collect( Collectors.toList() );
			List< Integer > vsB = Arrays.asList( pairwiseResultsElement.getAttributeValue( STITCHING_VS_B_TAG ).split( "," )).stream().map( s -> Integer.parseInt( s ) ).collect( Collectors.toList() );
			List< Integer > tpA = Arrays.asList( pairwiseResultsElement.getAttributeValue( STITCHING_TP_A_TAG ).split( "," )).stream().map( s -> Integer.parseInt( s ) ).collect( Collectors.toList() );
			List< Integer > tpB = Arrays.asList( pairwiseResultsElement.getAttributeValue( STITCHING_TP_B_TAG ).split( "," )).stream().map( s -> Integer.parseInt( s ) ).collect( Collectors.toList() );

			final double[] shift = XmlHelpers.getDoubleArray( pairwiseResultsElement, STICHING_SHIFT_TAG );
			final double corr = XmlHelpers.getDouble( pairwiseResultsElement, STICHING_CORRELATION_TAG );
			
			AffineTransform3D transform = new AffineTransform3D();
			// backwards-compatibility with just translation
			if (shift.length == 3)
				transform.setTranslation( shift );
			// tag contains row-packed copy of transformation matrix
			else
				transform.set( shift );

			Set<ViewId> vidsA = new HashSet<>();
			for (int i = 0; i < vsA.size(); i++)
				vidsA.add( new ViewId( tpA.get( i ), vsA.get( i ) ) );

			Set<ViewId> vidsB = new HashSet<>();
			for (int i = 0; i < vsB.size(); i++)
				vidsB.add( new ViewId( tpB.get( i ), vsB.get( i ) ) );
			
			final ValuePair< Set<ViewId>, Set<ViewId> > pair = new ValuePair<>( vidsA, vidsB );
			
			final PairwiseStitchingResult< ViewId > pairwiseStitchingResult = new PairwiseStitchingResult<>(pair, transform, corr );
			stitchingResults.setPairwiseResultForPair( pair, pairwiseStitchingResult );
		}

		return stitchingResults;
	}
	
	protected Element pairwiseResultToXml( final PairwiseStitchingResult< ViewId > sr )
	{
		final Element elem = new Element( STITCHINGRESULT_PW_TAG );

		elem.setAttribute( STITCHING_VS_A_TAG, String.join( ",", sr.pair().getA().stream().map( vi ->  Integer.toString( vi.getViewSetupId() ) ).collect( Collectors.toList() ) ) );
		elem.setAttribute( STITCHING_VS_B_TAG, String.join( ",", sr.pair().getB().stream().map( vi ->  Integer.toString( vi.getViewSetupId() ) ).collect( Collectors.toList() ) ) );
		elem.setAttribute( STITCHING_TP_A_TAG, String.join( ",", sr.pair().getA().stream().map( vi ->  Integer.toString( vi.getTimePointId() ) ).collect( Collectors.toList() ) ) );
		elem.setAttribute( STITCHING_TP_B_TAG, String.join( ",", sr.pair().getB().stream().map( vi ->  Integer.toString( vi.getTimePointId() ) ).collect( Collectors.toList() ) ) );
		
		elem.addContent( XmlHelpers.doubleArrayElement( STICHING_SHIFT_TAG, sr.getTransform().getRowPackedCopy() ) );
		elem.addContent( XmlHelpers.doubleElement(  STICHING_CORRELATION_TAG, sr.r() ) );
		
		return elem;
	}

}
