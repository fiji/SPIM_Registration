package mpicbg.spim.vis3d;

import java.util.ArrayList;
import java.util.Random;

import mpicbg.spim.registration.ViewDataBeads;
import spim.vecmath.Color3f;
import spim.vecmath.Point3f;
import spim.vecmath.Transform3D;
import spim.vecmath.Vector3f;

public class VisualizationFunctions
{
	public static Color3f getRandomPastellColor( final float lowerBorder ) { return getRandomPastellColor( System.currentTimeMillis(), lowerBorder ); }
	public static Color3f getRandomColor() { return getRandomColor( System.nanoTime() ); }

	public static Color3f getRandomColor( final long seed )
	{
		try
		{
			Thread.sleep(10);
		}
		catch (InterruptedException e)
		{}
		final Random rnd = new Random( seed );
		return new Color3f( rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat() );
	}

	public static Color3f getRandomPastellColor( final long seed, final float lowerBorder )
	{
		try
		{
			Thread.sleep(10);
		}
		catch (InterruptedException e)
		{}
		final Random rnd = new Random( seed );
		return new Color3f( rnd.nextFloat()*(1-lowerBorder) + lowerBorder, rnd.nextFloat()*(1-lowerBorder) + lowerBorder, rnd.nextFloat()*(1-lowerBorder) + lowerBorder );
	}

	public static ArrayList<Point3f> makeArrow( final Point3f from, final Point3f to, final float arrowHeadAngle, final float arrowHeadLength)
	{
		// get the vector of the line
		final Vector3f v = new Vector3f( to );
		v.sub( from );
		v.normalize();

		// the orthogonal vectors to compute
		Vector3f a,b;

		final Vector3f x = new Vector3f( 1, 0, 0 );
		final float length = v.dot(x);

		if (length > 0.9999 && length < 1.0001)
		{
			a = new Vector3f(0, 1, 0);
		}
		else
		{
			final Vector3f tmp = new Vector3f( v );

			tmp.scale( x.dot(v) );

			a = new Vector3f( x );
			a.sub(tmp);
		}

		b = new Vector3f();
		b.cross(a, v);

		// create the arrow
		final ArrayList<Point3f> arrowList = new ArrayList<Point3f>();
		arrowList.add(from);
		arrowList.add(to);

		computeArrowLines(arrowList, to, v, a, b, arrowHeadAngle, arrowHeadLength);
		return arrowList;

	}

	protected static void computeArrowLines(final ArrayList<Point3f> list, final Point3f to, final Vector3f v, final Vector3f a, final Vector3f b, 
											final float arrowHeadAngle, final float arrowHeadLength)
	{
		final Vector3f a1 = new Vector3f(a);
		final Vector3f b1 = new Vector3f(b);
		final Vector3f v1 = new Vector3f(v);

		a1.scale((float)Math.sin(arrowHeadAngle));
		b1.scale((float)Math.sin(arrowHeadAngle));
		v1.scale((float)Math.cos(arrowHeadAngle));

		a1.scale( arrowHeadLength );
		b1.scale( arrowHeadLength );
		v1.scale( arrowHeadLength );

		Point3f arrow = new Point3f(to);
		arrow.sub(v1);
		arrow.sub(a1);
		list.add(to);
		list.add(arrow);

		arrow = new Point3f(to);
		arrow.sub(v1);
		arrow.add(a1);
		list.add(to);
		list.add(arrow);

		arrow = new Point3f(to);
		arrow.sub(v1);
		arrow.add(b1);
		list.add(to);
		list.add(arrow);

		arrow = new Point3f(to);
		arrow.sub(v1);
		arrow.sub(b1);
		list.add(to);
		list.add(arrow);
	}
	
	final public static boolean storeBeadPosition = true;

	public static ArrayList<Point3f> getTransformedBoundingBox( final ViewDataBeads view )
	{
		final ArrayList<Point3f> boundingBox = new ArrayList<Point3f>();
		
		final Transform3D transformation = view.getTransform3D();
		final int[] imageSize = view.getImageSize();
		
		Point3f from, to;
		
		from = new Point3f(0,0,0);
		to = new Point3f(view.getImageSize()[0], 0, 0);
		view.getTransform3D().transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );
		
