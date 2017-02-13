package spim.process.interestpointregistration.pairwise;

import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class PairwiseResult< I extends InterestPoint >
{
	private List< PointMatchGeneric< I > > candidates, inliers;
	private double error = Double.NaN;
	private long time = 0;
	private String result = "", desc = "";
	private ViewId viewIdA, viewIdB;

	public void setResult( final long time, final String result )
	{
		this.time = time;
		this.result = result;
	}
	public void setDescriptions( final String desc ) { this.desc = desc; }
	public void setViewIdA( final ViewId viewIdA ) { this.viewIdA = viewIdA; }
	public void setViewIdB( final ViewId viewIdB ) { this.viewIdB = viewIdB; }
	public ViewId getViewIdA() { return viewIdA; }
	public ViewId getViewIdB() { return viewIdB; }
	public List< PointMatchGeneric< I > > getCandidates() { return candidates; }
	public List< PointMatchGeneric< I > > getInliers() { return inliers; }
	public String getDescription() { return desc; }
	public void setDescription( final String desc ) { this.desc = desc; }
	public double getError() { return error; }
	public void setCandidates( final List< PointMatchGeneric< I > > candidates ) { this.candidates = candidates; }
	public void setInliers( final List< PointMatchGeneric< I > > inliers, final double error )
	{
		this.inliers = inliers;
		this.error = error;
	}

	public String getFullDesc() { return "(" + new Date( time ) + "): " + desc + ": " + result; }
}
