package spim.fiji.plugin.interestpointregistration;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;

import spim.fiji.spimdata.interestpoints.InterestPoint;

public class ListPair
{
	final List< InterestPoint > listA, listB;
	final ViewId viewIdA, viewIdB;
	
	int candidates = 0;
	int correspondences = 0;
	float error = 0;
	
	public ListPair( final ViewId viewIdA, final ViewId viewIdB, final List< InterestPoint > listA, final List< InterestPoint > listB )
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
	public int getNumCorrespondences() { return correspondences; }
	public int getNumCandidates() { return candidates; }
	public float getError() { return error; }
	
	public void setNumCandidates( final int n ) { this.candidates = n; }
	public void setNumCorrespondences( final int n ) { this.correspondences = n; }
	public void setError( final float e ) { this.error = e; }
}
