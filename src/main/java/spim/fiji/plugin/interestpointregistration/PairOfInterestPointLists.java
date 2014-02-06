package spim.fiji.plugin.interestpointregistration;

import java.util.ArrayList;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;

import spim.fiji.spimdata.interestpoints.InterestPoint;

public class PairOfInterestPointLists
{
	final List< InterestPoint > listA, listB;
	final ViewId viewIdA, viewIdB;
	
	float error = 0;
	ArrayList<PointMatchGeneric<Detection>> candidates, inliers;
	
	public PairOfInterestPointLists( final ViewId viewIdA, final ViewId viewIdB, final List< InterestPoint > listA, final List< InterestPoint > listB )
	{
		this.listA = listA;
		this.listB = listB;
		this.viewIdA = viewIdA;
		this.viewIdB = viewIdB;
	}
	
	public List< InterestPoint > getListA() { return listA; }
	public List< InterestPoint > getListB() { return listB; }
	public ViewId getViewIdA() { return viewIdA; }
	public ViewId getViewIdB() { return viewIdB; }
	public int getNumInliers() { return inliers.size(); }
	public int getNumCandidates() { return candidates.size(); }
	public float getError() { return error; }
	public ArrayList< PointMatchGeneric< Detection > > getCandidates() { return candidates; }
	public ArrayList< PointMatchGeneric< Detection > > getInliers() { return inliers; }
	
	public void setError( final float e ) { this.error = e; }
	public void setCandidates( final ArrayList<PointMatchGeneric<Detection>> candidates ) { this.candidates = candidates; }
	public void setInliers(ArrayList<PointMatchGeneric<Detection>> inliers) { this.inliers = inliers; }
}
