package spim.fiji.spimdata.interestpoints;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Defines a pair of corresponding interest points
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class CorrespondingInterestPoints
{
	/**
	 * The detection id of the interest point in this {@link InterestPointList}
	 */
	final int detectionId;
	
	/**
	 * The {@link ViewId} the corresponding interest point belongs to
	 */
	final ViewId correspondingViewId;
	
	/**
	 * The label of {@link InterestPointList} as stored in the {@link ViewInterestPointLists} HashMap
	 */
	final String correspondingLabel;
	
	/**
	 * The detection id of the corresponding interest point in the {@link InterestPointList}
	 */
	final int correspondingDetectionId;

	public CorrespondingInterestPoints( final int detectionId, final ViewId correspondingViewId, final String correspondingLabel, final int correspondingDetectionId )
	{
		this.detectionId = detectionId;
		this.correspondingViewId = correspondingViewId;
		this.correspondingLabel = correspondingLabel;
		this.correspondingDetectionId = correspondingDetectionId;
	}

	/**
	 * @return The detection id of the interest point in this {@link InterestPointList}
	 */
	final public int getDetectionId() { return detectionId; }

	/**
	 * @return The {@link ViewId} the corresponding interest point belongs to
	 */
	final public ViewId getCorrespondingViewId() { return correspondingViewId; }
	
	/**
	 * @return The label of {@link InterestPointList} as stored in the {@link ViewInterestPointLists} HashMap
	 */
	final public String getCorrespodingLabel() { return correspondingLabel; }
	
	/**
	 * @return The detection id of the corresponding interest point in the {@link InterestPointList}
	 */
	final public int getCorrespondingDetectionId() { return correspondingDetectionId; }
}
