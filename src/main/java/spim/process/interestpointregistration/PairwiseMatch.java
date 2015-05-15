package spim.process.interestpointregistration;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.spimdata.interestpoints.InterestPoint;

public class PairwiseMatch
{
	final MatchPointList listA, listB;
	final ViewId viewIdA, viewIdB;
	
	double error = -1;
	ArrayList< PointMatchGeneric< Detection > > candidates, inliers;
	
	public PairwiseMatch( final ViewId viewIdA, final ViewId viewIdB, final MatchPointList listA, final MatchPointList listB )
	{
		this.listA = listA;
		this.listB = listB;
		this.viewIdA = viewIdA;
		this.viewIdB = viewIdB;
	}
	
	public MatchPointList getMatchPointListA() { return listA; }
	public MatchPointList getMatchPointListB() { return listB; }
	public ChannelProcess getChannelProcessedA() { return listA.getChannelProcessed(); }
	public ChannelProcess getChannelProcessedB() { return listB.getChannelProcessed(); }
	public List< InterestPoint > getListA() { return listA.getInterestpointList(); }
	public List< InterestPoint > getListB() { return listB.getInterestpointList(); }
	public ViewId getViewIdA() { return viewIdA; }
	public ViewId getViewIdB() { return viewIdB; }
	public int getNumInliers() { return inliers.size(); }
	public int getNumCandidates() { return candidates.size(); }
	public double getAvgError() { return error; }
	public ArrayList< PointMatchGeneric< Detection > > getCandidates() { return candidates; }
	public ArrayList< PointMatchGeneric< Detection > > getInliers() { return inliers; }

	public ArrayList< ViewId > getBothViewIds()
	{
		final ArrayList< ViewId > l = new ArrayList< ViewId >();
		l.add( viewIdA );
		l.add( viewIdB );
		return l;
	}

	public void setCandidates( final ArrayList< PointMatchGeneric< Detection > > candidates ) { this.candidates = candidates; }
	public void setInliers( final ArrayList< PointMatchGeneric< Detection > > inliers, final double error )
	{
		this.inliers = inliers;
		this.error = error;
	}
}