		from = new Point3f(imageSize[0], 0, 0);
		to = new Point3f(imageSize[0], imageSize[1], 0);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		from = new Point3f(imageSize[0], imageSize[1], 0);
		to = new Point3f(0, imageSize[1], 0);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );
		
		from = new Point3f(0, imageSize[1], 0);
		to = new Point3f(0, 0, 0);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );


		from = new Point3f(0, 0, imageSize[2]);
		to = new Point3f(imageSize[0], 0, imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		from = new Point3f(imageSize[0], 0,  imageSize[2]);
		to = new Point3f(imageSize[0], imageSize[1],  imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		from = new Point3f(imageSize[0], imageSize[1],  imageSize[2]);
		to = new Point3f(0, imageSize[1], imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );
		
		from = new Point3f(0, imageSize[1],  imageSize[2]);
		to = new Point3f(0, 0, imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		
		from = new Point3f(0, 0, 0);
		to = new Point3f(0, 0, imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		from = new Point3f(imageSize[0], 0, 0);
		to = new Point3f(imageSize[0], 0, imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		from = new Point3f(imageSize[0], imageSize[1], 0);
		to = new Point3f(imageSize[0], imageSize[1], imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );

		from = new Point3f(0, imageSize[1], 0);
		to = new Point3f(0, imageSize[1], imageSize[2]);
		transformation.transform( from );
		transformation.transform( to );		
		boundingBox.add( from );
		boundingBox.add( to );
		
		return boundingBox;
	}

	public static ArrayList<Point3f> getBoundingBox( final ViewDataBeads view )
	{
		final int[] to = view.getImageSize();
		final int[] fr = view.getImageSizeOffset();

		return getBoundingBox( fr, to );
	}
	
	public static ArrayList<Point3f> getBoundingBox( final int[] fr, final int[] to )
	{
		final ArrayList<Point3f> boundingBox = new ArrayList<Point3f>();
		
		for ( int d = 0; d < to.length; ++d )
			to[ d ] += fr[ d ];
		
		boundingBox.add( new Point3f(fr[0], fr[1], fr[2]) );
		boundingBox.add( new Point3f(to[0], fr[1], fr[2]) );
		
		boundingBox.add( new Point3f(to[0], fr[1], fr[2]) );
		boundingBox.add( new Point3f(to[0], to[1], fr[2]) );

		boundingBox.add( new Point3f(to[0], to[1], fr[2]) );
		boundingBox.add( new Point3f(fr[0], to[1], fr[2]) );
		
		boundingBox.add( new Point3f(fr[0], to[1], fr[2]) );
		boundingBox.add( new Point3f(fr[0], fr[1], fr[2]) );

		boundingBox.add( new Point3f(fr[0], fr[1], to[2]) );
		boundingBox.add( new Point3f(to[0], fr[1], to[2]) );

		boundingBox.add( new Point3f(to[0], fr[1], to[2]) );
		boundingBox.add( new Point3f(to[0], to[1], to[2]) );

		boundingBox.add( new Point3f(to[0], to[1], to[2]) );
		boundingBox.add( new Point3f(fr[0], to[1], to[2]) );

		boundingBox.add( new Point3f(fr[0], to[1], to[2]) );
		boundingBox.add( new Point3f(fr[0], fr[1], to[2]) );

		boundingBox.add( new Point3f(fr[0], fr[1], fr[2]) );
		boundingBox.add( new Point3f(fr[0], fr[1], to[2]) );

		boundingBox.add( new Point3f(to[0], fr[1], fr[2]) );
		boundingBox.add( new Point3f(to[0], fr[1], to[2]) );

		boundingBox.add( new Point3f(to[0], to[1], fr[2]) );
		boundingBox.add( new Point3f(to[0], to[1], to[2]) );

		boundingBox.add( new Point3f(fr[0], to[1], fr[2]) );
		boundingBox.add( new Point3f(fr[0], to[1], to[2]) );
		
		return boundingBox;
	}

	public static ArrayList<Point3f> getBoundingBox( final float minX, final float maxX, final float minY, final float maxY, final float minZ, final float maxZ )
	{
		final ArrayList<Point3f> boundingBox = new ArrayList<Point3f>();
		
		boundingBox.add( new Point3f(minX, minY , minZ) );
		boundingBox.add( new Point3f(maxX, minY , minZ) );
		
		boundingBox.add( new Point3f(maxX, minY, minZ) );
		boundingBox.add( new Point3f(maxX, maxY, minZ) );

		boundingBox.add( new Point3f(maxX, maxY, minZ) );
		boundingBox.add( new Point3f(minX, maxY, minZ) );
		
		boundingBox.add( new Point3f(minX, maxY, minZ) );
		boundingBox.add( new Point3f(minX, minY, minZ) );

		boundingBox.add( new Point3f(minX, minY, maxZ) );
		boundingBox.add( new Point3f(maxX, minY, maxZ) );

		boundingBox.add( new Point3f(maxX, minY,  maxZ) );
		boundingBox.add( new Point3f(maxX, maxY,  maxZ) );

		boundingBox.add( new Point3f(maxX, maxY,  maxZ) );
		boundingBox.add( new Point3f(minX, maxY, maxZ) );

		boundingBox.add( new Point3f(minX, maxY,  maxZ) );
		boundingBox.add( new Point3f(minX, minY, maxZ) );

		boundingBox.add( new Point3f(minX, minY, minZ) );
		boundingBox.add( new Point3f(minX, minY, maxZ) );

		boundingBox.add( new Point3f(maxX, minY, minZ) );
		boundingBox.add( new Point3f(maxX, minY, maxZ) );

		boundingBox.add( new Point3f(maxX, maxY, minZ) );
		boundingBox.add( new Point3f(maxX, maxY, maxZ) );

		boundingBox.add( new Point3f(minX, maxY, minZ) );
		boundingBox.add( new Point3f(minX, maxY, maxZ) );
		
		return boundingBox;
	}
}
