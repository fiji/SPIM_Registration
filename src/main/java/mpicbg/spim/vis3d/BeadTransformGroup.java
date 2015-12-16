package mpicbg.spim.vis3d;

import javax.media.j3d.Transform3D;
import javax.media.j3d.TransformGroup;
import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;

import mpicbg.spim.registration.bead.Bead;

public class BeadTransformGroup extends TransformGroup
{
	final Vector3d beadPositionVector;
	final Point3d beadPositionPoint;
	final protected Bead bead;
	
	public BeadTransformGroup( final Transform3D transformation, final Bead bead )
	{
		super( transformation );
		
		this.bead = bead;
		this.beadPositionVector = new Vector3d( bead.getL() );
		this.beadPositionPoint = new Point3d( bead.getL() );
	}
	
	public Vector3d getBeadPositionVector() { return new Vector3d( beadPositionVector ); }
	public Point3d getBeadPositionPoint() { return new Point3d( beadPositionPoint ); }
	
	public void getBeadPositionPoint( final Point3d point ) { point.set( beadPositionPoint ); }
	public void getBeadPositionVector( final Vector3d vector ) { vector.set( beadPositionVector ); }
	
	public Bead getBead() { return bead; }
}
