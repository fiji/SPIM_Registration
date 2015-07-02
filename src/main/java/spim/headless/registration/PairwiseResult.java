package spim.headless.registration;

import java.util.ArrayList;

import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.process.interestpointregistration.Detection;

public class PairwiseResult
{
	ArrayList< PointMatchGeneric< Detection > > candidates, inliers;

	public ArrayList< PointMatchGeneric< Detection > > getCandidates() { return candidates; }
	public ArrayList< PointMatchGeneric< Detection > > getInliers() { return inliers; }
	public double error = Double.NaN;
	public String result = "";

	public void setCandidates( final ArrayList< PointMatchGeneric< Detection > > candidates ) { this.candidates = candidates; }
	public void setInliers( final ArrayList< PointMatchGeneric< Detection > > inliers, final double error )
	{
		this.inliers = inliers;
		this.error = error;
	}
}
