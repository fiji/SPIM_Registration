package spim.process.fusion.export;

import ij.gui.GenericDialog;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import spim.fiji.plugin.fusion.BoundingBox;

public interface ImgExport
{
	/**
	 * Exports the image
	 * 
	 * @param img - Note, in rare cases this can be null (i.e. do nothing)
	 * @param bb
	 * @param title
	 */
	public < T extends RealType< T > & NativeType< T > > void exportImage( final RandomAccessibleInterval< T > img, final BoundingBox bb, final String title );
	
	/**
	 * Query the necessary parameters for the fusion (new dialog has to be made)
	 * 
	 * @return
	 */
	public abstract boolean queryParameters();
	
	/**
	 * Query additional parameters within the bounding box dialog
	 */
	public abstract void queryAdditionalParameters( final GenericDialog gd );

	/**
	 * Parse the additional parameters added before within the bounding box dialog
	 * @param gd
	 * @return
	 */
	public abstract boolean parseAdditionalParameters( final GenericDialog gd );
	
	public abstract ImgExport newInstance();
	
	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();	

}
